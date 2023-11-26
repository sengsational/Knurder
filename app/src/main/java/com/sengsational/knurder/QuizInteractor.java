package com.sengsational.knurder;

import android.os.AsyncTask;
import android.util.Log;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

/**
 * Created by Owner on 4/26/2018.
 */

public class QuizInteractor extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = QuizInteractor.class.getSimpleName();

    private CloseableHttpClient nHttpclient = null;
    private BasicCookieStore nCookieStore = null;
    private WebResultListener nListener;
    private HttpResponse nLastResponse = null;
    private String nErrorMessage = null;
    private long mQuizDateMs = 0L;
    private boolean mAlreadyPassed = false;

    public QuizInteractor(DataView dataView) {
        nListener = new WebResultListenerImpl(dataView);
    }

    public void getQuizPageFromWeb(DataView dataView) {
        if (dataView != null) dataView.showProgress(true);
        this.execute((Void) null);
    }

    @Override
    protected void onPreExecute() {
        Log.v("sengsational", "onPreExecute()..."); //Run order #01
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
        try {
            nHttpclient.close();
            nHttpclient = null;
        } catch (Exception e) {}

        if (success) {
            Log.v("sengsational", "onPostExecute success: " + success);
            nListener.onQuizPageSuccess(mAlreadyPassed, mQuizDateMs);
            nListener.onFinished();
        } else {
            Log.v("sengsational", "onPostExecute fail. " + nErrorMessage);
            // DRS 20180823 - Comment this because often the quiz page is not there, and not supposed to be there.
            // nListener.onError(nErrorMessage);
        }
    }



    @Override
    protected Boolean doInBackground(Void... params) {

        if (nErrorMessage != null) return false;

        if(!getSiteAccess("saucerknurd.com")){
            nListener.onError("interent problem");
            nErrorMessage = "Could not reach the web site.";
            return false;
        }

        try {
            do { // This is here to prevent tasted and store lists from updating at the same time.  One will queue behind the other
                Log.v(TAG, "doInBackground is accessing web update lock.");
            } while (!KnurderApplication.getWebUpdateLock(TAG)); // if lock unavailable, this will delay 1/2 second up to 10 seconds, then release.

            // Post user specifics to pull-up the quiz page
            String quizPage = pullQuizPage();
            if(quizPage == null){
                // DRS 20180823 - Comment this because often the quiz page is not there, and not supposed to be there.
                //nListener.onError("quiz page not found");
                Log.v("sengsational", "quiz page not found this time.");
                nErrorMessage = "Did not get to the quiz page.";
                return true; // return true so that mProgressStacker will unwind
            }
            //Log.v(TAG, quizPage);
            mQuizDateMs = getQuizDateMsFromContent(quizPage);
            mAlreadyPassed = getDidPassQuizFromContent(quizPage);
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

    private boolean getDidPassQuizFromContent(String quizPage) {
        return (quizPage!= null && quizPage.indexOf("you already passed")> 0);
    }

    private long getQuizDateMsFromContent(String quizPage) {
        long returnDateMs = 0L;
        if (quizPage != null) {
            try {
                SimpleDateFormat quizDateFormat = new SimpleDateFormat("yyyyMMdd");
                int idLoc = quizPage.indexOf("<form id=\"quiz\" name=\"quiz"); //<form id="quiz" name="quiz20200212" method="POST" action="grade.php" onsubmit="return false">
                if (idLoc > 0) {
                    String dateString = quizPage.substring(idLoc + 26, idLoc + 26 + 8);
                    Log.v(TAG, "got dateString [" + dateString + "]");
                    returnDateMs = quizDateFormat.parse(dateString).getTime();
                }
            } catch (Throwable t) {
                Log.e(TAG, "Failed to get quiz date from page content. " + t.getMessage());
            }
        }
        return returnDateMs;
    }

    private String pullQuizPage() {
        List<NameValuePair> paramList = new ArrayList<NameValuePair>();

        paramList.add(new BasicNameValuePair("email", TopLevelActivity.prefs.getString(TopLevelActivity.EMAIL_ADDRESS, "")));
        paramList.add(new BasicNameValuePair("UFO", TopLevelActivity.prefs.getString(TopLevelActivity.AUTHENTICATION_NAME, "")));
        paramList.add(new BasicNameValuePair("FirstName", TopLevelActivity.prefs.getString(TopLevelActivity.FIRST_NAME, "")));
        paramList.add(new BasicNameValuePair("LastName", TopLevelActivity.prefs.getString(TopLevelActivity.LAST_NAME, "")));
        paramList.add(new BasicNameValuePair("homestore", TopLevelActivity.prefs.getString(TopLevelActivity.STORE_NAME_LIST, "")));

        String quizPage = null;
        try {
            HttpResponse quizResponse = LoadDataHelper.getInstance().sendQuizPost("https://www.saucerknurd.com/glassnite/quiz/", paramList, nHttpclient);
            quizPage = LoadDataHelper.getResultBuffer(quizResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
        } catch (Exception e) {
            Log.e("sengsational", "Could not get quizPage. " + e.getMessage());
        }
        return quizPage;
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
