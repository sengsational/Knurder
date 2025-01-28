package com.sengsational.knurder;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.sengsational.ocrreader.OcrScanHelper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import cz.msebera.android.httpclient.HttpResponse;
import cz.msebera.android.httpclient.NameValuePair;
import cz.msebera.android.httpclient.cookie.Cookie;
import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;
import cz.msebera.android.httpclient.message.BasicNameValuePair;

import static com.sengsational.knurder.KnurderApplication.getContext;
import static com.sengsational.knurder.MenusPageInteractorImpl.getUntappdItemsFromData;
import static com.sengsational.knurder.MenusPageInteractorImpl.pullUntappedDataPage;
import static com.sengsational.knurder.TopLevelActivity.DEFAULT_STORE_NAME;
import static com.sengsational.knurder.TopLevelActivity.STORE_NAME_LIST;
import static com.sengsational.knurder.TopLevelActivity.USER_NAME;
import static com.sengsational.knurder.TopLevelActivity.USER_NUMBER;
import static com.sengsational.knurder.TopLevelActivity.prefs;

/**
 * Created by Dale Seng on 1/20/2025
 * This began as copy of TastedListInteractor. The constructor was altered to use card credentials, and the login changed to card login, rather than old site login.
 */
public class MemberDataInteractorImpl  extends AsyncTask<Void, Void, Boolean> implements MemberDataInteractor {
    private static final String TAG = MemberDataInteractor.class.getSimpleName();

    private CloseableHttpClient nHttpclient = null;
    private BasicCookieStore nCookieStore = null;
    private WebResultListener nListener;
    private String nStoreNumber = null;
    private String nStoreNumberOfList = null;
    private String nCardnumber = null;
    private String nCardPin = null;
    private String nMou = null;
    private String nSavePin = null;
    private HttpResponse nLastResponse = null;
    private String nErrorMessage = null;
    private boolean nStoreListOnly = false;
    private Context nContext = null;

    public void getMemberDataFromWeb(final String cardNumber, String cardPin, String mou, String savePin, final String storeNumber, final String storeNumberOfList, final String brewIds, final String brewNames, final WebResultListener listener, final DataView dataView, final Context context) {
        KnurderApplication.LogCallers(TAG,Thread.currentThread().getStackTrace(), "cardNumber " + cardNumber);
        nStoreNumber = storeNumber;
        nCardnumber = cardNumber;
        nCardPin = cardPin;
        nMou = mou;
        nSavePin = savePin;
        nListener = listener;
        nContext = context;
        nStoreListOnly = false;

        if (dataView != null) {
            Log.v(TAG, "dataView:" + dataView.getClass().getName() + " nStoreNumber " + nStoreNumber + " nCardnumber " + nCardnumber + " nCardPin " + nCardPin);
            dataView.showProgress(true);
        } else {
            Log.v(TAG, "MemberDataPresenterImpl.getMemberData() dataView was null");
        }
        this.execute((Void) null);
    }

    public void getMemberDataFromWeb(final String storeNumber, final WebResultListener listener, final DataView dataView, final Context context) {
        KnurderApplication.LogCallers(TAG,Thread.currentThread().getStackTrace(), "storeNumber " + storeNumber);
        nStoreNumber = storeNumber;
        nListener = listener;
        nContext = context;
        nStoreListOnly = true;

        if (dataView != null) {
            Log.v(TAG, "dataView:" + dataView.getClass().getName());
            dataView.showProgress(true);
        } else {
            Log.v(TAG, "MemberDataPresenterImpl.getMemberData() dataView was null");
        }
        this.execute((Void) null);
    }


    @Override
    protected void onPreExecute() {
        Log.v(TAG, "onPreExecute()..."); //Run order #01
        if (!nStoreListOnly && (TextUtils.isEmpty(nStoreNumber) || TextUtils.isEmpty(nCardnumber) || TextUtils.isEmpty(nCardPin))) {
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
            Log.v(TAG, "onPostExecute success: " + success + " with nStoreListOnly " + nStoreListOnly);
            if (!nStoreListOnly) {
                nListener.setToUserPresentation();
            } else {
                Log.v(TAG, "Not setting user presentation.");
            }
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
            nListener.onError("internet problem");
            nErrorMessage = "Could not reach the web site.";
            return false;
        }

        try {
            do { // This is here to prevent tasted and store lists from updating at the same time.  One will queue behind the other
                Log.v(TAG, "doInBackground is accessing web update lock.");
            } while (!KnurderApplication.getWebUpdateLock(TAG)); // if lock unavailable, this will delay 1/2 second up to 10 seconds, then release.

            String userNumberFromPage = null;
            String userNumberFromPrefs = prefs.getString(USER_NUMBER, "");

            if (!nStoreListOnly) {
                // >>>Log in<<<< and get the all the user's data (statistics, tasted) and also the current saucer beer list (not untappd beer list).
                // BLOCKS and returns null if it timed-out
                // ------     ----------------------------
                String statsWebPage = pullUserStatsPage(); // <=========== THIS IS THE LOGIN!!
                if(statsWebPage == null){
                    nListener.onError("login data not found");
                    nErrorMessage = "Did not get logged into the UFO site.";
                    Log.v(TAG, nErrorMessage);
                    return false;
                } else if (!statsWebPage.contains("member-greeting-header")) {
                    nListener.onError("member page not accessed");
                    nErrorMessage = "Did not get logged into the UFO site.";
                    Log.v(TAG, nErrorMessage);
                    return false;
                } else {
                    Log.v(TAG, "MILESTONE: Successfully logged in");
                    userNumberFromPage = getCookieValueFromLastResponse("member_id");
                }
                nListener.sendStatusToast("Refreshing available beers and your tasted beer list.", Toast.LENGTH_SHORT);
            } else {
                nListener.sendStatusToast("Refreshing available beers.", Toast.LENGTH_SHORT);
            }

            /** ************************/
            /** Get the brews in stock */
            /** ************************/
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(nContext);
            String listStoreNumber = nStoreNumber;
            listStoreNumber = prefs.getString(TopLevelActivity.STORE_NUMBER_LIST, nStoreNumber);
            Log.v(TAG, "Using store number " + listStoreNumber + " for list pull.");
            String beersWebPage = pullBeersWebPage(listStoreNumber); //bk-store-json.php?sid=
            boolean activePullFailed = false;
            if (beersWebPage == null) {
                nListener.onError("active beers data not found");
                nErrorMessage  = "Problem getting beers in stock.";
                Log.v(TAG, nErrorMessage);
                activePullFailed = true;
            }

            if (!activePullFailed) {
                if(!flagAllAsActiveStateNotDetermined()) {
                    nListener.onError("database error");
                    nErrorMessage = "Internal database error..";
                    Log.v(TAG, nErrorMessage);
                    activePullFailed = true;
                }
            }

            if (!activePullFailed) {
                // Insert the active beers into the database (or just update to active if record exists as a tasted record)
                if (!loadActiveFromSite(beersWebPage)) {
                    nListener.onError("active beer update error");
                    nErrorMessage = "Internal database error...";
                    Log.v(TAG, nErrorMessage);
                    activePullFailed = true;
                }
            }

            if (!activePullFailed) {
                // Remove inactive highlighted (keep inactive tasted, of course)
                if (!manageEntriesNotJustRefreshed(listStoreNumber)){
                    nListener.onError("clearing inactive error");
                    nErrorMessage = "Internal database error....";
                    Log.v(TAG, nErrorMessage);
                    activePullFailed = true;
                }
            }
            Log.v(TAG, "nStoreListOnly = " + nStoreListOnly);
            if (!nStoreListOnly) {
                /** ************************/
                /** Get the tasted page    */
                /** ************************/
                String tastedWebPage = null;
                if (userNumberFromPage != null) {
                    tastedWebPage = pullTastedPage(userNumberFromPage); // this method with String[] parameter will access index 3 to get the userNumber
                } else {
                    if (!"".equals(userNumberFromPrefs)) {
                        tastedWebPage = pullTastedPage(userNumberFromPrefs); // this method with String parameter will use userNumber directly
                    }
                }
                if(tastedWebPage == null) {
                    nListener.onError("member data not found");
                    nErrorMessage  = "Problem getting your member data.";
                    Log.v(TAG, nErrorMessage);
                    return false;
                }
                nListener.sendStatusToast("Got your member data.", Toast.LENGTH_SHORT);

                // Put a 'D' into all records with Tasted='T' (was: RemoveInactiveTasted (will be reloaded shortly) and set active tasted to untasted (will be updated))
                if(!flagAllAsTastedStateNotDetermined()) {
                    nListener.onError("database error");
                    nErrorMessage = "Internal database error.";
                    return false;
                }

                // Get user information from the top of the tasted JSON
                Map<String,String> userData = loadUserDataFromSite(tastedWebPage);
                //userData [username=sengsational]
                //userData [tasted_brew_count_this_round=129]
                nListener.saveValidCardCredentials(nCardnumber, nCardPin, nSavePin, nMou, nStoreNumber, userData.get("username"), userData.get("tasted_brew_count_this_round"));

                // Insert the tasted beers into the database (or just update to tasted if record exists as an active record)
                boolean logoff = false;
                if (!loadTastedFromSite(tastedWebPage, logoff)){
                    nListener.onError("data load error");
                    nErrorMessage = "Unable to put member data into the database.";
                    return false;
                }
            }

            // If the 'D' is still in there, then it means it wasn't loaded in the previous step, and needs to be deleted.
            if(!managedEntriesNotJustRefreshed()) {
                nListener.onError("database error");
                nErrorMessage = "Internal database error.";
                return false;
            }

            /** ************************/
            /** Get the UNTAPPD data   */
            /** ************************/

            /* START: THIS IS TO PULL MENU DATA FROM UNTAPPD ************** */
            boolean menuDataAdded = false;
            try {
                //SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(nContext);
                String untappdDataUrlString = UntappdHelper.getUntappdUrlForStoreNumber(listStoreNumber, nContext);
                if(!"".equals(untappdDataUrlString)) {
                    boolean skipTheRest = false;
                    // START NOTE: This code is duplicated in the refresh beer list "StoreListInteractorImple.doInBackground()
                    // START NOTE: This code is duplicated in the refresh beer list "MenusPageInteractorImple.doInBackground()
                    // Pull the page containing the list of beers
                    nListener.sendStatusToast("Getting untapped list...", Toast.LENGTH_SHORT);
                    String untappdDataPage = pullUntappedDataPage(untappdDataUrlString, nHttpclient, nCookieStore);
                    if (null == untappdDataPage) {
                        throw new Exception("not able to pull the untappdDataPage from " + untappdDataUrlString);
                        //nListener.onError("not able to pull the untappdDataPage from " + untappdDataUrlString);
                        //nErrorMessage = "Could not get the menu information from the location provided.";
                        //return false;
                    }
                    // Parse the beers out of the data
                    //nListener.sendStatusToast("Loading untapped list...", Toast.LENGTH_SHORT);

                    // --------- TAPS ---------------
                    ArrayList<UntappdItem> untappdItems = getUntappdItemsFromData(untappdDataPage, "taps");
                    if (untappdItems.size() == 0) {
                        throw new Exception("zero items pulled from untappdDataPage of size " + untappdDataPage.length());
                        //nListener.onError("zero items pulled from untappdDataPage of size " + untappdDataPage.length());
                        //nErrorMessage = "Did not understand the menu information found.";
                        //return false;
                    }

                    // Match the untappd list with the saucer tap list
                    nListener.sendStatusToast("Matching untappd list with saucer list...", Toast.LENGTH_SHORT);
                    OcrScanHelper.getInstance().matchUntappdItems(untappdItems, "taps", nContext);
                    // Save the results
                    int[] results = OcrScanHelper.getInstance().getResults("taps", nContext);

                    boolean matchTapsOnly = prefs.getBoolean("match_taps_only_switch", true);
                    if (!matchTapsOnly) {
                        //////////////NEW CODE - BOTTLES////////////////////////////////
                        //------------BOTTLES--------------
                        ArrayList<UntappdItem> untappdItemsBottles = getUntappdItemsFromData(untappdDataPage, "bottles");
                        Log.v(TAG, "DEBUG 1");
                        if (untappdItemsBottles.size() == 0) {
                            nListener.onError("zero bottle items pulled from untappdDataPage of size " + untappdDataPage.length());
                            nErrorMessage = "Did not understand the menu information found. Proceed with existing taps and ignore bottles.";
                        }

                        // Match the untappd list with the saucer bottles list
                        OcrScanHelper.getInstance().matchUntappdItems(untappdItemsBottles, "bottles", nContext);
                        Log.v(TAG, "DEBUG 2");
                        int[] bottleResults = OcrScanHelper.getInstance().getResults("bottles", nContext);
                        Log.v(TAG, "DEBUG bottleResults size: " + bottleResults.length);
                        /////////////END NEW CODE///////////////////////////////////////
                        // END NOTE: This code is duplicated in the refresh beer list "StoreListInteractorImpl.doInBackground()
                        // END NOTE: This code is duplicated in the refresh beer list "MenusPageInteractorImpl.doInBackground()
                    }

                    // Pull store summary page for new arrivals
                    nListener.sendStatusToast("Getting 'Just Landed'", Toast.LENGTH_SHORT);
                    String storePage = pullStorePage(listStoreNumber);
                    if (storePage == null) {
                        Log.e(TAG,"Store Page not received. ") ;
                    } else {
                        if (!loadNewArrivalsFromSite(storePage)){
                            Log.e(TAG,"New Arrivals not loaded.") ;
                        }
                    }

                    menuDataAdded = true;
                } else {
                    Log.v(TAG, "Not pulling from untappd.");
                }
            } catch (Throwable t) {
                Log.v(TAG, "Unable to pull menu data from untappd. " + t.getMessage());
            }
            /************* END: THIS IS TO PULL MENU DATA FROM UNTAPPD ***************/


            if (!nStoreListOnly && !uploadUserReviews()) {
                nListener.onError("Unable to upload user reviews.");
                nErrorMessage = "Error posting review(s) to Saucer web site.";
                // allow tasted list to be successful, even though reviews were not, so don't return from here.
            }
            if (!nStoreListOnly) {
                nListener.onMemberDataSuccess();
            } else {
                nListener.onStoreListSuccess(true, menuDataAdded); //nResetPresentation "true" is a guess
            }
        } catch (Exception e) {
            Log.e(TAG, LoadDataHelper.getInstance().getStackTraceString(e));
            nErrorMessage = "Exception " + e.getMessage();
            return false;
        } finally {
            // Before, we tried to LOGOFF, even though we might not be logged on.  I'm not convinced the new site really logs off anyway.
            // try {LoadDataHelper.getPageContent("https://tapthatapp.beerknurd.com/kiosk.php", null, nHttpclient, nCookieStore); } catch (Throwable t) {}
            KnurderApplication.releaseWebUpdateLock(TAG);
        }
        Log.v(TAG, "Returning " + true + " from doInBackground");
        return true;
    }

    private String pullBeersWebPage(String storeNumber) {
        String beersListPage = null;
        try {
            beersListPage = LoadDataHelper.getPageContent("https://fsbs.beerknurd.com/bk-store-json.php?sid=" + storeNumber, null, nHttpclient, nCookieStore);        //<<<<<<<<<<<<<<<<PULL STORE'S AVAILABLE LIST
        } catch (Exception e) {
            Log.e(TAG, "Could not get beersListPage. " + e.getMessage());
        }
        return beersListPage;
    }

    private String pullStorePage(String nStoreNumber) {
        String storePage = null;
        try {
            String storeUrl = StoreNameHelper.getInstance().getStoreUrlFromNumber(nStoreNumber, null);
            Log.v(TAG, "Pulling " + storeUrl);
            storePage = LoadDataHelper.getPageContent(storeUrl, null, nHttpclient, nCookieStore);        //<<<<<<<<<<<<<<<<PULL STORE'S AVAILABLE LIST
        } catch (Exception e) {
            Log.e(TAG, "Could not get storePage. " + e.getMessage());
        }
        return storePage;
    }


    private boolean flagAllAsActiveStateNotDetermined() {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            db.execSQL("update UFO set ACTIVE='D'");
        } catch (Exception e) {
            Log.e(TAG, "Could not get prepare database for new record loading. " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private boolean loadActiveFromSite(String beersWebPage) {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE

            Log.v(TAG, "Database was open in SLI.loadActiveFromSite " + db.isOpen());
            int currentCount = getCount(db, "UFO");
            Log.v(TAG, "Starting out we had " + currentCount + " records.");

            ////////////////////////////////////////////////////////
            // page contains the active list for the selected store
            ////////////////////////////////////////////////////////
            int updateCount = 0;

            // remove header material
            int brewInStockLoc = beersWebPage.indexOf("brewInStock\":[{");
            if (brewInStockLoc > -1) {
                beersWebPage = beersWebPage.substring(brewInStockLoc + 15);
            }

            String[] items = beersWebPage.split("\\},\\{");
            if (items.length > 1) {
                Log.v(TAG, "The active list (including bottled) had " + items.length + " items.");
                for (String string : items) {
                    SaucerItem modelItem = new SaucerItem();
                    modelItem.setStoreNameAndNumber(nStoreNumber, db);
                    // Load this item from the page into SaucerItem
                    //modelItem.clear();
                    modelItem.loadTta(string); // "name", "store_id",  "brew_id",  "brewer",  "city", "country", "container", "style","description","stars","reviews"
                    // Will not contain "review", "created", "active", "highlighted" or "tasted"
                    // We know this is an active beer, so set to true
                    modelItem.setActive(true);
                    // We will be updating new arrival shortly, but get false into the db to start with
                    modelItem.setNewArrival(false);

                    String brewId = modelItem.getBrew_id(); // The brew EXTRA_ID we're working with in this loop

                    // This could be in the database already, so we need to find out if it is.
                    Cursor cursor = db.query("UFO", new String[] {"BREW_ID, HIGHLIGHTED, TASTED"}, "BREW_ID = ?", new String[] {brewId}, null, null, null);
                    if (cursor.moveToFirst()) { // true if record found

                        // preserve highlighted flag
                        String highlighted = cursor.getString(1);
                        if ("T".equals(highlighted)) {
                            modelItem.setHighlighted("T");
                        } else if ("X".equals(highlighted)){
                            modelItem.setHighlighted("X");
                        } else {
                            modelItem.setHighlighted("F");
                        }

                        // preserve tasted flag
                        String tasted = cursor.getString(2);
                        if ("T".equals(tasted)) {
                            modelItem.setTasted("T");
                        } else {
                            modelItem.setTasted("F");
                        }

                        db.update("UFO", modelItem.getContentValues(), "BREW_ID = ?", new String[] {brewId});
                        //StringBuilder sb = new StringBuilder();
                        //sb.append(modelItem.getContentValues().get("ACTIVE"));
                        //sb.append(modelItem.getContentValues().get("TASTED"));
                        //sb.append(modelItem.getContentValues().get("HIGHLIGHTED"));
                        //sb.append(modelItem.getContentValues().get("NEW_ARRIVAL"));
                        //Log.v(TAG, "SLI Update database      " + sb + "  " + modelItem.getContentValues().toString());
                        updateCount++;
                        //Log.v(TAG, "updating BREW_ID: " + brewId);
                    } else { // need to insert
                        // Since this is an insert, we have to set the flags to default values
                        modelItem.setTasted(false);
                        modelItem.setHighlighted(false);
                        modelItem.setNewArrival(false);
                        //Log.v("insert", "BREW_ID " + brewId + " was NOT found, inserting a new row.");

                        // This is for logging purposes
                        //StringBuilder sb = new StringBuilder();
                        //sb.append(modelItem.getContentValues().get("ACTIVE"));
                        //sb.append(modelItem.getContentValues().get("TASTED"));
                        //sb.append(modelItem.getContentValues().get("HIGHLIGHTED"));
                        //sb.append(modelItem.getContentValues().get("NEW_ARRIVAL"));

                        db.insert("UFO", null, modelItem.getContentValues());
                        //Log.v(TAG, "SLI Insert into database " + sb + "  " + modelItem.getContentValues().toString());
                        //Log.v(TAG, "Inserted modelItem: " + modelItem.getName());

                        /*
                        if ("draught".equals(modelItem.getContainer())){
                            Log.v("styletest", "style " + modelItem.getStyle());
                            db.insertWithOnConflict ("STYLES", null, modelItem.getStyleCv(),SQLiteDatabase.CONFLICT_IGNORE);
                        }
                        */
                        currentCount++;
                        //Log.v(TAG, "inserting: " + brewId);
                    }
                    if (cursor != null) cursor.close();
                }
                Log.v(TAG, "After loading active, and updating " + updateCount + ", we had " + getCount(db, "UFO") + " records. That should equal " + currentCount + ".");
                //Log.v(TAG, "We had " + getCount(db, "STYLES") + " style records.");
            } else {
                Log.v(TAG, "Found nothing in the beersWeb page.");
                nListener.onError("found nothing on the beersweb page");
            }
        } catch (Exception e) {
            Log.e(TAG, LoadDataHelper.getInstance().getStackTraceString(e));
            nListener.onError("exception in storelistinteractor " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private boolean manageEntriesNotJustRefreshed(String nStoreNumber) {
        // All currently active beers from this store now have ACTIVE set to 'T'
        // This method seeks to disposition those entries still remaining in the ACTIVE = 'D' state.

        // Active T == entry has just validated as active
        // ACTIVE D == not determined
        // ACTIVE F == not active

        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            Log.v(TAG, "SLI.manageEntriesNotJustRefreshed before count " + getCount(db, "UFO"));

            // First, Prevent inactive tasted from getting deleted (we never delete tasted in the store list process).
            // Remove the 'D' from tasted beers that are not active.  This will prevent them from getting deleted, below.
            db.execSQL("update UFO set ACTIVE = 'F' where TASTED = 'T' and ACTIVE='D'");

            // First "part B": as an additional first step, manage the transition to using Untappd.
            // Users may have tasted with glass size and price, but no untappd beer number.
            // Because glass size and price has been declared as indicator for going to untappd,
            // we must remove glass size and price if untappd number not populated.
            db.execSQL("update UFO set GLASS_SIZE = null where TASTED = 'T' and ACTIVE = 'F' and UNTAPPD_BEER is null");
            db.execSQL("update UFO set GLASS_PRICE = null where TASTED = 'T' and ACTIVE = 'F' and UNTAPPD_BEER is null");

            // Second, Prevent inactive flagged from getting deleted.  If the user has something flagged, let them remove it instead of the process of updating.
            // Remove the 'D' from flagged beers that are not active.  This will prevent them from getting deleted, below.
            db.execSQL("update UFO set ACTIVE = 'F' where (HIGHLIGHTED = 'T' or HIGHLIGHTED='X') and ACTIVE='D'");

            // Third, Prevent entries with local reviews from getting deleted.
            // Remove the 'D' from local review beers that are not active.  This will prevent them from getting deleted, below.
            db.execSQL("update UFO set ACTIVE = 'F' where (REVIEW_FLAG = 'L') and ACTIVE='D'");

            // Forth, Remove records that 1) didn't get updated as active from this store
            //                        and 2) are not in the set of tasted
            //                        and 3) are not in the set of highlighted
            db.execSQL("delete from UFO where ACTIVE='D'");

            Log.v(TAG, "SLI.manageEntriesNotJustRefreshed after  count " + getCount(db, "UFO"));
        } catch (Exception e) {
            Log.e(TAG, "Could not get prepare database for new record loading. " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private boolean loadNewArrivalsFromSite(String storePage) {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            String[] newArrivalNames = LoadDataHelper.getNewArrivalsFromPage(storePage, getContext()); // SIDE EFFECT HERE!!! GETS UBER EATS LINK AND SAVES IT IN SHARED PREFS!!!!
            if (newArrivalNames != null && newArrivalNames.length > 0) {
                // Clear previous new arrivals
                db.execSQL("update UFO set NEW_ARRIVAL = 'F'");

                // Update new arrival flag using exact beer name
                for (String newArrivalName : newArrivalNames){
                    if(newArrivalName != null) {
                        newArrivalName = newArrivalName.replace("'"," ").replace("\""," ").replace("<"," ").replace(">"," ");
                        db.execSQL("update UFO set NEW_ARRIVAL = 'T' where NAME = '" + newArrivalName + "'");
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Could not get or update new arrivals. " + t.getMessage());
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
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
            beersTastedPage = LoadDataHelper.getPageContent(("https://fsbs.beerknurd.com/bk-member-json.php?uid=" + userNumber), nLastResponse, nHttpclient, nCookieStore, timeoutSeconds);  //<<<<<<<<<<<<<<<<<PULL TASTED<<<<<<<<<<<<<<<<<<<
            prefs.edit().putString(USER_NUMBER, userNumber).apply();
        } catch (Exception e) {
            Log.e(TAG, "Could not get tastedListPage. " + e.getMessage());
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
            Log.e(TAG, "Could not get prepare database for new tasted record loading. " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private Map<String, String> loadUserDataFromSite(String tastedWebPage) {
        Map<String,String> userData = new HashMap<String, String>();

        /*  user data [member_id=417180]
            user data [card_num=21611]
            user data [member_since=May 30, 2010]
            user data [email_address=jm@sengsational.com]
            user data [total_tasted=929]
            user data [saucer_memberships=1]
            user data [name=Dale]
            user data [homestore=Charlotte Flying Saucer]
            user data [roh=4]
            user data [username=sengsational]
            user data [tasted_brew_count_this_round=129]
            */

        // select header material
        int currentRoundLoc = tastedWebPage.indexOf("}},{\"tasted_brew_current_round\":[{");
        if (currentRoundLoc > -1) {
            tastedWebPage = tastedWebPage.substring(0, currentRoundLoc);
        }
        int memberLoc = tastedWebPage.indexOf("member\":{");
        if (memberLoc > -1) {
            tastedWebPage = tastedWebPage.substring(memberLoc + 9);
        }
        Log.v(TAG, "[" + tastedWebPage + "]");

        // Below code is duplicated from SaucerItem
        String[] nvpa = tastedWebPage.split("\",\""); //Use "quoted comma" to split, otherwise single commas would split!!
        for (String nvpString : nvpa) {
            String[] nvpItem = nvpString.split("\":\"");
            if (nvpItem.length < 2) continue;
            String identifier = nvpItem[0].replaceAll("\"", "");
            String content = nvpItem[1].replace("\\\"u", "u"); // "backslash quote u" will become "backslash u", so can't have that.
            content = content.replaceAll("\"", ""); // Remove quotes from within the content.  I don't know why we're doing this any more.
            userData.put(identifier, content);
        }
        return userData;
    }


    private boolean loadTastedFromSite(String tastedWebPage, boolean logoff) {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            int currentCount = getCount(db, "UFO");
            Log.v(TAG, "Starting out we had " + currentCount + " records.");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(KnurderApplication.getContext());
            boolean showOnlyCurrentTasted = prefs.getBoolean("current_beers_switch", true);

            ////////////////////////////////////////////////////////
            // page contains the active list for the selected store
            ////////////////////////////////////////////////////////
            int updateCount = 0;

            // remove header material
            int currentRoundLoc = tastedWebPage.indexOf("tasted_brew_current_round\":[{");
            if (currentRoundLoc > -1) {
                tastedWebPage = tastedWebPage.substring(currentRoundLoc + 29);
            }

            String[] items = tastedWebPage.split("\\},\\{");
            if (items.length > 1) {
                Log.v(TAG, "The tasted list (including bottled) had " + items.length + " items.");
                for (String string : items) {
                    SaucerItem modelItem = new SaucerItem();
                    modelItem.setStoreNameAndNumber(nStoreNumber, db);
                    // Load this item from the page into SaucerItem
                    //modelItemclear();
                    modelItem.loadTta(string);
                    if (showOnlyCurrentTasted && !modelItem.isOnCurrentPlate()) continue; // <<<<<<<< Do not load tasted from earlier plates

                    // We know this is a tasted beer, so set to true
                    modelItem.setTasted(true);

                    String brewId = modelItem.getBrew_id(); // The brew EXTRA_ID we're working with in this loop
                    if (brewId == null) {
                        Log.v(TAG, "loadTastedFromSite encountered a null brew_id [" + string + "]");
                        continue;
                    }
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
                Log.v(TAG, "After loading active, and updating " + updateCount + ", we had " + getCount(db, "UFO") + " records. That should equal " + currentCount + ".");
                //Log.v(TAG, "We had " + getCount(db, "STYLES") + " style records.");
            } else {
                Log.v(TAG, "Found nothing in the beersWeb page.");
                nListener.onError("no data found");
            }

            if (logoff) LoadDataHelper.getPageContent("http://www.beerknurd.com/user/logout", null, nHttpclient, nCookieStore); // Log off
        } catch (Exception e) {
            Log.e(TAG, LoadDataHelper.getInstance().getStackTraceString(e));
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
                String userName = TAG;
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
            Log.e(TAG, LoadDataHelper.getInstance().getStackTraceString(e));
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
            Log.v(TAG, "Before removing old tasted records we had " + currentCount + " records.");

            // First, Prevent deletion of active records.  Never delete actives.
            db.execSQL("update UFO set TASTED='F' where TASTED='D' and ACTIVE='T'");

            // Next, Prevent deletion of highlighted records.  Let the user do that.
            db.execSQL("update UFO set TASTED='F' where TASTED='D' and (HIGHLIGHTED='T' or HIGHLIGHTED='X')");

            db.execSQL("delete from  UFO where TASTED = 'D'");
            int newCount = getCount(db, "UFO");
            Log.v(TAG, "After  removing old tasted records we had " + newCount + " records.");
        } catch (Exception e) {
            Log.e(TAG, "Could not get prepare database for new tasted record loading. " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private String pullUserStatsPage() {
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
            //List <Cookie> cookies = nCookieStore.getCookies();

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

            nLastResponse = LoadDataHelper.getInstance().sendPost("https://tapthatapp.beerknurd.com/signin.php", finalPostParams, nHttpclient, "cardnumber", nCookieStore, 45);                         //<<<<<<<<<<<<SUBMIT LOGIN FORM PAGE<<<<<<<<<<<<<<<<
            postLoginPage = LoadDataHelper.getResultBuffer(nLastResponse).toString(); //<<<<<<<<<Pull from response to get the page contents
            Log.v(TAG, "MemberDataInteractor.doInBackground() Sent form with PIN filled-in.  Should be at logged in page now."); // Run Order #22
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

    public String getCookieValueFromLastResponse(String key) {
        List<Cookie> cookiesList = nCookieStore.getCookies();
        if (cookiesList.isEmpty()) {
            Log.v(TAG, "No cookies! !"); // Run Order #13
        } else {
            for (int i = 0; i < cookiesList.size(); i++) {
                Cookie cookie = cookiesList.get(i);
                if (key.equals(cookie.getName())) {
                    String cookieValue = cookie.getValue();
                    Log.v(TAG, "Found " + key + "=" + cookieValue);
                    return cookieValue;
                }
            }
            Log.v(TAG, "Did NOT find cookie for '" + key +"'" );
        }
        return "";
    }

    private boolean postReview(String reviewId, int stars, String reviewText, String timestamp, String saucerName, String beerName, String userName) {
        String afterPostPage = null;
        boolean successful = false;
        Log.e(TAG, "POSTING REVIEWS IS NOT ENABLED.");
        if (!successful) return successful; // DISABLED <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
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
            Log.e(TAG, "Exception on pre-execute getSiteAccess. " + e.getMessage());
            return false;
        }
        return true;
    }
    private String[] getCardUserDataNvp(String page) { //  userName, tastedCount, cardValue, loadedUser
        Document doc = Jsoup.parse(page);
        Elements memberProgress = doc.getElementsByClass("member_progress");
        String[] returnString = new String[4];
        if (memberProgress.size() < 1) {
            Log.v(TAG,"not logged-in");
            return null;
        } else {
            try {
                returnString[0] = "userName=" + memberProgress.get(0).text();
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
                Log.v(TAG, "Unable to parse good login page." + t.getMessage());
                return null;
            }
            return returnString;
        }
    }

    private String[] getUserDataNvp(String page) { //  userName, tastedCount, cardValue, loadedUser

        Document doc = Jsoup.parse(page);
        Elements userDetails = doc.getElementsByClass("user_details");
        String[] returnString = new String[4];
        if (userDetails.size() < 1) {
            Log.v(TAG,"not logged-in");
            return null;
        } else {
            try {
                Element userDetailsDiv = userDetails.get(0);
                Elements userNames = userDetailsDiv.getElementsByClass("username");
                returnString[0] = "userName=" + userNames.get(0).text();
                Log.v(TAG, "Found userName " + returnString[0]);

                Elements memberTasted = userDetailsDiv.getElementsByClass("member_tasted");
                String memberTastedText = memberTasted.text();
                returnString[1] = "tastedCount=" + memberTastedText.split(" ")[0];
                Log.v(TAG, "Found tastedCount " + returnString[1]);

                int tastedUrlLineLoc = page.indexOf("user_json_url"); //$user_json_url = "https://fsbs.beerknurd.com/bk-member-json.php?uid=417180";
                if (tastedUrlLineLoc > -1) {
                    int tastedUrlStartQuoteLoc = page.indexOf("\"https", tastedUrlLineLoc);
                    int tastedUrlEndQuotLoc = page.indexOf("\";", tastedUrlLineLoc);
                    String tastedUrl = page.substring(tastedUrlStartQuoteLoc+1, tastedUrlEndQuotLoc);
                    Log.v(TAG, "Found tasted URL [" + tastedUrl + "]");
                    int equalsLoc = tastedUrl.indexOf("=");
                    returnString[3] = "loadedUser=" + tastedUrl.substring(equalsLoc+1);
                    Log.v(TAG, "Found loadedUser " + returnString[3]);
                }

                // This only required for the Captain Keith's Quiz
                SharedPreferences.Editor editor = TopLevelActivity.prefs.edit();
                try {
                    Elements profileInfo = doc.getElementsByClass("profile-info");
                    Element element = profileInfo.first();
                    String emailAddress = element.getElementById("m_stat_email").text();
                    editor.putString(TopLevelActivity.EMAIL_ADDRESS, emailAddress);
                    Log.v(TAG, "Found email address [" + emailAddress + "]");
                    String firstName = returnString[0].substring(9).split(" ")[0];
                    Log.v(TAG, "Found firstName [" + firstName + "]");
                    editor.putString(TopLevelActivity.FIRST_NAME, firstName);

                } catch (Throwable t) {
                    Log.v(TAG, "Unable to parse stuff for the quiz. " + t.getMessage());
                } finally {
                    editor.apply();
                }

            } catch (Throwable t) {
                Log.v(TAG, "Unable to parse good login page." + t.getMessage());
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

interface MemberDataInteractor {
    void getMemberDataFromWeb(final String cardNumber, String cardPin, String mou, String savePin, final String storeNumber, final String storeNumberOfList, final String brewIds, final String brewNames, final WebResultListener listener, final DataView dataView, final Context context) ;
}
