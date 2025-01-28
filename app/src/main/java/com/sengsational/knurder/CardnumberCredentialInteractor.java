package com.sengsational.knurder;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private String nBrewNames = null;
    private String nStoreId = null;
    private String nSavePin = null;
    private HttpResponse nLastResponse = null;
    private String nErrorMessage = null;

    public void validateCardnumberCredentialsFromWeb(final String cardNumber, String cardPin, String mou, String savePin, final String storeNumber, final String storeNumberOfList, final String brewIds, final String brewNames, final String storeId, final WebResultListener listener) {
            nStoreNumber = storeNumber;
            nStoreNumberOfList = storeNumberOfList;
            nCardnumber = cardNumber;
            nCardPin = cardPin;
            nMou = mou;
            nSavePin = savePin;
            nListener = listener;
            nBrewIds = brewIds;
            nBrewNames = brewNames;
            Log.v(TAG, "nStoreId: " + nStoreId + " (validateCardNumberCredentials)");
            nStoreId = storeId;
            this.execute((Void) null);
    }

    @Override
    protected void onPreExecute() {
        Log.v(TAG, "onPreExecute()..."); //Run order #01
        if (TextUtils.isEmpty(nStoreNumber) || TextUtils.isEmpty(nCardnumber) || TextUtils.isEmpty(nCardPin)) {
            nListener.onError("input parameters problem: " + nStoreNumber + ", " + nCardnumber + ", " + nCardPin);
            nErrorMessage = "There was a problem with the credentials.";
        }
        // set-up a single nHttpclient
        if (nHttpclient != null) {
            Log.e(TAG, "Attempt to set-up more than one HttpClient!!");
        } else {
            try {
                nCookieStore = new BasicCookieStore();
                HttpClientBuilder clientBuilder = HttpClientBuilder.create();
                nHttpclient = clientBuilder.setRedirectStrategy(new LaxRedirectStrategy()).setDefaultCookieStore(nCookieStore).build();
                nHttpclient.log.enableDebug(true);
                Log.v(TAG, "nHttpclient object created."); //Run order #02
            } catch (Throwable t) {//
                Log.v(TAG, "nHttpclient object NOT created. " + t.getMessage());
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                Log.v(TAG, sw.toString());
                nListener.onError("client error");
                nErrorMessage = "Problem with the http connection.";
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                nListener.onError("client error");
                nErrorMessage = "The UFO web site no longer accepts connections from older Android devices.  Sorry.  Nothing I can do about it.\n\nPlease try on a device with Jelly Bean or higher.";
            }
        }
    }

    @Override
    protected void onPostExecute(final Boolean success) {
        // new nHttpclient object each time
        Log.v(TAG, "onPostExecute with success " + success);
        try {
            nHttpclient.close();
            nHttpclient = null;
        } catch (Exception e) {}

        if (success) {
            Log.v(TAG, "onPostExecute success: " + success);
            nListener.setToUserPresentation();
            nListener.onFinished();
        } else {
            Log.v(TAG, "onPostExecute fail.");
            nListener.onError(nErrorMessage);
        }
    }

    @Override
    protected Boolean doInBackground(Void... params) {

        if (nErrorMessage != null) {
            Log.v(TAG, "doInBackground ERROR: " + nErrorMessage);
            return false;
        }

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
            if(kioskLoggedInPage == null || !(kioskLoggedInPage.contains("member-greeting-header"))){
                nListener.onError("card number login data not found");
                nErrorMessage = "Card number not authenticated.";
                Log.v(TAG, kioskLoggedInPage.substring(Math.min(kioskLoggedInPage.length(), 3753)));
                Log.v(TAG, nErrorMessage);
                return false;
            }

            // Here we do   WEB OPERATION: Read the currently queued
            //            LOCAL OPERATION: Get locally flagged beers
            //            LOCAL OPERATION: Set local QUEUED variable to match the web
            //
            if (nBrewIds != null && nBrewIds.contains(",")) {
                String currentQueuePage = LoadDataHelper.getPageContent("https://tapthatapp.beerknurd.com/memberQueues.php", null, nHttpclient, nCookieStore);
                Map<String, String> beerIdsCurrentlyOnTheWebQueue = LoadDataHelper.getCurrentQueuedBeerNamesFromHtml(currentQueuePage);
                Log.v(TAG, "beerIdsCurrentlyOnTheWebQueue [" + beerIdsCurrentlyOnTheWebQueue + "]");
                Log.v(TAG, "brewNames [" + nBrewNames + "]");
                // 1) Set all database items to queued = 'F',
                // 2) set ONLY those in beerIdsCurrentlyOnTheWebQueue as queued = 'T'
                String[] brewIdsFlaggedInTheApp = nBrewIds.split(",");
                String[] brewNamesFlaggedInTheApp = nBrewNames.split(",");
                resetQueued(beerIdsCurrentlyOnTheWebQueue);  // After this, our CURRENTLY_QUEUED is aligned with the web (local database activity only)

                String storeName = StoreNameHelper.getInstance().getStoreNameFromNumber(nStoreNumberOfList, null);
                StringBuffer brewIdsNeedingTimestamps = new StringBuffer();
                StringBuffer brewNamesNeedingTimestamps = new StringBuffer();
                int sentCount = 0;
                for (int i = 0; i < brewIdsFlaggedInTheApp.length; i++) {
                    String appFlaggedBrewId = brewIdsFlaggedInTheApp[i];
                    String appFlaggedBrewName = brewNamesFlaggedInTheApp[i];
                    // appFlagBrewId might be already tasted.  The saucer does not allow already tasted to be queued.
                    if (brewIdIsTasted(appFlaggedBrewId)) {
                        Log.v(TAG, "The beer " + appFlaggedBrewName + " is tasted...not sending it.");
                        continue;
                    }
                    // appFlaggedBrewId's can exist on the web or not.
                    // If the web list contains one that is in our list (item is in both), we do nothing with it here.
                    if (beerIdsCurrentlyOnTheWebQueue.containsKey(appFlaggedBrewId)) {
                        Log.v(TAG, "The beer " + appFlaggedBrewName + " is already on the web queue...not sending it.");
                        continue;
                    }
                    // appFlaggedBrewId is NOT on in the web que.  It's missing on the web.
                    // But we only add it if it doesn't have a current time stamp.
                    if (hasCurrentTimestamp(appFlaggedBrewId)) {
                        Log.v(TAG, "The beer " + appFlaggedBrewName + " has a current time stamp...not sending it.");
                        continue;
                    }
                    // it's possible that they have flagged a beer in one location, but changed location in the app. Only locally available beers
                    // should get sent to the queue.
                    if (!brewIdIsActive(appFlaggedBrewId)) {
                        Log.v(TAG, "The beer " + appFlaggedBrewName + " was not active, not sent.");
                        continue;
                    }
                    brewIdsNeedingTimestamps.append(appFlaggedBrewId).append(",");
                    Log.v(TAG, "appFlaggedBrewname:" + appFlaggedBrewName);
                    brewNamesNeedingTimestamps.append(appFlaggedBrewName).append(",");
                    sentCount++;
                }
                if (sentCount > 0) {
                    int addedCount = addBeersToQueue(brewIdsNeedingTimestamps.toString(), brewNamesNeedingTimestamps.toString(), nStoreId, beerIdsCurrentlyOnTheWebQueue); // WEB OPERATION to post each beer
                    Log.v(TAG, "nStoreId: " + nStoreId);
                    nListener.sendStatusToast("Sent " + addedCount + (addedCount>1?" untasted beers to ":" untasted beer to ") + storeName + " queue.", Toast.LENGTH_SHORT);
                    Log.v(TAG, "Sent " + addedCount + (addedCount>1?" untasted beers to ":" untasted beer to ") + storeName + " queue. Added Count " + addedCount);
                } else {
                    nListener.sendStatusToast("No active, untasted beers to queue.", Toast.LENGTH_SHORT);
                    Log.v(TAG, "No active, untasted beers to queue.");
                }
            } else {
                Log.v(TAG, "No BrewIds found " + nBrewIds);
            }

            nListener.onSuccess(nSavePin, nCardPin, nMou, nCardnumber, nStoreNumber);
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

    private void resetQueued(Map<String, String> currentQueuedBeerIds) {
        SQLiteDatabase db = null;
        Context context = KnurderApplication.getContext();
        if (context == null) return;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(context) ;
            db = ufoDatabaseAdapter.openDb(context);                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            // first clear the queued
            db.execSQL("UPDATE UFO SET CURRENTLY_QUEUED = 'F'");
            Set<String> brewIdArray = currentQueuedBeerIds.keySet();
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

    private boolean brewIdIsActive(String appFlaggedBrewId) {
        SQLiteDatabase db = null;
        Cursor cursor = null;
        Context context = KnurderApplication.getContext();
        if (context == null) return false;
        String active = "?";
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(context) ;
            db = ufoDatabaseAdapter.openDb(context);                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("UFO", new String[] {"ACTIVE"}, "BREW_ID = ?", new String[] {appFlaggedBrewId}, null, null, null);
            if (cursor.moveToFirst()) {
                active = cursor.getString(0);
            }
            cursor.close();
            Log.v(TAG, "active: " + active);
        } catch (SQLiteException e) {
            Log.v(TAG, "Failed to access database " + e.getClass().getName() + " " + e.getMessage());
        } finally {
            try {cursor.close();} catch (Throwable t) {}
            try {db.close();} catch (Throwable t) {}
        }
        return "T".equals(active);
    }



    private boolean getSiteAccess(String site) {
        try {
            if (InetAddress.getByName(site).equals("")) {
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception on pre-execute getSiteAccess. " + e.getMessage());
            return false;
        }
        return true;
    }

    // This method was duplicated in MemberDataInteractorImpl.pullUserStatsPage() //TODO: Consolidate duplicated code (some day)
    private String validateCardCredentials() {
        String enterYourPinPage = null;
        String postLoginPage = null;
        String localMouString = "0".equals(nMou)?"no":"yes";
        String localStoreIdString = StoreNameHelper.getTwoCharacterStoreIdFromStoreNumber(nStoreNumber);
        String localCardNumString = "%" + localStoreIdString + nCardnumber + "=?";
        // MOU PROCESS DIFFERENCE
        if ("yes".equals(localMouString)) localCardNumString = "%mou" + nCardnumber + "=?";

        String cardData = "%" + localStoreIdString + nCardnumber;
        String localVarCharStoreIdString = StoreNameHelper.getVarCharacterStoreIdFromStoreNumber(nStoreNumber);
        try {
            String vistorMemberPage = LoadDataHelper.getPageContent("https://tapthatapp.beerknurd.com/kiosk.php?sid=" + nStoreNumber, null, nHttpclient, nCookieStore);                       //<<<<<<<<<GET INITIAL LOGIN FORM PAGE<<<<<<<<<<<<<<<<<<<
            Log.v(TAG, "doInBackground() Ran vistorMember page"); // Run Order #14
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
            firstPostParms.add(new BasicNameValuePair("cardNum",localCardNumString));

            // Submitting the form with card number on it from the visitorMember page:
            nLastResponse = LoadDataHelper.getInstance().sendPost("https://tapthatapp.beerknurd.com/kiosk.php", firstPostParms, nHttpclient, "cardnumber", nCookieStore, 45);

            //String enterYourPinPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            //Log.v(TAG, "Should be 'Enter Your Pin' page\n" + enterYourPinPage);

            Log.v(TAG, "doInBackground() (probably) got session ID.  Logging in with " + nCardnumber + " " + nCardPin + " " + nMou + " " + nStoreNumber); // Run Order #17

            List <NameValuePair> postParams = new ArrayList<NameValuePair>();
            postParams.add(new BasicNameValuePair("homestore","%" + localStoreIdString));
            postParams.add(new BasicNameValuePair("cardNumber", nCardnumber));
            postParams.add(new BasicNameValuePair("mouOpt", localMouString));
            postParams.add(new BasicNameValuePair("cardNum", localCardNumString));
            postParams.add(new BasicNameValuePair("signinPinNumber", nCardPin));
            postParams.add(new BasicNameValuePair("submitPin", "Beam+Me+Up!"));

            nLastResponse = LoadDataHelper.getInstance().sendPost("https://tapthatapp.beerknurd.com/member-signin.php", postParams, nHttpclient, "cardnumber", nCookieStore, 45);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<
            enterYourPinPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            Log.v(TAG, "doInBackground() Sent form with fields filled-in.  Should be at enter your pin page now."); // Run Order #22
            //Log.v(TAG, "response page from the post:\n" + enterYourPinPage);

            String cardDataValueMouOrNot = "yes".equals(localMouString)?localCardNumString:"";
            List <NameValuePair> finalPostParams = new ArrayList<NameValuePair>();
            finalPostParams.add(new BasicNameValuePair("signinPin", nCardPin));
            finalPostParams.add(new BasicNameValuePair("cardData", cardDataValueMouOrNot));
            finalPostParams.add(new BasicNameValuePair("submitPin", "Beam+Me+Up!"));

            Log.v(TAG, "doInBackground() posting with signinPin [" + nCardPin + "], cardData [" + cardDataValueMouOrNot + "], submitPin [Beam+Me+Up!]");

            nLastResponse = LoadDataHelper.getInstance().sendPost("https://tapthatapp.beerknurd.com/signin.php", finalPostParams, nHttpclient, "cardnumber", nCookieStore, 45);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<
            postLoginPage = LoadDataHelper.getResultBuffer(nLastResponse); //<<<<<<<<<Pull from response to get the page contents
            Log.v(TAG, "CardnumberCredentialInteractor.doInBackground() Sent form with PIN filled-in.  Should be at logged in page now."); // Run Order #22
            if (postLoginPage!=null) {
                Log.v(TAG, "response page from the post had " + postLoginPage.length() + " characters.");
                //Log.v(TAG, "[" + postLoginPage + "]");
            } else {
                Log.e(TAG, "the postLoginPage was null");
            }

        } catch (Exception e) {
            Log.e(TAG, "Could not get credentialOkPage. " + e.getMessage());
        }
        return postLoginPage;
    }

    private int addBeersToQueue(String brewIdsNeedingTimestamps, String brewNamesNeedingTimestamps, String storeId, Map<String, String> beerIdsCurrentlyOnTheWebQueue) {
        int sentCount = 0;
        setQueuedTimestamp(brewIdsNeedingTimestamps); //local database activity only.

        String storeName = StoreNameHelper.getCurrentStoreNameShort();
        String memberId = LoadDataHelper.getCookie(nCookieStore, "member_id");
        try {
            String[] brewIdArray = brewIdsNeedingTimestamps.split(",");
            String[] brewNameArray = brewNamesNeedingTimestamps.split(",");
            for (int i = 0; i < brewIdArray.length; i++) {
                String brewId = brewIdArray[i];
                String brewName = brewNameArray[i];
                Log.v(TAG, "BEER BEING ADDED " + brewId + ":" + brewName);
                String beerName = beerIdsCurrentlyOnTheWebQueue.get(brewId);
                if (beerName == null) beerName = brewName;
                beerName.replaceAll(" ", "+");
                List <NameValuePair> postParams = new ArrayList<NameValuePair>();
                postParams.add(new BasicNameValuePair("chitCode",brewId + "-" + storeId + "-" + memberId));
                postParams.add(new BasicNameValuePair("chitBrewId", brewId));
                postParams.add(new BasicNameValuePair("chitBrewName", beerName));
                postParams.add(new BasicNameValuePair("chitStoreName", storeName.replaceAll(" ", "+")));

                nLastResponse = LoadDataHelper.getInstance().sendPost("https://tapthatapp.beerknurd.com/addToQueue.php", postParams, nHttpclient, "addToQueue", nCookieStore, 45);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<

                Thread.sleep(500); // Try not to overdrive the saucer web site
                sentCount++;
            }
        } catch (Throwable t) {
            Log.v(TAG, "ERROR: Failed to post brew ID " + t.getMessage());
        }
        return sentCount;
    }
}

