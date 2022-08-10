package com.sengsational.knurder;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.sengsational.ocrreader.OcrScanHelper;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import cz.msebera.android.httpclient.impl.client.BasicCookieStore;
import cz.msebera.android.httpclient.impl.client.CloseableHttpClient;
import cz.msebera.android.httpclient.impl.client.HttpClientBuilder;
import cz.msebera.android.httpclient.impl.client.LaxRedirectStrategy;

import static com.sengsational.knurder.KnurderApplication.getContext;
import static com.sengsational.knurder.MenusPageInteractorImpl.getUntappdItemsFromData;
import static com.sengsational.knurder.MenusPageInteractorImpl.pullUntappedDataPage;

/**
 * Created by Dale Seng on 5/30/2016.
 */
public class StoreListInteractorImpl  extends AsyncTask<Void, Void, Boolean> implements StoreListInteractor {
    private static final String TAG = StoreListInteractorImpl.class.getSimpleName();

    private CloseableHttpClient nHttpclient = null;
    private BasicCookieStore nCookieStore = null;
    private WebResultListener nListener;
    private String nStoreNumber = null;
    private boolean nResetPresentation;
    private String nErrorMessage = null;
    private boolean mWebUpdateLock;

    @Override public void getStoreListFromWeb(final String storeNumber, final WebResultListener listener, boolean resetPresentation) {
            nStoreNumber = storeNumber;
            nListener = listener;
            nResetPresentation = resetPresentation;
            this.execute((Void) null);
    }

    @Override
    protected void onPreExecute() {
        Log.v("sengsational", "onPreExecute()..."); //Run order #01
        if (TextUtils.isEmpty(nStoreNumber)) {
            nListener.onError("store number error");
            nErrorMessage = "We need a Saucer location.";
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

        if(!getSiteAccess("beerknurd.com")){
            nListener.onError("could not get to the web site");
            nErrorMessage = "Could not reach the web site.";
            return false;
        }

        try {
            do { // This is here to prevent tasted and store lists from updating at the same time.  One will queue behind the other
                Log.v(TAG, "doInBackground is accessing web update lock.");
            } while (!KnurderApplication.getWebUpdateLock(TAG)); // if lock unavailable, this will delay 1/2 second up to 10 seconds, then release.

            nListener.sendStatusToast("Getting list from saucer site...", Toast.LENGTH_SHORT);
            // Get the currently active beers from the particular store
            String beersWebPage = pullBeersWebPage(nStoreNumber);
            if(beersWebPage == null){
                nListener.onError("did not get beer list page");
                nErrorMessage = "Did not get the list of beers from the UFO site.";
                return false;
            }
            //nListener.sendStatusToast("Got the list.  Loading local table...", Toast.LENGTH_SHORT);
            // Remove everything except the tasted and highlighted records
            if(!flagAllAsActiveStateNotDetermined()) {
                nListener.onError("database error");
                nErrorMessage = "Internal database error..";
                return false;
            }

            StoreNameHelper.getStoreIdsByStateMapFromDatabase(true);

            // Insert the active beers into the database (or just update to active if record exists as a tasted record)
            if (!loadActiveFromSite(beersWebPage)){
                nListener.onError("active beer update error");
                nErrorMessage = "Internal database error...";
                return false;
            }

            // Remove inactive highlighted (keep inactive tasted, of course)
            if (!manageEntriesNotJustRefreshed(nStoreNumber)){
                nListener.onError("clearing inactive error");
                nErrorMessage = "Internal database error....";
                return false;
            }

            nListener.sendStatusToast("Table loaded.  Checking just landed...", Toast.LENGTH_SHORT);
            String storePage = pullStorePage(nStoreNumber);
            if (storePage == null) {
                Log.e("sengsational","Store Page not received.") ;
            } else {
                if (!loadNewArrivalsFromSite(storePage)){
                    Log.e("sengsational","New Arrivals not loaded.") ;
                }
            }

            /************* START: THIS IS TO PULL MENU DATA FROM UNTAPPD ***************/
            boolean menuDataAdded = false;
            try {
                Context context = KnurderApplication.getContext();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
                String untappdDataUrlString = UntappdHelper.getUntappdUrlForStoreNumber(nStoreNumber);
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
                    ArrayList<UntappdItem> untappdItems = getUntappdItemsFromData(untappdDataPage);
                    if (untappdItems.size() == 0) {
                        throw new Exception("zero items pulled from untappdDataPage of size " + untappdDataPage.length());
                        //nListener.onError("zero items pulled from untappdDataPage of size " + untappdDataPage.length());
                        //nErrorMessage = "Did not understand the menu information found.";
                        //return false;
                    }
                    // Match the untappd list with the saucer tap list
                    nListener.sendStatusToast("Matching untappd list with saucer list...", Toast.LENGTH_SHORT);
                    OcrScanHelper.getInstance().matchUntappdItems(untappdItems, context);
                    // Save the results
                    int[] results = OcrScanHelper.getInstance().getResults(context);
                    // END NOTE: This code is duplicated in the refresh beer list "StoreListInteractorImple.doInBackground()
                    // END NOTE: This code is duplicated in the refresh beer list "MenusPageInteractorImple.doInBackground()
                    menuDataAdded = true;
                } else {
                    Log.v(TAG, "Not pulling from untappd.");
                }
            } catch (Throwable t) {
                Log.v(TAG, "Unable to pull menu data from untappd. " + t.getMessage());
            }
            /************* END: THIS IS TO PULL MENU DATA FROM UNTAPPD ***************/

            nListener.saveValidStore(nStoreNumber);
            nListener.onStoreListSuccess(nResetPresentation, menuDataAdded);
            // >>>>>>>>>>Active items now loaded into the database<<<<<<<<<<<<<<<<<
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

    private boolean flagAllAsActiveStateNotDetermined() {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            db.execSQL("update UFO set ACTIVE='D'");
        } catch (Exception e) {
            Log.e("sengsational", "Could not get prepare database for new record loading. " + e.getMessage());
            return false;
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }

    private boolean xremoveAllExceptTastedAndHighlighted(SQLiteDatabase db) {
        try {
            Log.v("sengsational", "We have " + getCount(db, "UFO") + " before.");
            db.execSQL("delete from UFO where TASTED<>'T' and HIGHLIGHTED is null");
            db.execSQL("delete from UFO where TASTED<>'T' and HIGHLIGHTED='F'");
            db.execSQL("update UFO set ACTIVE = 'F'");
            Log.v("sengsational", "We have " + getCount(db, "UFO") + " tasted plus highlighted");
            // We now have only tasted/highlighted records in the database or empty database
        } catch (Exception e) {
            Log.e("sengsational", "Could not get prepare database for new record loading. " + e.getMessage());
            return false;
        }
        return true;
    }

    private boolean loadActiveFromSite(String beersWebPage) {
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE

            Log.v("sengsational", "Database was open in SLI.loadActiveFromSite " + db.isOpen());
            int currentCount = getCount(db, "UFO");
            Log.v("sengsational", "Starting out we had " + currentCount + " records.");

            ////////////////////////////////////////////////////////
            // page contains the active list for the selected store
            ////////////////////////////////////////////////////////
            int updateCount = 0;
            String[] items = beersWebPage.split("\\},\\{");
            if (items.length > 1) {
                Log.v("sengsational", "The active list (including bottled) had " + items.length + " items.");
                for (String string : items) {
                    SaucerItem modelItem = new SaucerItem();
                    modelItem.setStoreNameAndNumber(nStoreNumber, db);
                    // Load this item from the page into SaucerItem
                    //modelItem.clear();
                    modelItem.load(string); // "name", "store_id",  "brew_id",  "brewer",  "city", "country", "container", "style","description","stars","reviews"
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
                        //Log.v("sengsational", "updating BREW_ID: " + brewId);
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
                        //Log.v("sengsational", "inserting: " + brewId);
                    }
                    if (cursor != null) cursor.close();
                }
                Log.v("sengsational", "After loading active, and updating " + updateCount + ", we had " + getCount(db, "UFO") + " records. That should equal " + currentCount + ".");
                //Log.v("sengsational", "We had " + getCount(db, "STYLES") + " style records.");
            } else {
                Log.v("sengsational", "Found nothing in the beersWeb page.");
                nListener.onError("found nothing on the beersweb page");
            }
        } catch (Exception e) {
            Log.e("sengsational", LoadDataHelper.getInstance().getStackTraceString(e));
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
        SQLiteDatabase db = null;
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getContext());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            Log.v("sengsational", "SLI.manageEntriesNotJustRefreshed before count " + getCount(db, "UFO"));

            // First, Prevent inactive tasted from getting deleted (we never delete tasted in the store list process).
            // Remove the 'D' from tasted beers that are not active.  This will prevent them from getting deleted, below.
            db.execSQL("update UFO set ACTIVE = 'F' where TASTED = 'T' and ACTIVE='D'");

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

            Log.v("sengsational", "SLI.manageEntriesNotJustRefreshed after  count " + getCount(db, "UFO"));
        } catch (Exception e) {
            Log.e("sengsational", "Could not get prepare database for new record loading. " + e.getMessage());
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
            Log.e("sengsational", "Could not get or update new arrivals. " + t.getMessage());
        } finally {
            try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return true;
    }


    private String pullBeersWebPage(String storeNumber) {
        String beersListPage = null;
        try {
            beersListPage = LoadDataHelper.getPageContent("http://www.beerknurd.com/api/brew/list/" + storeNumber, null, nHttpclient, nCookieStore);        //<<<<<<<<<<<<<<<<PULL STORE'S AVAILABLE LIST
        } catch (Exception e) {
            Log.e("sengsational", "Could not get beersListPage. " + e.getMessage());
        }
        return beersListPage;
    }

    private String pullStorePage(String nStoreNumber) {
        String storePage = null;
        try {
            String storeName = StoreNameHelper.getInstance().getStoreNameFromNumber(nStoreNumber, null);
            storeName = storeName.replaceAll(" ","-").toLowerCase();
            if (storeName != null && storeName.startsWith("the-")) storeName = storeName.substring(4); // the-lake-flying-saucer is in valid...it's lake-flying-saucer
            Log.v("sengsational", "Pulling http://www.beerknurd.com/locations/" + storeName);
            storePage = LoadDataHelper.getPageContent("http://www.beerknurd.com/locations/" + storeName, null, nHttpclient, nCookieStore);        //<<<<<<<<<<<<<<<<PULL STORE'S AVAILABLE LIST
        } catch (Exception e) {
            Log.e("sengsational", "Could not get beersListPage. " + e.getMessage());
        }
        return storePage;
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

    private int getCount(SQLiteDatabase db, String dbTableName){
        Log.v("sengsational", "getCount() database was open? " + db.isOpen());
        Cursor cursor = db.query(dbTableName, new String[]{"COUNT(*) AS count"}, null, null, null, null, null);
        int count = -1;
        if (cursor.moveToFirst()){
            count = cursor.getInt(0);
        }
        cursor.close();
        return count;
    }

}

interface StoreListInteractor {
    void getStoreListFromWeb(String storeNumber, WebResultListener listener, boolean resetPresentation);
}

