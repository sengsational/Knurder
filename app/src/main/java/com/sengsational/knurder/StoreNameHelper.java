package com.sengsational.knurder;

import android.content.ContentValues;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.sengsational.knurder.KnurderApplication.getContext;
import static com.sengsational.knurder.TopLevelActivity.STORE_NUMBER_LIST;

/**
 * Created by Owner on 5/23/2016.
 */
public class StoreNameHelper {
    public static final String TAG = "StoreNameHelper";

    public static final String[] statesNvpArray = new String[] {"- Select a Saucer -=XX;",  "Addison Flying Saucer=TX;Texas;",  "Charlotte Flying Saucer=NC;North Carolina;",  "Columbia Flying Saucer=SC;South Carolina;",  "Cordova Flying Saucer=TN;Tennessee;",  "Cypress Waters Flying Saucer=TX;Texas;",   "DFW Airport Flying Saucer=TX;Texas;",   "Fort Worth Flying Saucer=TX;Texas;",  "Houston Flying Saucer=TX;Texas;",  "Kansas City Flying Saucer=MO;Missouri;KS;Kansas;","Little Rock Flying Saucer=AR;Arkansas;", "Memphis Flying Saucer=TN;Tennessee;",  "Nashville Flying Saucer=TN;Tennessee;",  "Raleigh Flying Saucer=NC;North Carolina;",  "San Antonio Flying Saucer=TX;Texas;",  "Sugar Land Flying Saucer=TX;Texas;",  "The Lake Flying Saucer=TX;Texas;"};

    private static StoreNameHelper storeNameHelper;
    private static TreeMap<String, String> stateHelper;
    private static final String[][] storeNumberToTwoChar = new String[][] {
            {"13887","ad"},
            {"13888","ch"},
            {"13878","co"},
            {"13883","cv"},
            {"18686214","cw"},
            {"18262641","df"},
            {"13891","fw"},
            {"13880","ab"},
            {"13892","kc"},
            {"13885","lr"},
            {"13881","mt"},
            {"13886","nv"},
            {"13877","aa"},
            {"13882","sa"},
            {"13879","sl"},
            {"13884","rh"}
    };
    private static final String[][] storeNumberToVarChar = new String[][] {
            {"13887","add"},
            {"13888","char"},
            {"13878","col"},
            {"13883","cor"},
            {"18686214","cypress"},
            {"18262641","dfw"},
            {"13891","fw"},
            {"13880","hou"},
            {"13892","kc"},
            {"13885","lr"},
            {"13881","mem"},
            {"13886","nash"},
            {"13877","ral"},
            {"13882","sa"},
            {"13879","sl"},
            {"13884","lake"}
    };

    private static final String[][] storeNumberToStoreUrl = new String[][] {
            {"13888","https://www.beerknurd.com/charlotte-flying-saucer/"},
            {"13883","https://www.beerknurd.com/cordova-flying-saucer/"},
            {"18686214","https://www.beerknurd.com/cypress-waters-flying-saucer/"},
            {"18262641","https://www.beerknurd.com/dfw-airport-flying-saucer/"},
            {"13891","https://www.beerknurd.com/fort-worth-flying-saucer/"},
            {"13880","https://www.beerknurd.com/houston-flying-saucer/"},
            {"13885","https://www.beerknurd.com/little-rock-flying-saucer/"},
            {"13881","https://www.beerknurd.com/memphis-flying-saucer/"},
            {"13877","https://www.beerknurd.com/raleigh-flying-saucer/"},
            {"13882","https://www.beerknurd.com/san-antonio-flying-saucer/"},
            {"13879","https://www.beerknurd.com/sugar-land-flying-saucer/"},
            {"13884","https://www.beerknurd.com/the-lake-flying-saucer/"}
    };

    public static StoreNameHelper getInstance() {
        if (storeNameHelper == null){
            storeNameHelper = new StoreNameHelper();
        }
        return storeNameHelper;
    }

    private StoreNameHelper() {
    }

    public static void confirmLocationsLoadedInDatabase() {
        UfoDatabaseAdapter.DatabaseHelper ufoHelper = null;
        try {
            //ufoHelper = new UfoDatabaseAdapter.DatabaseHelper(getContext());
            ufoHelper = UfoDatabaseAdapter.DatabaseHelper.getInstance(getContext());
            SQLiteDatabase db = ufoHelper.getWritableDatabase();                                    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            int count = ufoHelper.getCount(db, "LOCATIONS");
            Log.v(TAG, "there were " + count + " records in the LOCATIONS database");

            final String[] storesNvpArray = new String[] {"_none=- Select a Saucer -",
                    "13888=Charlotte Flying Saucer",
                    "13883=Cordova Flying Saucer",
                    "18686214=Cypress Waters Flying Saucer",
                    "18262641=DFW Airport Flying Saucer",
                    "13891=Fort Worth Flying Saucer",
                    "13880=Houston Flying Saucer",
                    "13885=Little Rock Flying Saucer",
                    "13881=Memphis Flying Saucer",
                    "13877=Raleigh Flying Saucer",
                    "13882=San Antonio Flying Saucer",
                    "13879=Sugar Land Flying Saucer",
                    "13884=The Lake Flying Saucer"};


            if (count < storesNvpArray.length) {  // Load database with HARD CODED if it's not got enough records
                Log.v(TAG, "store database getting loaded.");
                db.execSQL("delete from locations");
                ContentValues values = null;
                for (int i = 0; i < storesNvpArray.length; i++){
                    String[] numberNameNvp = storesNvpArray[i].split("=");
                    String[] nameStateNvp = statesNvpArray[i].split("=");
                    values = new ContentValues();
                    values.put("STORE_ID", numberNameNvp[0]);
                    Log.v(TAG,"SNH.confirm " + numberNameNvp[1]);
                    values.put("STORE_NAME", numberNameNvp[1]);
                    values.put("LOCAL_STATES", nameStateNvp[1]);
                    //Log.v(TAG, " values to string " + values.toString());
                    db.insert("LOCATIONS", null, values);
                }
            } else {
                Log.v(TAG, "store database was good.");
                // Remove old stores
                try {
                    db.execSQL("delete from locations where STORE_NAME = 'Austin Flying Saucer';");
                    db.execSQL("delete from locations where STORE_NAME = 'St Louis Flying Saucer';");
                    db.execSQL("delete from locations where STORE_NAME = 'Dallas Meddlesome Moth';");
                } catch(Throwable t) {
                    Log.e(TAG, "Could not delete old store: " + t.getMessage());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem in StoreNameHelper.confirmLocationsLoadedInDatabase() " + e.getMessage());
        } finally {
            try {ufoHelper.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }

    }

    public static String getTwoCharacterStoreIdFromStoreNumber(String storeNumber) {
        String storeId = "xx";
        for (String[] pair: storeNumberToTwoChar) {
            if (pair[0].equals(storeNumber)) {
                storeId = pair[1];
                break;
            }
        }
        return storeId;
    }

    public static String getVarCharacterStoreIdFromStoreNumber(String storeNumber) {
        String storeId = "xxx";
        for (String[] pair: storeNumberToVarChar) {
            if (pair[0].equals(storeNumber)) {
                storeId = pair[1];
                break;
            }
        }
        return storeId;
    }

    public static String getStoreIdsForState(String stateCode) {
        if (stateHelper == null) stateHelper = getStoreIdsByStateMapFromDatabase(false);
        String storeIds = stateHelper.get(stateCode);
        if (storeIds != null) return storeIds;
        else return "";
    }

    public static TreeMap<String, String> getStoreIdsByStateMapFromDatabase(boolean closeDatabase) {
        stateHelper = new TreeMap<>();
        Cursor cursor = null;
        UfoDatabaseAdapter.DatabaseHelper ufoHelper = null;
        try {
            ufoHelper = UfoDatabaseAdapter.DatabaseHelper.getInstance(getContext());
            SQLiteDatabase db = ufoHelper.getReadableDatabase();                                  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("LOCATIONS", new String[] {"STORE_ID, LOCAL_STATES"}, null, null, null, null, null);
            while (cursor.moveToNext()){
                String storeId = cursor.getString(0);
                String localStates = cursor.getString(1);
                if (localStates != null) {
                    try {
                        String firstState = localStates.split(";")[0];
                        if (firstState != null) {
                            String storeIds = stateHelper.get(firstState);
                            if (storeIds == null) {
                                stateHelper.put(firstState, storeId);
                            } else {
                                stateHelper.put(firstState, storeIds + "," + storeId);
                            }
                        }
                    } catch (Throwable t) {
                        Log.e(TAG, "Error parsing [" + localStates + "]");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem in StoreNameHelper.getStoreIdsByStateMapFromDatabase() " + e.getMessage());
        } finally {
            try {if (closeDatabase) ufoHelper.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return stateHelper;
    }

    public static String getCurrentStoreNumber(String defaultStoreNumber) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return prefs.getString(STORE_NUMBER_LIST,defaultStoreNumber);
    }

    public static String getCurrentStoreName() {
        return getInstance().getStoreNameFromNumber(getCurrentStoreNumber("0"),null);
    }

    public static String getCurrentStoreNameShort() {
        String nameWithFlyingSaucer = getCurrentStoreName();
        int fsLoc = nameWithFlyingSaucer.indexOf(" Flying Saucer");
        if (fsLoc > -1) {
            return nameWithFlyingSaucer.substring(0, fsLoc);
        }
        return nameWithFlyingSaucer;
    }

    public void reloadFromPageAndSaveToDatabase(String[] names, String[] numbers) {
        UfoDatabaseAdapter.DatabaseHelper ufoHelper = null;
        boolean openRequired = false;
        try {
            ufoHelper = UfoDatabaseAdapter.DatabaseHelper.getInstance(getContext());
            SQLiteDatabase db = ufoHelper.getWritableDatabase();                                    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            int count = ufoHelper.getCount(db, "LOCATIONS");
            ContentValues values = null;
            if (names != null && names.length > count) {
                db.execSQL("delete from locations");
                for (int i = 0; i < names.length; i++){
                    if (names[i] == null || numbers[i] == null) continue;
                    values = new ContentValues();
                    values.put("STORE_ID", numbers[i].trim());
                    Log.v(TAG, "SNH.reload " + names[i].trim());
                    values.put("STORE_NAME", names[i].trim());
                    values.put("LOCAL_STATES", lookupStatesForStoreName(names[i].trim()));
                    db.insert("LOCATIONS", null, values);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem in StoreNameHelper.reloadFromPageAndSaveToDatabase() " + e.getMessage());
        } finally {
            try {
                if (openRequired) ufoHelper.close();
            } catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
    }

    public String lookupStatesForStoreName(String storeName) {
        String statesCsv = "";
        for (int i = 0; i < statesNvpArray.length; i++){
            String[] nameStateNvp = statesNvpArray[i].split("=");
            if(storeName.equals(nameStateNvp[0])){
                statesCsv = nameStateNvp[1];
                break;
            }
        }
        return statesCsv;
    }

    public Set<String> getStoreTreeSet() {
        Set<String> storeSet = new TreeSet<String>();
        Cursor cursor = null;
        UfoDatabaseAdapter.DatabaseHelper ufoHelper = null;
        try {
            //ufoHelper = new UfoDatabaseAdapter.DatabaseHelper(getContext());
            ufoHelper = UfoDatabaseAdapter.DatabaseHelper.getInstance(getContext());
            SQLiteDatabase db = ufoHelper.getReadableDatabase();                                  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("LOCATIONS", new String[] {"STORE_ID, STORE_NAME"}, null, null, null, null, null);
            while (cursor.moveToNext()){
                storeSet.add(cursor.getString(0) + "=" + cursor.getString(1));
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem in StoreNameHelper.getStoreTreeSet() " + e.getMessage());
        } finally {
            try {ufoHelper.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return storeSet;
    }

    List<String> getSortedStoreNames() {
        List<String> sortedStoreNames = getStoreNamesFromDatabase();
        Collections.sort(sortedStoreNames);
        return sortedStoreNames;
    }

    private List<String> getStoreNamesFromDatabase() {
        ArrayList<String> storeNames = new ArrayList<String>();
        Cursor cursor = null;
        UfoDatabaseAdapter.DatabaseHelper ufoHelper = null;
        try {
            //ufoHelper = new UfoDatabaseAdapter.DatabaseHelper(getContext());
            ufoHelper = UfoDatabaseAdapter.DatabaseHelper.getInstance(getContext());
            SQLiteDatabase db = ufoHelper.getReadableDatabase();                                  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("LOCATIONS", new String[] {"STORE_NAME"}, null, null, null, null, null);
            while (cursor.moveToNext()){
                storeNames.add(cursor.getString(0));
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem in StoreNameHelper.getStoreNamesFromDatabase() " + e.getMessage());
        } finally {
            try {ufoHelper.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return storeNames;
    }

    public static String getStoreNumberFromName(String storeName){
        String storeNumber = "00000";
        Cursor cursor = null;
        UfoDatabaseAdapter.DatabaseHelper ufoHelper = null;
        try {
            //ufoHelper = new UfoDatabaseAdapter.DatabaseHelper(getContext());
            ufoHelper = UfoDatabaseAdapter.DatabaseHelper.getInstance(getContext());
            SQLiteDatabase db = ufoHelper.getReadableDatabase();                                  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("LOCATIONS", new String[] {"STORE_ID"}, "STORE_NAME = ?", new String[] {storeName}, null, null, null);
            if (cursor.moveToFirst()){
                storeNumber = cursor.getString(0);
            } else {
                Log.e(TAG, "Problem in StoreNameHelper.getStoreNumberFromName().  Did not find  " + storeName  + " in the database.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem in StoreNameHelper.getStoreNumberFromName() " + e.getMessage());
        } finally {
            try {ufoHelper.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return storeNumber;
    }

    public String getStoreNameFromNumber(String storeNumber, SQLiteDatabase db) {
        String storeName = "";
        Cursor cursor = null;
        UfoDatabaseAdapter.DatabaseHelper ufoHelper = null;
        boolean dbLocal = (db == null);
        try {
            if (dbLocal) {
                ufoHelper = UfoDatabaseAdapter.DatabaseHelper.getInstance(getContext());
                db = ufoHelper.getReadableDatabase();                                  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            }
            cursor = db.query("LOCATIONS", new String[] {"STORE_NAME"}, "STORE_ID = ?", new String[] {storeNumber}, null, null, null);
            if (cursor.moveToFirst()){
                storeName = cursor.getString(0);
            } else {
                Log.e(TAG, "Problem in StoreNameHelper.getStoreNameFromNumber().  Did not find  " + storeNumber  + " in the database.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Problem in StoreNameHelper.getStoreNameFromNumber() " + e.getMessage());
        } finally {
            try {
                if (dbLocal) ufoHelper.close();
            } catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        }
        return storeName;
    }
    public String getStoreUrlFromNumber(String nStoreNumber, String defaultValue) {
        String returnUrl = defaultValue;
        for (int i = 0; i < storeNumberToStoreUrl.length; i++) {
            String storeNumber = storeNumberToStoreUrl[i][0];
            if (storeNumber.equals(nStoreNumber)){
                returnUrl = storeNumberToStoreUrl[i][1];
                break;
            }
        }
        return returnUrl;
    }

    public HashMap<String,String> getStoreMap() {
        Set<String> storeTreeSet = getStoreTreeSet();
        Log.v(TAG, "Getting store set. Size: " + storeTreeSet.size());
        HashMap<String, String> aStoreMap = new HashMap<String, String>();
        Iterator<String> dssIterator = storeTreeSet.iterator();
        while (dssIterator.hasNext()) {
            String nvp = dssIterator.next();
            String[] nvpArray = nvp.split("=");
            aStoreMap.put(nvpArray[1], nvpArray[0]);
        }
        return aStoreMap;
    }

    public String getDatabaseTableCreationCommand() {
        return "CREATE TABLE LOCATIONS (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "STORE_ID TEXT, " +
                "STORE_NAME TEXT, " +
                "LOCAL_STATES TEXT);";
    }

}
