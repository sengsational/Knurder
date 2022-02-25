package com.sengsational.knurder;

import android.os.AsyncTask;
//import android.support.annotation.NonNull;
//import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.cookie.Cookie;
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
    private String nCardnumber = null;
    private String nCardPin = null;
    private String nMou = null;
    private String nBrewIds = null;
    private String nSavePin = null;
    private HttpResponse nLastResponse = null;
    private String nErrorMessage = null;

    public void validateCardnumberCredentialsFromWeb(final String cardNumber, String cardPin, String mou, String savePin, final String storeNumber, final String brewIds, final WebResultListener listener) {
            nStoreNumber = storeNumber;
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
                nErrorMessage = "Card number not authenciated.";
                return false;
            }

            if (nBrewIds != null && nBrewIds.contains(",")) {
                String currentQueuePage = LoadDataHelper.getPageContent("https://www.beerknurd.com/tapthatapp/memberQueue.php", null, nHttpclient, nCookieStore);
                String currentQueuedBeerIds = LoadDataHelper.getCurrentQueuedBeerNamesFromHtml(currentQueuePage);
                Log.v(TAG, "currentQueuedBeerIds [" + currentQueuedBeerIds + "]");
                String[] brewIdArray = nBrewIds.split(",");
                for (String brewId : brewIdArray) {
                    if (currentQueuedBeerIds.contains(brewId)) continue;
                    try {
                        String queueUrl = "https://www.beerknurd.com/tapthatapp/queue-up-brew.php?action=member&brewID=" + brewId + "&storeID=" + nStoreNumber;
                        String queueitupResultPage = LoadDataHelper.getPageContent(queueUrl, null, nHttpclient, nCookieStore);
                        Thread.sleep(500); // Try not to overdrive the saucer web site
                    } catch (Throwable t) {
                        Log.v(TAG, "ERROR: Failed to post brew ID " + t.getMessage());
                    }
                }
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
            nLastResponse = LoadDataHelper.getInstance().sendPost("https://www.beerknurd.com/tapthatapp/kiosk.php", firstPostParms, nHttpclient, "cardnumber", nCookieStore);

            //String enterYourPinPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            //Log.v(TAG, "Should be 'Enter Your Pin' page\n" + enterYourPinPage);

            Log.v("sengsational", "doInBackground() (probably) got session ID.  Logging in with " + nCardnumber + " " + nCardPin + " " + nMou + " " + nStoreNumber); // Run Order #17

            List <NameValuePair> postParams = new ArrayList<NameValuePair>();
            postParams.add(new BasicNameValuePair("cardData","%" + localStoreIdString + nCardnumber));
            postParams.add(new BasicNameValuePair("signinPinNumber", nCardPin));
            postParams.add(new BasicNameValuePair("submitPin", "Beam+Me+Up!"));

            // ? = %3F
            // % = %25
            nLastResponse = LoadDataHelper.getInstance().sendPost("https://www.beerknurd.com/tapthatapp/signin.php?cd=%25" + localStoreIdString + nCardnumber + "=%3F", postParams, nHttpclient, "cardnumber", nCookieStore);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<
            credentialOkPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            Log.v("sengsational", "doInBackground() Sent form with fields filled-in.  Should be logged-in now."); // Run Order #22
            // Log.v(TAG, "response page from the post:\n" + credentialOkPage);

        } catch (Exception e) {
            Log.e("sengsational", "Could not get credentialOkPage. " + e.getMessage());
        }
        return credentialOkPage;
    }

}

