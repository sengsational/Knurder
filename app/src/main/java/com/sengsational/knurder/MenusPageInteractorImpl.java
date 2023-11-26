package com.sengsational.knurder;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.sengsational.ocrreader.OcrScanHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;

import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;

/**
 * Created by Dale Seng on 5/12/2021.
 */

public class MenusPageInteractorImpl  extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = MenusPageInteractorImpl.class.getSimpleName();

    private CloseableHttpClient nHttpclient = null;
    private BasicCookieStore nCookieStore = null;
    private WebResultListener nListener;
    private String nStoreNumber = null;
    private String nErrorMessage = null;
    private String nMenuUrl;
    private Context nApplicationContext;

    public void getMenuDataFromWeb(final String url, final WebResultListener webResultListener, Context applicationContext) {
        nListener = webResultListener;
        nMenuUrl = url.replace("https://","http://");
        nApplicationContext = applicationContext;
        this.execute((Void) null);
    }

    @Override
    protected void onPreExecute() {
        Log.v("sengsational", "onPreExecute()..."); //Run order #01
        if (TextUtils.isEmpty(nMenuUrl)) {
            nListener.onError("menu url error");
            nErrorMessage = "We did not get a url to pull from.";
        }

        // set-up a single nHttpclient
        if (nHttpclient != null) {
            Log.e("sengsational", "Attempt to set-up more than one HttpClient!!");
        } else {
            try {
                nCookieStore = new BasicCookieStore();
                HttpClientBuilder clientBuilder = HttpClientBuilder.create();
                nHttpclient = clientBuilder.setRedirectStrategy(new LaxRedirectStrategy()).setDefaultCookieStore(nCookieStore).build();
                nHttpclient.log.enableDebug(true);
                Log.v("sengsational", "nHttpclient object created."); //Run order #02
            } catch (Throwable t) {//
                Log.v("sengsational", "nHttpclient object NOT created. " + t.getMessage());
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                Log.v("sengsational", sw.toString());
                nListener.onError("http client error");
                nErrorMessage = "Problem with the http connection.";
            }
        }
    }

    @Override
    protected void onPostExecute(final Boolean success) {
        // new nHttpclient object each time
        try {
            nHttpclient.close();
            nHttpclient = null;
        } catch (Exception e) {}

        if (success) {
            Log.v("sengsational", "onPostExecute success: " + success);
            nListener.onFinished();
        } else {
            Log.v("sengsational", "onPostExecute fail");
            nListener.onError(nErrorMessage);
        }
    }

    @Override
    protected Boolean doInBackground(Void... params) {

        if (nErrorMessage != null) return false;

        if(!getSiteAccess("saucerknurd.com")){
            nListener.onError("could not get to the web site");
            nErrorMessage = "Could not reach the saucerknurd.com web site.";
            return false;
        }

        try {
            do { // This is here to prevent tasted and store lists from updating at the same time.  One will queue behind the other
                Log.v(TAG, "doInBackground is accessing web update lock.");
            } while (!KnurderApplication.getWebUpdateLock(TAG)); // if lock unavailable, this will delay 1/2 second up to 10 seconds, then release.

            // Get the touchless menu html page from the URL passed-in
            String touchlessWebPage = pullTouchlessWebPage(nMenuUrl);
            if(touchlessWebPage == null){
                nListener.onError("did not get touchless menu web page");
                nErrorMessage = "Did not get the touchless menu page from the UFO site.";
                return false;
            }

            // Pull the Untappd data URL from the touchelssWepPage
            String untappdDataUrlString = getUntappdDataUrl(touchlessWebPage);
            if (null == untappdDataUrlString) {
                nListener.onError("did not get untappdDataUrlString from touchlessWebPage");
                nErrorMessage = "Could not find the location for the menu information from the touchless menu page.";
                return false;
            }
            // START NOTE: This code is duplicated in the refresh beer list "StoreListInteractorImpl.doInBackground()
            // START NOTE: This code is duplicated in the refresh beer list "MenusPageInteractorImpl.doInBackground()
            // Pull the page containing the list of beers
            String untappdDataPage = pullUntappedDataPage(untappdDataUrlString, nHttpclient, nCookieStore);
            if (null == untappdDataPage) {
                nListener.onError("not able to pull the untappdDataPage from " + untappdDataUrlString);
                nErrorMessage = "Could not get the menu information from the location provided.";
                return false;
            }
            // Parse the beers out of the data
            ArrayList<UntappdItem> untappdItems = getUntappdItemsFromData(untappdDataPage);
            if (untappdItems.size() == 0) {
                nListener.onError("zero items pulled from untappdDataPage of size " + untappdDataPage.length());
                nErrorMessage = "Did not understand the menu information found.";
                return false;
            }
            // Match the untappd list with the saucer tap list
            OcrScanHelper.getInstance().matchUntappdItems(untappdItems, nApplicationContext);
            // Save the results
            int[] results = OcrScanHelper.getInstance().getResults(nApplicationContext);
            // END NOTE: This code is duplicated in the refresh beer list "StoreListInteractorImpl.doInBackground()
            // END NOTE: This code is duplicated in the refresh beer list "MenusPageInteractorImpl.doInBackground()
            Log.v(TAG, "About to finish data parse from untappd.  Setting extras.");
            Intent data = new Intent();
            data.putExtra("totalItemCount", results[0]);
            data.putExtra("newItemCount", results[1]);
            data.putExtra("totalTapCount", results[2]);
            data.putExtra("untappdUrlExtra", untappdDataUrlString);
            nListener.onOcrScanSuccess(data);
            nListener.onFinished();

            // BELOW IS FOR THE FORMER METHOD, WHICH PERFORMED OCR ON AN IMAGE FILE
            //                                                 --------------------
            /*
            // Parse the page to find the link to the tap beer jpg [menus/fschar-beer-1.jpg?v=21]
            String imageLoc = getTapImageLocFromHtml(touchlessWebPage);
            if (imageLoc == null) {
                nListener.onError("did not find the location to the image with tap menu");
                nErrorMessage = "Found the touchless menu page, but didn't find the link to the menu image.";
                return false;
            }

            // Download the jpg [https://saucerknurd.com/touchless-menu/menus/fschar-beer-1.jpg?v=21]
            String imageUrl = "https://saucerknurd.com/touchless-menu/" + imageLoc;
            Bitmap downloadedBitmap = downloadJpgFromWeb(imageUrl);
            if (downloadedBitmap == null) {
                nListener.onError("not able to download image from web");
                nErrorMessage = "Unable to get image from " + imageUrl;
                return false;
            }
            Log.v("sengsational", "Bitmap is " + downloadedBitmap.getByteCount() + " bytes.");

            // Make bitmap single column
            float leftMargin = 15F/750F;
            float firstColumn = 236F/750F;
            float secondColumn = 236F/750F;
            float thirdColumn = 236F/750F;
            Bitmap singleColumnBitmap = singleColomnize(downloadedBitmap, leftMargin, firstColumn, secondColumn, thirdColumn);
            Bitmap emptyCornerBitmap = Bitmap.createBitmap(10, 10, singleColumnBitmap.getConfig());
            Bitmap populatedCornerBitmap = Bitmap.createBitmap(singleColumnBitmap, 0, 0, 10, 10);
            if (populatedCornerBitmap.sameAs(emptyCornerBitmap)) {
                nListener.onError("not able to make single column bitmap");
                nErrorMessage = "Unable to manipulate the image from " + imageUrl;
                return false;
            }

            // Do OCR on single column bitmap
            int pixelRowsPerChunk = 200;
            int overlapRows = 24; //12 had 36 found, 18 had 38 found, 24 had 39 found, 30 had 39 found.
            /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
            String textFromBitmap = getTextFromBitmap(singleColumnBitmap, pixelRowsPerChunk, overlapRows);  //Creates OcrScanHelper and populates instance variables
            /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

            // Log the output, if debugging
            boolean sendResultToLog = false;
            if (sendResultToLog) {
                int position = 0; int textChunkSize = 2000; // Log entries can't be real long, so split them up.
                Log.v(TAG, "szof"+textFromBitmap.length());
                while (position < textFromBitmap.length()) {
                    int adjustedChunk = Math.min(textChunkSize, textFromBitmap.length() - position);
                    Log.v(TAG, "==================\n\n" + textFromBitmap.substring(position, adjustedChunk + position) + "\n\n===============================");
                    position+=textChunkSize;
                }
            }

            // Get the results from the OcrScanHelper (which updates the database) and pass the summary values to the calling process.
            int[] results = OcrScanHelper.getResults(nApplicationContext);
            Log.v(TAG, "About to finish OCR from JPG.  Setting extras.");
            Intent data = new Intent();
            data.putExtra("totalItemCount", results[0]);
            data.putExtra("newItemCount", results[1]);
            data.putExtra("totalTapCount", results[2]);
            nListener.onOcrScanSuccess(data);
            nListener.onFinished();

             */



            //nListener.saveValidStore(nStoreNumber);
            //nListener.onStoreListSuccess(nResetPresentation);
        } catch (Exception e) {
            Log.e(TAG, LoadDataHelper.getInstance().getStackTraceString(e));
            nErrorMessage = "Exception " + e.getMessage();
            return false;
        } finally {
            KnurderApplication.releaseWebUpdateLock(TAG);
        }
        Log.v(TAG, "Returning " + true + " from doInBackground");
        return true;
    }

    public static ArrayList<UntappdItem> getUntappdItemsFromData(String untappdData) {
        ArrayList<UntappdItem> untappdItems = new ArrayList<UntappdItem>();
        int htmlLoc = untappdData.indexOf("container.innerHTML");
        if (htmlLoc < 0) {
            Log.v(TAG, "The page did not contain [container.innerHTML]");
            return untappdItems; //page didn't match expectations
        }
        //Log.v(TAG, " previous  [" + untappdData.substring(3000,6000));
        untappdData = untappdData.replaceAll("\\\\\\\"", "\"");
        untappdData = untappdData.replaceAll("<\\\\", "<");
        untappdData = untappdData.replaceAll("\\\\n", "");
        //Log.v(TAG, " unescaped [" + untappdData.substring(3000,6000));

        int firstHtmlLoc = untappdData.indexOf("<", htmlLoc);
        String lastThingToInclude = "menu-title\">Bottles"; // Cut-off the bottles section.... right now, only including taps.
        int lastThingLoc = untappdData.indexOf(lastThingToInclude);
        if (lastThingLoc < 0) lastThingLoc = untappdData.length();
        Log.v(TAG, "first thing " + firstHtmlLoc + " last thing " + lastThingLoc);
        StringBuffer buf = new StringBuffer();
        buf.append("<html><body>");
        buf.append(untappdData.substring(firstHtmlLoc, lastThingLoc));
        buf.append("</body></html>");

        Document doc = Jsoup.parse(Parser.unescapeEntities(buf.toString(),true));
        String changableName = "beer";
        Elements beerList = doc.getElementsByClass(changableName);
        if (beerList.size() == 0) {
            changableName = "item";
            beerList = doc.getElementsByClass(changableName);
        }
        Log.v(TAG, "There were "  + beerList.size() + " beers pulled from the Untappd data.");
        int maxItems = 999;
        int untappdeAddedCount = 0;
        for (Element beer: beerList) {
            String beerName = "";
            String breweryName = "";
            String ounces = "";
            String price = "";
            String abv = "";
            String beerNumber = "";
            String breweryNumber = "";
            int position = 0;
            try {
                position = 1;
                //System.out.println("DEBUG beer element [" + beer.html() + "]");
                Element beerNameElement = beer.getElementsByClass(changableName + "-name").first();/*1*/ position=2;
                if (beerNameElement != null) {
                    Element anchorElement = beerNameElement.getElementsByTag("a").first();/*3*/ position=4;
                    if (anchorElement != null) {
                        beerName = anchorElement.text();/*4*/ position=5;
                        beerNumber = getLastNumberFromAnchor(beerNameElement); position=6;
                    }
                }

                try {
                    Element metaElement = beer.getElementsByClass(changableName + "-meta").first();/*1*/ position=107;
                    Element abvElement = metaElement.getElementsByClass(changableName + "-abv").first(); position=7;
                    abv = abvElement.text(); position =8;
                } catch (Throwable t) {
                    // non-essential
                }

                Element breweryNameElement = beer.getElementsByClass("brewery").first(); position=9;
                if (breweryNameElement != null) {
                    breweryName = breweryNameElement.text(); position=10;
                    breweryNumber = getLastNumberFromAnchor(breweryNameElement);position=11;
                }

                Element typeElement = beer.getElementsByClass("type").first(); position=12;
                if (typeElement != null) ounces = typeElement.text(); position=13;

                Element priceElement = beer.getElementsByClass("price").first(); position=14;
                if (priceElement != null) price = priceElement.text().replaceAll("\\\\", ""); position=15;

                UntappdItem untappdItem = new UntappdItem(beerName, breweryName, ounces, price, abv, beerNumber, breweryNumber);
                untappdItems.add(untappdItem);
                untappdeAddedCount++;
                //System.out.println("DEBUG "  +  breweryNumber + " " + beerNumber + " " + beerName + " " + breweryName );
            } catch (Throwable t) {
                Log.v(TAG, "ERROR: unable to parse: " + position + "   " + t.getMessage() + "\nbeerName:" + beerName + " breweryName:" + breweryName+" ounces:" + ounces + " price:" + price + " abv:" + abv + " beerNumber:" +beerNumber + " breweryNumber:" + breweryNumber + "\n" + beer.html());
                continue;
            }

            //Log.v(TAG, "beerName:" + beerName + " breweryName:" + breweryName+" ounces:" + ounces + " price:" + price + " abv:" + abv + " beerNumber:" +beerNumber + " breweryNumber:" + breweryNumber + "\n" + beer.html());
            if (maxItems-- < 0) break;
        }
        Log.v(TAG, "untappdAddedCount = " + untappdeAddedCount);
        return untappdItems;
    }

    private static String getLastNumberFromAnchor(Element anchorParentElement) {
        Element anchorElement = anchorParentElement.getElementsByTag("a").first();
        // <a href="https://untappd.com/b/sierra-nevada-brewing-co-pale-ale/6284" class="item-title-color" target="_blank">
        // <a href="https://untappd.com/brewery/1142" class="item-title-color" target="_blank">Sierra Nevada Brewing Co.</a>
        String urlString = anchorElement.attr("href");
        if (urlString == null) return "";
        String[] bits = urlString.split("/");
        try {
            Integer.parseInt(bits[bits.length-1]);
            return bits[bits.length-1];
        } catch (Throwable t) {
            // never mind
        }
        return "";
    }

    /*
    private String getTextFromBitmap(Bitmap singleColumnBitmap, int pixelRowsPerChunk, int overlapRows) {
        TextRecognizer textRecognizer = new TextRecognizer.Builder(nApplicationContext).build();

        if (!textRecognizer.isOperational()) {
            Log.v(TAG, "The textRecognizer was not operational.");
            return null;
        } else {
            Frame.Builder frameBuilder = new Frame.Builder();
            SparseArray<TextBlock> chunkItems;
            Bitmap chunkBitmap = Bitmap.createBitmap(singleColumnBitmap.getWidth(), pixelRowsPerChunk + overlapRows, singleColumnBitmap.getConfig()); //
            Canvas canvas = new Canvas(chunkBitmap);
            Frame frame;
            //StringBuilder builder = new StringBuilder();
            int height = singleColumnBitmap.getHeight();
            int width = singleColumnBitmap.getWidth();
            // Loop through chunks because doing the whole thing results in poor detection results.
            int maxLoops = 99;
            StringBuilder builder = new StringBuilder();
            for(int position = 0; position < height; position = position + pixelRowsPerChunk){
                // make adjusted chunk for very the last chunk, so it doesn't copy beyond the source height
                int chunkAdjusted = Math.min(pixelRowsPerChunk + overlapRows, height - position);
                Bitmap chunkCopied = Bitmap.createBitmap(singleColumnBitmap, 0, position, width, chunkAdjusted);
                Log.v(TAG, "Processing position " + position + ", with " + chunkAdjusted + " rows.");
                // paste chunk from the singleColumnBitmap into the chunkBitmap
                canvas.drawBitmap(chunkCopied, 0, 0, null);
                // build a frame with the chunk
                frame = frameBuilder.setBitmap(chunkBitmap).build();
                // detect text from the chunk
                chunkItems = textRecognizer.detect(frame);
                Log.v(TAG, "chunkItems.size() : " + chunkItems.size());
                for (int i = 0; i < chunkItems.size(); i++) {
                    TextBlock item = chunkItems.valueAt(i);
                    if (item != null && item.getValue() != null) {
                        OcrScanHelper.getInstance(nApplicationContext).scanTapMenu(item); /// <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
                    }
                    int completePercent = (position * 100) / height;
                    nListener.onOcrScanProgress(completePercent);
                    //String foundValue = chunkItems.valueAt(i).getValue();
                    //Log.v(TAG, "foundValue [" + foundValue + "]");
                    //builder.append(foundValue);
                    //builder.append("\n");
                }
                if (--maxLoops == 0) break;
            }
            return "(builder is turned off)"; //builder.toString();
            //return builder.toString();
        }
    }

     */

    /*
    private Bitmap singleColomnize(Bitmap downloadedBitmap, float leftMargin, float firstColumn, float secondColumn, float thirdColumn) {
        //test--int testChunk = 400;
        int originalWidth = downloadedBitmap.getWidth();
        int originalHeight = downloadedBitmap.getHeight();
        Log.v(TAG, "Original Size [" + originalWidth + ", " + originalHeight);
        int maxColumnPixels = (int)(Collections.max(Arrays.asList(firstColumn, secondColumn, thirdColumn)) * downloadedBitmap.getWidth());
        Log.v(TAG, "Column width [" + maxColumnPixels + "]");
        int leftMarginPixels = (int)(leftMargin * originalWidth); // 15
        int firstColumnPixels = (int)(firstColumn * originalWidth); // 236
        int secondColumnPixels = (int)(secondColumn * originalWidth);  // 236
        int thirdColumnPixels = (int)(thirdColumn * originalWidth);  // 236
        Bitmap singleColumnBitmap = Bitmap.createBitmap(maxColumnPixels, originalHeight * 3, downloadedBitmap.getConfig()); //
        ///test--Bitmap singleColumnBitmap = Bitmap.createBitmap(maxColumnPixels, testChunk, downloadedBitmap.getConfig()); //
        Log.v(TAG, "New Image Size [" + singleColumnBitmap.getWidth() + ", " + singleColumnBitmap.getHeight() + "]");
        Canvas canvas = new Canvas(singleColumnBitmap);
        int leftPixel = leftMarginPixels; //15
        int rightPixel = leftPixel + firstColumnPixels; //15 + 236 = 251
        int topPixel = 0; // 0
        int bottomPixel = originalHeight; //1236
        //Log.v(TAG, "First Paste -  Horizontal Range [" + leftPixel + ", " + rightPixel + "]   Vertical Range [" + topPixel + ", " + bottomPixel + "] 15, 251, 0, 1236");
        Log.v(TAG, "canvas.drawBitmap(Bitmap.createBitmap(downloadedBitmap, "+ leftPixel + ", 0, " + maxColumnPixels +", " + originalHeight+ "), 0, " + topPixel + ", null);");
        canvas.drawBitmap(Bitmap.createBitmap(downloadedBitmap, leftPixel, 0, maxColumnPixels, originalHeight), 0, topPixel, null); // 487, 0, 236, 1236
        //test--canvas.drawBitmap(Bitmap.createBitmap(downloadedBitmap, leftPixel, 800, maxColumnPixels, testChunk), 0, topPixel, null); // 487, 0, 236, 1236
        leftPixel = rightPixel; rightPixel = leftPixel + secondColumnPixels; topPixel = bottomPixel; bottomPixel = bottomPixel + originalHeight;
        Log.v(TAG, "canvas.drawBitmap(Bitmap.createBitmap(downloadedBitmap, "+ leftPixel + ", 0, " + maxColumnPixels +", " + originalHeight+ "), 0, " + topPixel + ", null);");
        canvas.drawBitmap(Bitmap.createBitmap(downloadedBitmap, leftPixel, 0, maxColumnPixels, originalHeight), 0, topPixel, null); // 487, 0, 236, 1236
        leftPixel = rightPixel; rightPixel = leftPixel + thirdColumnPixels; topPixel = bottomPixel; bottomPixel = bottomPixel + originalHeight;
        Log.v(TAG, "canvas.drawBitmap(Bitmap.createBitmap(downloadedBitmap, "+ leftPixel + ", 0, " + maxColumnPixels +", " + originalHeight+ "), 0, " + topPixel + ", null);");
        canvas.drawBitmap(Bitmap.createBitmap(downloadedBitmap, leftPixel, 0, maxColumnPixels, originalHeight), 0, topPixel, null); // 487, 0, 236, 1236
        return singleColumnBitmap;
    }

     */

    /*
    private Bitmap downloadJpgFromWeb(String imageUrl) {
        Bitmap touchlessBeerBitmap = null;
        try {
            touchlessBeerBitmap = LoadDataHelper.getImageContent(imageUrl, null, nHttpclient, nCookieStore);
        } catch (Exception e) {
            Log.e("sengsational", "Could not get touchlessBeerBitmap. " + e.getMessage());
        }
        return touchlessBeerBitmap;
    }

     */
    private String getUntappdDataUrl(String touchlessWebPage) {
        String dataUrlString = null;
        int linkData = touchlessWebPage.indexOf("PreloadEmbedMenu(\"menu-container\"");
        int linkDataEnd = touchlessWebPage.indexOf("}", linkData);
        if (linkDataEnd > 0 && linkData > 0 && linkDataEnd > linkData) {
            String linkDataFound = touchlessWebPage.substring(linkData, linkDataEnd);
            //Log.v("sengsational", "[" + linkDataFound + "]"); //PreloadEmbedMenu("menu-container",35529,137645)}
            String[] splitLinkArray = linkDataFound.replace(")", ",").replaceAll(" ","").split(",");
            if (splitLinkArray.length > 2) {
                dataUrlString = "https://business.untappd.com/locations/" + splitLinkArray[1] + "/themes/" + splitLinkArray[2] + "/js";
                Log.v(TAG, "Untapped data URL: " + dataUrlString);
                // HOUSTON :   https://business.untappd.com/locations/34604/themes/133945/js
                // CHARLOTTE:  https://business.untappd.com/locations/35529/themes/137645/js
                // RALEIGH:    https://business.untappd.com/locations/35528/themes/137641/js
            }
        } else {
            Log.v("sengsational", "invalid in page:" + touchlessWebPage );
        }
        return dataUrlString;
    }

    /*
    // Look in page for [src="menus/], and take the one that says "beer-1"
    // Not sure this is good for all locations, but it's the best I have now
    private String getTapImageLocFromHtml(String touchlessWebPage) {
        String srcContent = null;
        if (touchlessWebPage.contains("src=\"menus/")) {
            int start = 0;
            int srcLoc = touchlessWebPage.indexOf("src=\"menus/", start);
            do {
                int srcLocEnd = touchlessWebPage.indexOf("\">", srcLoc);
                if (srcLocEnd > 0) {
                    String tmpSrc = touchlessWebPage.substring(srcLoc, srcLocEnd);
                    Log.v("sengsational", "[" + tmpSrc + "]");
                    int quoteLocStart = tmpSrc.indexOf("\"");
                    int quoteLocEnd = tmpSrc.indexOf("\"", quoteLocStart + 1);
                    if (quoteLocStart > 0 && quoteLocEnd > 0 && tmpSrc.contains("beer-1")){
                        srcContent = tmpSrc.substring(quoteLocStart + 1, quoteLocEnd - 1);
                    }
                    start = srcLoc + 1;
                    srcLoc = touchlessWebPage.indexOf("src=\"menus/", start);
                } else {
                    Log.v("sengsational", "invalid in page:" + touchlessWebPage );
                }
            }  while (srcLoc > 0);
        }
        return srcContent;
    }

     */

    private String pullTouchlessWebPage(String url) {
        String touchlessPage = null;
        try {
            touchlessPage = LoadDataHelper.getPageContent(url, null, nHttpclient, nCookieStore);
        } catch (Exception e) {
            Log.e("sengsational", "Could not get touchlessPage. " + e.getMessage());
        }
        return touchlessPage;
    }

    public static String pullUntappedDataPage(String url, CloseableHttpClient nHttpclient, BasicCookieStore nCookieStore) {
        String untappedDataPage = null;
        try {
            untappedDataPage = LoadDataHelper.getPageContent(url, null, nHttpclient, nCookieStore);
        } catch (Exception e) {
            Log.e("sengsational", "Could not get untappedDataPage. " + e.getMessage());
        }
        return untappedDataPage;
    }

    private boolean getSiteAccess(String site) {
        try {
            if (InetAddress.getByName(site).equals("")) {
                return false;
            }
        } catch (Exception e) {
            Log.e("sengsational", "Exception on pre-execute getSiteAccess. " + e.getMessage());
            return false;
        }
        return true;
    }

}



