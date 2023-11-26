package com.sengsational.knurder;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.List;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;

import static com.sengsational.knurder.KnurderApplication.getContext;
import static com.sengsational.knurder.TopLevelActivity.DEFAULT_STORE_NAME;
import static com.sengsational.knurder.TopLevelActivity.STORE_NAME_LIST;
import static com.sengsational.knurder.TopLevelActivity.USER_NAME;
import static com.sengsational.knurder.TopLevelActivity.USER_NUMBER;
import static com.sengsational.knurder.TopLevelActivity.prefs;

/**
 * Created by Dale Seng on 5/30/2016.
 */
public class TastedListInteractorImpl  extends AsyncTask<Void, Void, Boolean> implements TastedListInteractor {
    private static final String TAG = TastedListInteractor.class.getSimpleName();

    private CloseableHttpClient nHttpclient = null;
    private BasicCookieStore nCookieStore = null;
    private WebResultListener nListener;
    private String nStoreNumber = null;
    private String nAuthenticationName = null;
    private String nPassword = null;
    private String nMou = null;
    private String nSavePassword = null;
    private HttpResponse nLastResponse = null;
    private String nErrorMessage = null;

    public void getTastedListFromWeb(final String authenticationName, String password, String mou, String savePassword, final String storeNumber, final WebResultListener listener, final DataView dataView) {
            nStoreNumber = storeNumber;
            nAuthenticationName = authenticationName;
            nPassword = password;
            nMou = mou;
            nSavePassword = savePassword;
            nListener = listener;

            if (dataView != null) {
                Log.v(TAG, "dataView:" + dataView.getClass().getName());
                dataView.showProgress(true);
            } else {
                Log.v("sengsational", "TastedListPresenterImpl.getTastedList() dataView was null");
            }
            this.execute((Void) null);
    }

    @Override
    protected void onPreExecute() {
        Log.v("sengsational", "onPreExecute()..."); //Run order #01
        if (TextUtils.isEmpty(nStoreNumber) || TextUtils.isEmpty(nAuthenticationName) || TextUtils.isEmpty(nPassword)) {
            nListener.onError("input parameters problem: " + nStoreNumber + ", " + nAuthenticationName + ", " + nPassword);
            nErrorMessage = "There was a problem with the credentials.";
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
                nListener.onError("client error");
                nErrorMessage = "Problem with the http connection.";
            }
        }
    }

    @Override
    protected void onPostExecute(final Boolean success) {
        // new nHttpclient object each time
        Log.v("sengsational", "onPostExecute with success " + success);
        try {
            nHttpclient.close();
            nHttpclient = null;
        } catch (Exception e) {}

        if (success) {
            Log.v("sengsational", "onPostExecute success: " + success);
            nListener.setToUserPresentation();
            nListener.onFinished();
        } else {
            Log.v("sengsational", "onPostExecute fail.");
            nListener.onError(nErrorMessage);
        }
    }

    @Override
    protected Boolean doInBackground(Void... params) {

        if (nErrorMessage != null) return false;

        if(!getSiteAccess("beerknurd.com")){
            nListener.onError("interent problem");
            nErrorMessage = "Could not reach the web site.";
            return false;
        }

        try {
            do { // This is here to prevent tasted and store lists from updating at the same time.  One will queue behind the other
                Log.v(TAG, "doInBackground is accessing web update lock.");
            } while (!KnurderApplication.getWebUpdateLock(TAG)); // if lock unavailable, this will delay 1/2 second up to 10 seconds, then release.

            String[] userDataNvp = null;
            String userNumberFromPrefs = prefs.getString(USER_NUMBER, "");

            boolean includeLogon = true; //When I took this out, it was faster, but the user stats were not populated.  This is bad: total tasted was not populated, nor was user name.
            if (includeLogon) {
                // >>>Log in<<<< and get the user's statistics page
                // BLOCKS and returns null if it timed-out
                // ------     ----------------------------
                String statsWebPage = pullUserStatsPage();
                if(statsWebPage == null){
                    nListener.onError("login data not found");
                    nErrorMessage = "Did not get logged into the UFO site.";
                    return false;
                }
                userDataNvp = getUserDataNvp(statsWebPage); //  userName (what they called themselves), tastedCount, cardValue, loadedUser (the 5 digit key for this user)
                if(userDataNvp == null || nLastResponse == null) {
                    nListener.onError("user data not found.");
                    nErrorMessage = "Not able to get logged into the UFO site.  Use a web browser to validate you've got the right card number, password, matching saucer location, and appropriate MOU setting.";
                    if ("1".equals(nMou)) nErrorMessage += " Are you really MOU??";
                    return false;
                }else {
                    String userName = userDataNvp[0].split("=")[1];
                    String tastedCount = userDataNvp[1].split("=")[1];
                    nListener.saveValidCredentials(nAuthenticationName, nPassword, nSavePassword, nMou, nStoreNumber, userName, tastedCount);
                }
            }

            // Get the tasted page
            nListener.sendStatusToast("Requested your tasted list. Waiting...", Toast.LENGTH_SHORT);
            String tastedWebPage = null;
            if (userDataNvp != null) {
                tastedWebPage = pullTastedPage(userDataNvp); // this method with String[] parameter will access index 3 to get the userNumber
            } else {
                if (!"".equals(userNumberFromPrefs)) {
                    tastedWebPage = pullTastedPage(userNumberFromPrefs); // this method with String parameter will use userNumber directly
                }
            }
            if(tastedWebPage == null) {
                nListener.onError("tasted data not found");
                nErrorMessage  = "Problem getting your tasted list.";
                return false;
            }
            nListener.sendStatusToast("Got your tasted list.  Logging off...", Toast.LENGTH_SHORT);

            // Put a 'D' into all records with Tasted='T' (was: RemoveInactiveTasted (will be reloaded shortly) and set active tasted to untasted (will be updated))
            if(!flagAllAsTastedStateNotDetermined()) {
                nListener.onError("database error");
                nErrorMessage = "Internal database error.";
                return false;
            }

            // Insert the tasted beers into the database (or just update to tasted if record exists as an active record)
            boolean logoff = false;
            if (!loadTastedFromSite(tastedWebPage, logoff)){
                nListener.onError("data load error");
                nErrorMessage = "Unable to put tasted list into the database.";
                return false;
            }

            // If the 'D' is still in there, then it means it wasn't loaded in the previous step, and needs to be deleted.
            if(!managedEntriesNotJustRefreshed()) {
                nListener.onError("database error");
                nErrorMessage = "Internal database error.";
                return false;
            }

            if (!uploadUserReviews()) {
                nListener.onError("Unable to upload user reviews.");
                nErrorMessage = "Error posting review(s) to Saucer web site.";
                // allow tasted list to be successful, even though reviews were not, so don't return from here.
            }
            nListener.onTastedListSuccess();
            // >>>>>>>>>>Tasted items now loaded into the database<<<<<<<<<<<<<<<<<
        } catch (Exception e) {
            Log.e("sengsational", LoadDataHelper.getInstance().getStackTraceString(e));
            nErrorMessage = "Exception " + e.getMessage();
            return false;
        } finally {
            // try to LOGOFF, even though we might not be logged on.
            try {LoadDataHelper.getPageContent("http://www.beerknurd.com/user/logout", null, nHttpclient, nCookieStore); } catch (Throwable t) {}
            KnurderApplication.releaseWebUpdateLock(TAG);
        }
        Log.v(TAG, "Returning " + true + " from doInBackground");
        return true;
    }

    private String pullTastedPage(String[] userDataNvp) {
        String userNumber = nStoreNumber;
        if (userDataNvp != null) {
            String userNumberNvp = userDataNvp[3];
            try {
                String[] nvp = userNumberNvp.split("=");
                userNumber = nvp[1].trim();
            } catch (Throwable t) {/* doesnt matter... still works with store number IF logged in */}
        }
        return pullTastedPage(userNumber);
    }

    private String pullTastedPage(String userNumber) {
        String beersTastedPage = null;
        try {
            int timeoutSeconds = 90;
            beersTastedPage = LoadDataHelper.getPageContent(("https://www.beerknurd.com/api/tasted/list_user/" + userNumber), nLastResponse, nHttpclient, nCookieStore, timeoutSeconds);  //<<<<<<<<<<<<<<<<<PULL TASTED<<<<<<<<<<<<<<<<<<<
            prefs.edit().putString(USER_NUMBER, userNumber).apply();
        } catch (Exception e) {
            Log.e("sengsational", "Could not get tastedListPage. " + e.getMessage());
            nListener.sendStatusToast(e.getMessage(), Toast.LENGTH_LONG);
            return null;
        }
        return beersTastedPage;
    }

    private boolean flagAllAsTastedStateNotDetermined() {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            db.execSQL("update UFO set TASTED = 'D'");
        } catch (Exception e) {
            Log.e("sengsational", "Could not get prepare database for new tasted record loading. " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private boolean loadTastedFromSite(String tastedWebPage, boolean logoff) {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            int currentCount = getCount(db, "UFO");
            Log.v("sengsational", "Starting out we had " + currentCount + " records.");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(KnurderApplication.getContext());
            boolean showOnlyCurrentTasted = prefs.getBoolean("current_beers_switch", true);

            ////////////////////////////////////////////////////////
            // page contains the active list for the selected store
            ////////////////////////////////////////////////////////
            int updateCount = 0;
            String[] items = tastedWebPage.split("\\},\\{");
            if (items.length > 1) {
                Log.v("sengsational", "The tasted list (including bottled) had " + items.length + " items.");
                for (String string : items) {
                    SaucerItem modelItem = new SaucerItem();
                    modelItem.setStoreNameAndNumber(nStoreNumber, db);
                    // Load this item from the page into SaucerItem
                    //modelItemclear();
                    modelItem.load(string);
                    if (showOnlyCurrentTasted && !modelItem.isOnCurrentPlate()) continue; // <<<<<<<< Do not load tasted from earlier plates

                    // We know this is a tasted beer, so set to true
                    modelItem.setTasted(true);

                    String brewId = modelItem.getBrew_id(); // The brew EXTRA_ID we're working with in this loop

                    // This could be in the database already, so we need to find out if it is.
                    // This probably is in the database with TASTED='D'.  If so, we will update the record and TASTED will be 'T'
                    Cursor cursor = db.query("UFO", new String[] {"BREW_ID, HIGHLIGHTED, CITY, ACTIVE, NEW_ARRIVAL, BREWER, USER_REVIEW, USER_STARS, REVIEW_FLAG"}, "BREW_ID = ?", new String[] {brewId}, null, null, null);
                    if (cursor.moveToFirst()) { // since we have already removed the tasted, if true (record found), we need to delete and replace it with this tasted with active = "Y"

                        // preserve local highlighted flag
                        String highlighted = cursor.getString(1);
                        if ("T".equals(highlighted)) {
                            modelItem.setHighlighted("T");
                        } else if ("X".equals(highlighted)){
                            modelItem.setHighlighted("X");
                        } else {
                            modelItem.setHighlighted("F");
                        }

                        // preserve local city, since tasted seems to come as "SomeCity" as opposed to "SomeCity, XX", where XX is the state code
                        String city = cursor.getString(2);
                        modelItem.setCity(city);

                        // preserve local brewer, since tasted seems to come as the beer name
                        String brewer = cursor.getString(5);
                        modelItem.setBrewer(brewer);

                        // preserve local active
                        String active = cursor.getString(3);
                        if ("T".equals(active)){
                            modelItem.setActive(true);
                        } else {
                            modelItem.setActive(false);
                        }

                        // preserve local new_arrival
                        String new_arrival = cursor.getString(4);
                        if ("T".equals(new_arrival)){
                            modelItem.setNewArrival(true);
                        } else {
                            modelItem.setNewArrival(false);
                        }

                        // preserve local review when web review doesn't exist
                        String userReviewLocal = cursor.getString(6);
                        String userStarsLocal = cursor.getString(7);
                        String reviewFlagLocal = cursor.getString(8);
                        String userReviewWeb = modelItem.getUserReview();
                        boolean webReviewExists = userReviewWeb != null && !"null".equals(userReviewWeb);

                        if (webReviewExists) {
                            // our model has it, and it will go into the database
                            //Log.v(TAG, "this review came from the web and should populate our db " + userReviewWeb);
                            modelItem.setReviewFlag("W");
                        } else if ("L".equals(reviewFlagLocal)) {
                            // the model from the web has no review, but we have one.  We must preserve it.
                            modelItem.setUserReview(userReviewLocal);
                            modelItem.setUserStars(userStarsLocal);
                            modelItem.setReviewFlag(reviewFlagLocal);
                        }

                        //Log.v(TAG, "updating database with " + modelItem);
                        db.update("UFO", modelItem.getContentValues(), "BREW_ID = ?", new String[] {brewId});
                        //StringBuilder sb = new StringBuilder();
                        //sb.append(modelItem.getContentValues().get("ACTIVE"));
                        //sb.append(modelItem.getContentValues().get("TASTED"));
                        //sb.append(modelItem.getContentValues().get("HIGHLIGHTED"));
                        //sb.append(modelItem.getContentValues().get("NEW_ARRIVAL"));
                        //Log.v(TAG, "TLI Update database      " + sb + " " + modelItem.getContentValues().toString());
                        updateCount++;
                    } else {
                        // a tasted item that's not in the database.  Set default flags
                        modelItem.setActive(false); // presume it's not active, or it would have been found in the database
                        modelItem.setHighlighted("F"); // not in the database, can't be highlighted
                        modelItem.setNewArrival("F"); // not in the database, can't be a new arrival
                        currentCount++;
                        //StringBuilder sb = new StringBuilder();
                        //sb.append(modelItem.getContentValues().get("ACTIVE"));
                        //sb.append(modelItem.getContentValues().get("TASTED"));
                        //sb.append(modelItem.getContentValues().get("HIGHLIGHTED"));
                        //sb.append(modelItem.getContentValues().get("NEW_ARRIVAL"));
                        //Log.v(TAG, "TLI Insert into database " + sb + " " + modelItem.getContentValues().toString());
                        db.insert("UFO", null, modelItem.getContentValues());
                    }
                    if (cursor != null) cursor.close();
                }
                Log.v("sengsational", "After loading active, and updating " + updateCount + ", we had " + getCount(db, "UFO") + " records. That should equal " + currentCount + ".");
                //Log.v("sengsational", "We had " + getCount(db, "STYLES") + " style records.");
            } else {
                Log.v("sengsational", "Found nothing in the beersWeb page.");
                nListener.onError("no data found");
            }

            if (logoff) LoadDataHelper.getPageContent("http://www.beerknurd.com/user/logout", null, nHttpclient, nCookieStore); // Log off
        } catch (Exception e) {
            Log.e("sengsational", LoadDataHelper.getInstance().getStackTraceString(e));
            nListener.onError("exception " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }













    private boolean uploadUserReviews() {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(KnurderApplication.getContext());
            boolean showOnlyCurrentTasted = prefs.getBoolean("current_beers_switch", true);

            // Select REVIEW_FLAG = "L" (local) and TASTED = "T" (tasted)
            Cursor cursor = db.query("UFO", new String[] {"REVIEW_ID, USER_STARS, USER_REVIEW, TIMESTAMP, NAME, REVIEW_FLAG, TASTED"}, "REVIEW_FLAG = ? AND TASTED = ?", new String[] {"L", "T"}, null, null, null);
            Log.v(TAG, "There were " + cursor.getCount() + " reviews to upload.");
            StringBuffer reviewIdsDone = new StringBuffer("(");
            while (cursor.moveToNext()) {

                String reviewId = cursor.getString(0);
                int stars = 3;
                String starsText = cursor.getString(1);
                try {stars = Integer.parseInt(starsText);} catch (Throwable t) {Log.e(TAG, "ERROR: Failed to parse stars text [" + starsText + "]" );}
                String reviewText = cursor.getString(2);
                String timestamp = cursor.getString(3);
                String beerName = cursor.getString(4);
                String saucerName = prefs.getString(STORE_NAME_LIST, DEFAULT_STORE_NAME);
                String userName = prefs.getString(USER_NAME, "");


                /*
                String reviewId = "17888129";
                int stars = 4;
                String reviewText = "This is a nice beer. Love the style and this is a great example of it. Check out their saison too, it's a good one.";
                String timestamp = "1531717343";
                String saucerName = "Charlotte Flying Saucer";
                String beerName = "Allagash White";
                String userName = "sengsational";
                */


                Log.v(TAG, "TO BE UPLOADED: " + reviewId + " " + stars + " " + timestamp + " " + beerName + " " + saucerName + " " + userName + " " + reviewText + " "  );
                boolean successful = postReview(reviewId, stars, reviewText, timestamp, saucerName, beerName, userName);
                if (successful) {
                    reviewIdsDone.append("'").append(reviewId).append("',");
                }
            }
            if (cursor != null) cursor.close();


            // reviewIdsDone maybe (
            // reviewIdsDone maybe ('17888129',
            // reviewIdsDone maybe ('17888129','17888129',
            int len = reviewIdsDone.length();
            if (len > 10)  { // have at least one review posted
                reviewIdsDone.deleteCharAt(len - 1).append(")"); // replace ending comma with paren
                db.execSQL("update UFO set REVIEW_FLAG='W' where REVIEW_ID IN " + reviewIdsDone.toString());
            }
        } catch (Exception e) {
            Log.e("sengsational", LoadDataHelper.getInstance().getStackTraceString(e));
            nListener.onError("exception " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }














    // In this method, we examine tasted records that were not re-flagged as tasted, above, and are still in an undetermined state.
    private boolean managedEntriesNotJustRefreshed() {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            int currentCount = getCount(db, "UFO");
            Log.v("sengsational", "Before removing old tasted records we had " + currentCount + " records.");

            // First, Prevent deletion of active records.  Never delete actives.
            db.execSQL("update UFO set TASTED='F' where TASTED='D' and ACTIVE='T'");

            // Next, Prevent deletion of highlighted records.  Let the user do that.
            db.execSQL("update UFO set TASTED='F' where TASTED='D' and (HIGHLIGHTED='T' or HIGHLIGHTED='X')");

            db.execSQL("delete from  UFO where TASTED = 'D'");
            int newCount = getCount(db, "UFO");
            Log.v("sengsational", "After  removing old tasted records we had " + newCount + " records.");
        } catch (Exception e) {
            Log.e("sengsational", "Could not get prepare database for new tasted record loading. " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private String pullUserStatsPage() {
        String userStatsPage = null;
        try {
            nListener.sendStatusToast("Going to logon page...", Toast.LENGTH_SHORT);
            //ADDED https
            String loginFormPage = LoadDataHelper.getPageContent("https://www.beerknurd.com/user", null, nHttpclient, nCookieStore);                       //<<<<<<<<<GET INITIAL LOGIN FORM PAGE<<<<<<<<<<<<<<<<<<<
            Log.v("sengsational", "doInBackground() Ran first page"); // Run Order #14

            // Scrape the page for the parameters to submit to the login page
            List<NameValuePair> postParams = LoadDataHelper.getInstance().getFormParams(loginFormPage, nAuthenticationName, nPassword, nMou, nStoreNumber);

            Log.v("sengsational", "doInBackground() Got form parameters from first page.  Logging in with " + nAuthenticationName + " " + nPassword + " " + nMou + " " + nStoreNumber); // Run Order #17
            // Added https
            nListener.sendStatusToast("Request sent.  Might take a LONG time!!! Waiting for logon...", Toast.LENGTH_LONG);

            // The following sendPost() BLOCKS and THROWS an exception if the page times-out
            //                          ------     -----------------------------------------
            nLastResponse = LoadDataHelper.getInstance().sendPost("https://www.beerknurd.com/user", postParams, nHttpclient, "logon", nCookieStore, 240);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<

            userStatsPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            nListener.sendStatusToast("Log on completed OK.", Toast.LENGTH_SHORT);
            Log.v("sengsational", "doInBackground() Sent form with fields filled-in.  Should be logged-in now."); // Run Order #22
        } catch (Exception e) {
            Log.e("sengsational", "Could not get userStatsPage. " + e.getMessage());
        }
        return userStatsPage;
    }

    private boolean postReview(String reviewId, int stars, String reviewText, String timestamp, String saucerName, String beerName, String userName) {
        String afterPostPage = null;
        boolean successful = false;
        try {
            String reviewFormPage = LoadDataHelper.getPageContent("https://www.beerknurd.com/node/" + reviewId + "/edit", null, nHttpclient, nCookieStore)  ;
            Log.v(TAG, "reviewFormPage ran and has " + reviewFormPage.length() + " characters.");
            // Scrape the page for the parameters to submit to the review page
            List<NameValuePair> postParams = LoadDataHelper.getInstance().getReviewFormParams(reviewFormPage, stars, reviewText, timestamp, saucerName, beerName, userName);
            Log.v(TAG, "got " + postParams.size() + " params from review form page.");
            //try {Log.v(TAG, "Sleeping 5 seconds to see if this helps the post process."); Thread.sleep(5000);} catch (Exception e) {}
            nLastResponse = LoadDataHelper.getInstance().sendPost("https://www.beerknurd.com/node/" + reviewId + "/edit", postParams, nHttpclient, "review", nCookieStore, 45);
            afterPostPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            successful = !afterPostPage.contains("Error message");
            if (!successful) {
                Log.v(TAG, "FAILED TO POST: The text \"Error message\" was found in the response from the saucer site.");
            }
            boolean printThePage = false;
            if (printThePage) {
                int pageLength = afterPostPage.length();
                for (int i = 0; i < pageLength; i = i+100) {
                    int charPos = Math.min( i + 100, pageLength);
                    Log.v("HTML", afterPostPage.substring(i, charPos));
                }
            }
            Log.v(TAG, "Form post with review for " + beerName + " that was " + reviewText.length() + " characters long.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to post review. " + e.getMessage());
        }
        return successful;
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
    private String[] getUserDataNvp(String page) { //  userName, tastedCount, cardValue, loadedUser

        Document doc = Jsoup.parse(page);
        Elements userInfo = doc.getElementsByClass("user-info");
        String[] returnString = new String[4];
        if (userInfo.size() < 1) {
            Log.v("sengsational","not logged-in");
            return null;
        } else {
            try {
                returnString[0] = "userName=" + userInfo.get(0).text();
                Elements profileItemValues = doc.getElementsByClass("profile-item-value");
                for (Element element : profileItemValues) {
                    if (element != null && element.text().contains("brews")){
                        String[] countLineWords = element.text().split(" ");
                        if (countLineWords.length > 0) returnString[1] = "tastedCount=" + countLineWords[0];
                        break;
                    }
                }
                Elements inputElements = doc.getElementsByTag("input");
                for (int i = 0; i < inputElements.size(); i++) {
                    Element inputElement = inputElements.get(i);
                    if ("card_num".equals(inputElement.id())){
                        String cardValue = inputElement.val();
                        if (cardValue != null){
                            returnString[2] = "cardValue=" + cardValue.replaceAll("[^\\d.]", "");
                        }
                    } else if ("loaded_user".equals(inputElement.id())){
                        String loadedUser = inputElement.val();
                        returnString[3] = "loadedUser = " + loadedUser;
                    }
                }
                // This only required for the Captain Keith's Quiz
                SharedPreferences.Editor editor = TopLevelActivity.prefs.edit();
                try {
                    Elements profileItems = doc.getElementsByClass("profile-item");
                    boolean firstElement = true;
                    for (Element element : profileItems) {
                        if (firstElement) {
                            if (element != null) {
                                int commaLoc = element.text().indexOf(",");
                                if (commaLoc > 0) {
                                    editor.putString(TopLevelActivity.FIRST_NAME, element.text().substring(0,commaLoc).trim());
                                }
                            }
                            firstElement = false;
                            continue;
                        }

                        if (element != null && element.text().startsWith("Email")) {
                            Elements classNameElements = element.getElementsByClass("profile-item-value" );
                            for(Element subElement : classNameElements) {
                                if(subElement != null && subElement.text().contains("@")) {
                                    editor.putString(TopLevelActivity.EMAIL_ADDRESS, subElement.text().trim());
                                }
                                break;
                            }
                        }
                    }
                } catch (Throwable t) {
                    Log.v(TAG, "Unable to parse stuff for the quiz. " + t.getMessage());
                } finally {
                    editor.apply();
                }

            } catch (Throwable t) {
                Log.v("sengsational", "Unable to parse good login page." + t.getMessage());
                return null;
            }
            return returnString;
        }
    }

    private int getCount(SQLiteDatabase db, String dbName){
        Cursor cursor = db.query(dbName, new String[]{"COUNT(*) AS count"}, null, null, null, null, null);
        int count = -1;
        if (cursor.moveToFirst()){
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

}

interface TastedListInteractor {
    void getTastedListFromWeb(final String authenticationName, final String password, final String savePassword, final String mou,  final String storeNumber, final WebResultListener listener, final DataView dataView);
}
