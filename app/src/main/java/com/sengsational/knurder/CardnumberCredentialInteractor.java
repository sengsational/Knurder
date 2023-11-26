package com.sengsational.knurder;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

/**
 * Created by Dale Seng on 5/30/2016.
 */
public class CardnumberCredentialInteractor  extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = CardnumberCredentialInteractor.class.getSimpleName();

    private CloseableHttpClient nHttpclient = null;
    private BasicCookieStore nCookieStore = null;
    private WebResultListener nListener;
    private String nStoreNumber = null;
    private String nStoreNumberOfList = null; // If they're not at the store that issued their card, the destination for brews on que needs to be different.
    private String nCardnumber = null;
    private String nCardPin = null;
    private String nMou = null;
    private String nBrewIds = null;
    private String nSavePin = null;
    private HttpResponse nLastResponse = null;
    private String nErrorMessage = null;

    public void validateCardnumberCredentialsFromWeb(final String cardNumber, String cardPin, String mou, String savePin, final String storeNumber, final String storeNumberOfList, final String brewIds, final WebResultListener listener) {
            nStoreNumber = storeNumber;
            nStoreNumberOfList = storeNumberOfList;
            nCardnumber = cardNumber;
            nCardPin = cardPin;
            nMou = mou;
            nSavePin = savePin;
            nListener = listener;
            nBrewIds = brewIds;
            this.execute((Void) null);
    }

    @Override
    protected void onPreExecute() {
        Log.v("sengsational", "onPreExecute()..."); //Run order #01
        if (TextUtils.isEmpty(nStoreNumber) || TextUtils.isEmpty(nCardnumber) || TextUtils.isEmpty(nCardPin)) {
            nListener.onError("input parameters problem: " + nStoreNumber + ", " + nCardnumber + ", " + nCardPin);
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
            Log.v(TAG, "Internet problem: could not reach beerknurd.com");
            return false;
        }

        try {
            do { // This is here to prevent tasted and store lists from updating at the same time.  One will queue behind the other
                Log.v(TAG, "doInBackground is accessing web update lock.");
            } while (!KnurderApplication.getWebUpdateLock(TAG)); // if lock unavailable, this will delay 1/2 second up to 10 seconds, then release.
            // >>>Attempt to validate card number with pin
            String kioskLoggedInPage = validateCardCredentials();
            if(kioskLoggedInPage == null || !kioskLoggedInPage.contains("member-header")){
                nListener.onError("card number login data not found");
                nErrorMessage = "Card number not authenticated.";
                return false;
            }

            if (nBrewIds != null && nBrewIds.contains(",")) {
                String currentQueuePage = LoadDataHelper.getPageContent("https://www.beerknurd.com/tapthatapp/memberQueue.php", null, nHttpclient, nCookieStore);
                String beerIdsCurrentlyOnTheWebQueue = LoadDataHelper.getCurrentQueuedBeerNamesFromHtml(currentQueuePage);
                Log.v(TAG, "beerIdsCurrentlyOnTheWebQueue [" + beerIdsCurrentlyOnTheWebQueue + "]");
                // 1) Set all database items to queued = 'F',
                // 2) set ONLY those in beerIdsCurrentlyOnTheWebQueue as queued = 'T'
                String[] brewIdsFlaggedInTheApp = nBrewIds.split(",");
                resetQueued(beerIdsCurrentlyOnTheWebQueue);  // After this, our CURRENTLY_QUEUED is aligned with the web.

                String storeName = StoreNameHelper.getInstance().getStoreNameFromNumber(nStoreNumberOfList, null);
                StringBuffer brewIdsNeedingTimestamps = new StringBuffer();
                int sentCount = 0;
                for (String appFlaggedBrewId : brewIdsFlaggedInTheApp) {
                    // appFlagBrewId might be already tasted.  The saucer does not allow already tasted to be queued.
                    if (brewIdIsTasted(appFlaggedBrewId)) {
                        continue;
                    }
                    // appFlaggedBrewId's can exist on the web or not.
                    // If the web list contains one that is in our list (item is in both), we do nothing with it here.
                    if (beerIdsCurrentlyOnTheWebQueue.contains(appFlaggedBrewId)) {
                        continue;
                    }
                    // appFlaggedBrewId is NOT on in the web que.  It's missing on the web.
                    // But we only add it if it doesn't have a current time stamp.
                    if (hasCurrentTimestamp(appFlaggedBrewId)) {
                        continue;
                    }

                    brewIdsNeedingTimestamps.append(appFlaggedBrewId).append(",");
                    try {
                        String queueUrl = "https://www.beerknurd.com/tapthatapp/queue-up-brew.php?action=member&brewID=" + appFlaggedBrewId + "&storeID=" + nStoreNumberOfList;
                        String queueitupResultPage = LoadDataHelper.getPageContent(queueUrl, null, nHttpclient, nCookieStore);
                        Thread.sleep(500); // Try not to overdrive the saucer web site
                        sentCount++;
                    } catch (Throwable t) {
                        Log.v(TAG, "ERROR: Failed to post brew ID " + t.getMessage());
                    }
                }
                nListener.sendStatusToast("Sent " + sentCount + (sentCount>1?" untasted beers to ":" untasted beer to ") + storeName + " queue.", Toast.LENGTH_SHORT);
                setQueuedTimestamp(brewIdsNeedingTimestamps.toString());
            } else {
                Log.v(TAG, "No BrewIds found " + nBrewIds);
            }

            nListener.onSuccess(nSavePin, nCardPin, nMou, nCardnumber);
        } catch (Exception e) {
            Log.e("sengsational", LoadDataHelper.getInstance().getStackTraceString(e));
            nErrorMessage = "Exception " + e.getMessage();
            return false;
        } finally {
            KnurderApplication.releaseWebUpdateLock(TAG);
        }
        Log.v(TAG, "Returning " + true + " from doInBackground");
        return true;
    }

    private boolean hasCurrentTimestamp(String appFlaggedBrewId) {
        boolean hasCurrentTimeStamp = false;
        SQLiteDatabase db = null;
        Cursor cursor = null;
        Context context = KnurderApplication.getContext();
        if (context == null) return false;

        String queStamp = ""; //""1970 01 01 01 01";
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(context) ;
            db = ufoDatabaseAdapter.openDb(context);                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("UFO", new String[] {"QUE_STAMP"}, "BREW_ID = ?", new String[] {appFlaggedBrewId}, null, null, null);
            if (cursor.moveToFirst()) {
                queStamp = cursor.getString(0);
            }
            cursor.close();
            Log.v(TAG, "queStamp: " + queStamp);

        } catch (SQLiteException e) {
            Log.v(TAG, "Failed to access database " + e.getClass().getName() + " " + e.getMessage());
        } finally {
            try {cursor.close();} catch (Throwable t) {}
            try {db.close();} catch (Throwable t) {}
        }
        if (queStamp != null && queStamp.length() > 0) {
            try {
                long ageInMs = new Date().getTime() - SaucerItem.qdf.parse(queStamp).getTime();
                Log.v(TAG, "ageInMs " + ageInMs + " FOUR_HOURS " + SaucerItem.FOUR_HOURS + " queued [" + appFlaggedBrewId + "] queStamp [" + queStamp + "]");
                if (ageInMs > 0 && ageInMs < SaucerItem.FOUR_HOURS) {
                    Log.v(TAG, "Has current timestamp.");
                    hasCurrentTimeStamp = true;
                } else {
                    Log.v(TAG, "Has older timestamp.");
                }
            } catch (Throwable t) {
                Log.v(TAG, "ERROR: Failed to parse timestamp " + t.getClass().getName() + " " + t.getMessage());
            }
        } else {
            Log.v(TAG, "Item " + appFlaggedBrewId + " had no timestamp.");
        }
        return hasCurrentTimeStamp;
    }

    private void resetQueued(String currentQueuedBeerIds) {
        SQLiteDatabase db = null;
        Context context = KnurderApplication.getContext();
        if (context == null) return;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(context) ;
            db = ufoDatabaseAdapter.openDb(context);                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            // first clear the queued
            db.execSQL("UPDATE UFO SET CURRENTLY_QUEUED = 'F'");
            String[] brewIdArray = currentQueuedBeerIds.split(",");
            for (String brewId: brewIdArray) {
                Log.v(TAG, "updating [" + brewId + "] as queued" );
                db.execSQL("UPDATE UFO SET CURRENTLY_QUEUED = 'T' WHERE BREW_ID ='" + brewId.trim() +"'");
            }
        } catch (SQLiteException e) {
            Log.v(TAG, "Database error. " + e.getClass().getName() + e.getMessage());
        } finally {
            try {db.close();} catch (Throwable t) {}
        }
    }

    private void setQueuedTimestamp(String brewIdsNeedingTimestamp) {
        String dateString = SaucerItem.qdf.format(new Date());
        SQLiteDatabase db = null;
        Context context = KnurderApplication.getContext();
        if (context == null) return;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(context) ;
            db = ufoDatabaseAdapter.openDb(context);                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            String[] brewIdArray = brewIdsNeedingTimestamp.split(",");
            for (String brewId: brewIdArray) {
                Log.v(TAG, "updating [" + brewId + "] with timestamp." );
                db.execSQL("UPDATE UFO SET QUE_STAMP = '" + dateString + "' WHERE BREW_ID ='" + brewId.trim() +"'");
                Log.v(TAG, "updating [" + brewId + "] as queued." );
                db.execSQL("UPDATE UFO SET CURRENTLY_QUEUED = 'T' WHERE BREW_ID ='" + brewId.trim() +"'");
            }
        } catch (SQLiteException e) {
            Log.v(TAG, "Database error. " + e.getClass().getName() + e.getMessage());
        } finally {
            try {db.close();} catch (Throwable t) {}
        }
    }

    private boolean brewIdIsTasted(String appFlaggedBrewId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        Context context = KnurderApplication.getContext();
        if (context == null) return false;
        String tasted = "?";
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(context) ;
            db = ufoDatabaseAdapter.openDb(context);                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("UFO", new String[] {"TASTED"}, "BREW_ID = ?", new String[] {appFlaggedBrewId}, null, null, null);
            if (cursor.moveToFirst()) {
                tasted = cursor.getString(0);
            }
            cursor.close();
            Log.v(TAG, "tasted: " + tasted);
        } catch (SQLiteException e) {
            Log.v(TAG, "Failed to access database " + e.getClass().getName() + " " + e.getMessage());
        } finally {
            try {cursor.close();} catch (Throwable t) {}
            try {db.close();} catch (Throwable t) {}
        }
        return "T".equals(tasted);
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

    private String validateCardCredentials() {
        String credentialOkPage = null;
        String localMouString = "0".equals(nMou)?"no":"yes";
        String localStoreIdString = StoreNameHelper.getTwoCharacterStoreIdFromStoreNumber(nStoreNumber);
        String cardData = "%" + localStoreIdString + nCardnumber;
        String localVarCharStoreIdString = StoreNameHelper.getVarCharacterStoreIdFromStoreNumber(nStoreNumber);
        try {
            String vistorMemberPage = LoadDataHelper.getPageContent("https://www.beerknurd.com/tapthatapp/classes/getStore.php?loc=" + localVarCharStoreIdString, null, nHttpclient, nCookieStore);                       //<<<<<<<<<GET INITIAL LOGIN FORM PAGE<<<<<<<<<<<<<<<<<<<
            Log.v("sengsational", "doInBackground() Ran vistorMember page"); // Run Order #14
            // We get cookies set: kiosk_pass=true, store_id=13888, store_name=Charlotte, PHPSESSID=(GUID).
            // List <Cookie> cookies = nCookieStore.getCookies();


            // The above page has a form with "homestore": Card Assigned Store.  %ad=Addision, %ch=Charlotte, etc
            // The above page has a form with "cardNumber": (digits)
            // The above page has a form with "mouOpt": ("yes" or "no")

            List <NameValuePair> firstPostParms = new ArrayList<NameValuePair>();
            firstPostParms.add(new BasicNameValuePair("homestore","%" + localStoreIdString));
            firstPostParms.add(new BasicNameValuePair("cardNumber",nCardnumber));
            firstPostParms.add(new BasicNameValuePair("mouOpt",localMouString));
            firstPostParms.add(new BasicNameValuePair("submit","Beam+Me+Up!"));
            // The above page javascript builds this:
            firstPostParms.add(new BasicNameValuePair("cardNum","%" + localStoreIdString + nCardnumber + "=?"));

            // Submitting the form with card number on it from the visitorMember page:
            nLastResponse = LoadDataHelper.getInstance().sendPost("https://www.beerknurd.com/tapthatapp/kiosk.php", firstPostParms, nHttpclient, "cardnumber", nCookieStore, 45);

            //String enterYourPinPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            //Log.v(TAG, "Should be 'Enter Your Pin' page\n" + enterYourPinPage);

            Log.v("sengsational", "doInBackground() (probably) got session ID.  Logging in with " + nCardnumber + " " + nCardPin + " " + nMou + " " + nStoreNumber); // Run Order #17

            List <NameValuePair> postParams = new ArrayList<NameValuePair>();
            postParams.add(new BasicNameValuePair("cardData","%" + localStoreIdString + nCardnumber));
            postParams.add(new BasicNameValuePair("signinPinNumber", nCardPin));
            postParams.add(new BasicNameValuePair("submitPin", "Beam+Me+Up!"));

            // ? = %3F
            // % = %25
            String goodWay = "=%3F";
            String appleWay = "%3D?&no=0";
            String emptyWay = "";
            nLastResponse = LoadDataHelper.getInstance().sendPost("https://www.beerknurd.com/tapthatapp/signin.php?cd=%25" + localStoreIdString + nCardnumber + appleWay, postParams, nHttpclient, "cardnumber", nCookieStore, 45);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<
            //nLastResponse = LoadDataHelper.getInstance().sendPost("https://www.beerknurd.com/tapthatapp/signin.php", postParams, nHttpclient, "cardnumber", nCookieStore, 45);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<
            credentialOkPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            Log.v("sengsational", "doInBackground() Sent form with fields filled-in.  Should be logged-in now."); // Run Order #22
            // Log.v(TAG, "response page from the post:\n" + credentialOkPage);

        } catch (Exception e) {
            Log.e("sengsational", "Could not get credentialOkPage. " + e.getMessage());
        }
        return credentialOkPage;
    }

}

