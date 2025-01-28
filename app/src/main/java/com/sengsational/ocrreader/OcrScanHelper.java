package com.sengsational.ocrreader;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.sengsational.knurder.ConcurrentHashSet;
import com.sengsational.knurder.SaucerItem;
import com.sengsational.knurder.UfoDatabaseAdapter;
import com.sengsational.knurder.UntappdItem;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.sengsational.knurder.SaucerItem.BREWERY_CLEANUP;
import static com.sengsational.knurder.TopLevelActivity.STORE_NUMBER_LIST;
import static com.sengsational.knurder.TopLevelActivity.prefs;

public class OcrScanHelper {
    private static final String TAG = OcrScanHelper.class.getSimpleName();
    private static final SimpleDateFormat NDF = new SimpleDateFormat("yyyy MM dd");

    private static ConcurrentHashSet<String[]> mFoundResults;
    private static String mStoreNumber = "13888"; // better than nothing
    private static OcrScanHelper mOcrScanHelper;

    public static OcrScanHelper getInstance(){
        if (mOcrScanHelper == null) {
            mOcrScanHelper = new OcrScanHelper();
        }
        return mOcrScanHelper;
    }

    private OcrScanHelper() {
        Log.v(TAG, ">>>>>>>>>>>> OcrScanHelperCreated <<<<<<<<<<<<<<< (this should happen once per UI action)");
        mStoreNumber = prefs.getString(STORE_NUMBER_LIST,"13888");
    }

    public int[] getResults(String tapsOrBottles, Context context) {
        int populatedActiveItemsCount = updateFoundItems(tapsOrBottles, context);
        Log.v(TAG, "mPouplatedActiveTapsCount " + populatedActiveItemsCount);
        Log.v(TAG, "mFoundResults.size() " + mFoundResults.size());
        return new int[] {populatedActiveItemsCount, mFoundResults.size(), getAllItems(tapsOrBottles, context).size()};
    }

    public void matchUntappdItems(ArrayList<UntappdItem> items, String tapsOrBottles, Context context) {
        if (!mStoreNumber.equals(prefs.getString(STORE_NUMBER_LIST,"13888"))) {
            mStoreNumber = prefs.getString(STORE_NUMBER_LIST,"13888");
        }
        /* This is to log the data so that we can perform offline validation
        boolean logTheSaucerAndUntappdItems = false;
        if (logTheSaucerAndUntappdItems) {
            StringBuffer csvBuf = new StringBuffer();
            for (UntappdItem item: items) {
                csvBuf.append("#\"").append("UNTAPPD").append("\",").append(item.getMatchingCsv());
                //                                                          ********************* < returns an item with fields not populated
                Log.v(TAG, csvBuf.toString());
                csvBuf = new StringBuffer();
            }
            List<String[]> allTapNames = getAllTapsForMatching(context);
            for (int i = 0; i < allTapNames.size(); i++){
                String[] values = allTapNames.get(i);
                csvBuf.append("#\"").append("SAUCER").append("\",\"").append(values[0]).append("\",\"").append(values[1]).append("\",\"").append(values[2]).append("\",\"").append(mStoreNumber).append("\"");
                Log.v(TAG, csvBuf.toString());
                csvBuf = new StringBuffer();
            }
        }
        */
        MatchGroup matchGroup = null;
        if (tapsOrBottles.contains("taps")) {
            matchGroup = new MatchGroup().load(getAllItemsForMatching("taps", context), mStoreNumber).load(items);
        } else {
            matchGroup = new MatchGroup().load(getAllItemsForMatching("bottles", context), mStoreNumber).load(items);
        }
        long startTime = new Date().getTime();
        mFoundResults = matchGroup.match(); // <=========LONG RUNNING METHOD
        Log.v(TAG, "match() took " + Math.round((new Date().getTime() - startTime)/1000) + " seconds for " + tapsOrBottles);

        /** THIS IS FOR CURIOSITY ABOUT UNMATCHED ITEMS */
        ArrayList<MatchItem>leftovers = matchGroup.getLeftoverSaucer();
        for (MatchItem item: leftovers) {
            Log.v(TAG, "leftoverSaucer \"" + item.getNonStyleTextMatch() + "\", " + item);
        }
        leftovers = matchGroup.getLeftoverUntappd();
        for (MatchItem item: leftovers) {
            Log.v(TAG, "lefotoverUntappd \"" + item.getNonStyleTextMatch() + "\", " + item);
        }

    }


    // "searchName" should contain a word starting at breweryLocInOcr and of length breweryWordSize.  This method replaces that word/phrase with the contents of "alternate"
    private String replaceByLocation(String searchName, String alternate, int breweryLocInOcr, int breweryWordSize) {
        if (breweryLocInOcr < 0 || breweryWordSize < 1) return searchName;
        StringBuffer returnStringValue = new StringBuffer(searchName);
        returnStringValue.replace(breweryLocInOcr, breweryLocInOcr + breweryWordSize, alternate);
        return returnStringValue.toString();
    }
    /////////////////////////////////////////////
    /* Uses mFoundResults to add tap menu data */
    /////////////////////////////////////////////
    private int updateFoundItems(String tapsOrBottles, Context context) {
        UfoDatabaseAdapter repository = new UfoDatabaseAdapter(context);
        String[] pullFields = new String[]{SaucerItem.NAME, SaucerItem.BREWER, SaucerItem.ACTIVE, SaucerItem.CONTAINER, SaucerItem.STORE_ID, SaucerItem.BREW_ID, SaucerItem.ABV, SaucerItem.DESCRIPTION};
        String selectionFields = "ACTIVE=? AND CONTAINER=? AND STYLE<>? and STYLE<>? and STORE_ID=? and NAME=?";
        String[] selectionArgs = new String[]{"T", "draught", "Mix", "Flight", mStoreNumber, "(beerNamePlaceholder)"};
        if (!tapsOrBottles.contains("taps")) {
            selectionFields = "ACTIVE=? AND CONTAINER<>? AND STYLE<>? and STYLE<>? and STORE_ID=? and (NAME=? or NAME=? or Name=?)";
            selectionArgs = new String[]{"T", "draught", "Mix", "Flight", mStoreNumber, "(beerNamePlaceholder)", "(beerNamePlaceholder)", "(beerNamePlaceholder)"};
        }
        SQLiteDatabase db = repository.openDb(context);

        db.execSQL("UPDATE UFOLOCAL SET ADDED_NOW_FLAG = ''");

        String lastUpdated = "1970 01 01";
        try {
            Calendar cal = Calendar.getInstance();
            lastUpdated = NDF.format(cal.getTime());
        } catch (Exception e) {}

        //for (String[] aResult: mFoundResults) { // DRS 20180112 - Change to ConcurrentHashSet - can't use forEach
        Iterator iterator = mFoundResults.iterator();
        while (iterator.hasNext()) {
            String[] aResult = (String[])iterator.next();
            String aBeerName = aResult[0];
            String aGlassSize = aResult[1];
            String aPriceString = aResult[2];
            String aAbv = aResult[3]; // may be ""
            String aBeerNumber = aResult[4]; // may be ""
            String aBreweryNumber = aResult[5]; // may be ""
            selectionArgs[5] = aBeerName;
            if (!tapsOrBottles.contains("taps")) {
                selectionArgs[6] = aBeerName + " (CAN)";
                selectionArgs[7] = aBeerName + " (BTL)";
            }

            // Look-up aBeerName in the main UFO table.  Expect one record (despite the 'for' loop).
            Cursor mainTableCursor = repository.query(       "UFO",          pullFields,        selectionFields,          selectionArgs);
            if (mainTableCursor != null) {
                //if (!tapsOrBottles.contains("taps")) Log.v(TAG, "DEBUG [" + selectionArgs[7] + "]");
                for (mainTableCursor.moveToFirst(); !mainTableCursor.isAfterLast(); mainTableCursor.moveToNext()) {
                    //Log.v(TAG, "DEBUG --------------------");
                    String beerName = mainTableCursor.getString(0);
                    String brewer = mainTableCursor.getString(1);
                    String store_id = mainTableCursor.getString(4);
                    String brew_id = mainTableCursor.getString(5);
                    String abv = mainTableCursor.getString(6);
                    String description = mainTableCursor.getString(7);
                    //Log.v(TAG, "For input into other table: [" + beerName + ", " + aGlassSize + ", " + brewer + ", " + store_id + ", " + brew_id + ", " + " ]");

                    //this check is done in copyMenuData()
                    //if ("0".equals(abv) && !"".equals(aAbv)) {
                        //Log.v(TAG, "The tap list did not have ABV, but the Untappd data did have ABV.\n[" + beerName + ", " + aAbv + ", " + description + "]");
                    //}

                    // Populate the UFOLOCAL table - up until now, it's been in mFoundResults<String[]>
                    ContentValues values = new ContentValues();
                    values.put("NAME", beerName);
                    values.put("STORE_ID", store_id);
                    values.put("BREW_ID", brew_id);
                    values.put("GLASS_SIZE", aGlassSize);
                    values.put("GLASS_PRICE", aPriceString);
                    values.put("LAST_UPDATED_DATE", lastUpdated);
                    values.put("ADDED_NOW_FLAG", "Y");
                    values.put("ABV", aAbv); // DRS 20220726
                    values.put("UNTAPPD_BEER", aBeerNumber); // DRS 20220730
                    values.put("UNTAPPD_BREWERY", aBreweryNumber); // DRS 20220730

                    // Update if it's there already, otherwise insert
                    int id = getId(beerName, store_id, db);
                    if (id == -1) {
                        db.insert("UFOLOCAL", null, values);
                        //Log.v(TAG, "Inserted record into UFOLOCAL");
                    } else {
                        db.update("UFOLOCAL", values, "_id=?", new String[]{Integer.toString(id)});
                        //Log.v(TAG, "Updated record in UFOLOCAL");
                    }
                } // end for each found menu item
                mainTableCursor.close();
            } else {
                Log.e(TAG, "NO DATA FROM THE DATABASE - null cursor");
            }
        }
        // Copy from the UFOLOCAL table to the UFO table, updating tap beers with whatever glass size and prices were found.
        UfoDatabaseAdapter.copyMenuData(context);
        repository.close();
        db.close();
        return UfoDatabaseAdapter.countMenuItems(mStoreNumber, context);
    }

    private int getId(String name, String storeId, SQLiteDatabase dbr){
        int resultId = -1;
        Cursor c = dbr.query("UFOLOCAL",new String[]{"_id"},"NAME =? AND STORE_ID=?",new String[]{name,storeId},null,null,null,null);
        if (c.moveToFirst()) //if the row exist then return the id
            resultId = c.getInt(c.getColumnIndexOrThrow("_id"));
        c.close();
        return resultId;
    }

    private Map getAllItems(String tapsOrBottles, Context context) {
        HashMap<String, String> allTaps = new HashMap<>(100);
        List<String[]> allTapsArrays = getAllItemsForMatching(tapsOrBottles, context);
        for (String[] aTapArray: allTapsArrays) {
            allTaps.put(aTapArray[0], breweryTextCleanup(aTapArray[1]));
        }
        Log.v(TAG, "There were " + allTaps.size() + " taps in the database for " + mStoreNumber);
        return allTaps;
    }

    private List<String[]> getAllItemsForMatching(String tapsOrBottles, Context context) {
        UfoDatabaseAdapter repository = new UfoDatabaseAdapter(context);
        UfoDatabaseAdapter.open(context);
        String[] pullFields = new String[]{SaucerItem.NAME, SaucerItem.BREWER, SaucerItem.BREW_ID};
        String selectionFields = "ACTIVE=? AND CONTAINER=? AND STYLE<>? and STYLE<>? and STORE_ID=?";
        if (!tapsOrBottles.contains("taps")) selectionFields = "ACTIVE=? AND CONTAINER<>? AND STYLE<>? and STYLE<>? and STORE_ID=?";
        String[] selectionArgs = new String[]{"T", "draught", "Mix", "Flight", mStoreNumber};
        Cursor aCursor = repository.query(       "UFO",          pullFields,        selectionFields,          selectionArgs);
        ArrayList<String[]> allItems = new ArrayList<String[]>();
        if (aCursor == null) {
            Log.e(TAG, "NO DATA FROM THE DATABASE - null cursor");
        } else {
            for (aCursor.moveToFirst(); !aCursor.isAfterLast(); aCursor.moveToNext()) {
                String beerName = aCursor.getString(0);
                if (!tapsOrBottles.contains("taps")) {
                    beerName = beerName.replace(" (CAN)", "");
                    beerName = beerName.replace(" (BTL)", "");
                }
                String brewer = aCursor.getString(1);
                //brewer = breweryTextCleanup(brewer);
                String brew_id = aCursor.getString(2);
                allItems.add(new String[]{beerName, brewer, brew_id});
            }
            aCursor.close();
        }
        repository.close();
        Log.v(TAG, "There were " + allItems.size() + " Saucer items pulled from the database for matching.");
        return allItems;
    }

    // This only removes one word or phrase
    public static String breweryTextCleanup(String brewer) {
        if (brewer == null) return null;
        brewer = brewer.replace("The", "").trim();
        brewer = brewer.replaceAll("'", "").trim();
        for (String companyBrewer: BREWERY_CLEANUP) {
            int wordLoc = brewer.indexOf(companyBrewer);
            if (wordLoc > -1) {
                brewer = brewer.replace(companyBrewer, "").trim();
                break;
            }
        }
        return brewer;
    }

    /*
    private void populateOcrMispellings() {
        //The key is what is in the Saucer database.
        //The alternates might be OCR mispellings OR just alternate official names
        mOcrBreweries.put("Rogue",new String[]{"oque", "Roque"});
        mOcrBreweries.put("Bells",new String[]{"Bell's","Bel's"});
        mOcrBreweries.put("Bell's",new String[]{"Bells","Bel's"});
        mOcrBreweries.put("Ayinger",new String[]{"nger"});
        mOcrBreweries.put("Lindeman's", new String[]{"Lindemans"});
        mOcrBreweries.put("Highland", new String[]{"ighland"});
        mOcrBreweries.put("Dogfish Head", new String[]{"ogfish Head"});
        mOcrBreweries.put("The Matt", new String[]{"Saranac"});
        mOcrBreweries.put("Mercury", new String[]{"Clown Shoes"});
        mOcrBreweries.put("Clipper City", new String[]{"Heavy Seas"});
        mOcrBreweries.put("Bird Song", new String[]{"Birds ang"});
        mOcrBreweries.put("Birdsong", new String[]{"Birds ang"});
        mOcrBreweries.put("Ciderboys Cider", new String[]{"Ciderboys"});
        mOcrBreweries.put("Ciderboys Cider Co", new String[]{"Ciderboys"});
        mOcrBreweries.put("Fullsteam", new String[]{"Fulsleam"});
        mOcrBreweries.put("Hi Wire", new String[]{"H-Wire"});
        mOcrBreweries.put("Stone", new String[]{"IOne"});
        mOcrBreweries.put("21st Amendemnt", new String[]{"21st Amendement","2st Amendment"});

    }
    */

}