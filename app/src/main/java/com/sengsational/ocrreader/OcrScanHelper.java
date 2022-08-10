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

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.sengsational.knurder.SaucerItem.BREWERY_CLEANUP;
import static com.sengsational.knurder.TopLevelActivity.STORE_NUMBER;
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
        mStoreNumber = prefs.getString(STORE_NUMBER,"13888");
    }

    public int[] getResults(Context context) {
        int populatedActiveTapsCount = updateFoundTaps(context);
        Log.v(TAG, "mPouplatedActiveTapsCount " + populatedActiveTapsCount);
        Log.v(TAG, "mFoundResults.size() " + mFoundResults.size());
        return new int[] {populatedActiveTapsCount, mFoundResults.size(), getAllTaps(context).size()};
    }

    public void matchUntappdItems(ArrayList<UntappdItem> items, Context context) {
        if (!mStoreNumber.equals(prefs.getString(STORE_NUMBER,"13888"))) {
            mStoreNumber = prefs.getString(STORE_NUMBER,"13888");
        }
        /* This is to log the data so that we can perform offline validation
        boolean logTheSaucerAndUntappdItems = false;
        if (logTheSaucerAndUntappdItems) {
            StringBuffer csvBuf = new StringBuffer();
            for (UntappdItem item: items) {
                csvBuf.append("#\"").append("UNTAPPD").append("\",").append(item.getMatchingCsv());
                Log.v(TAG, csvBuf.toString());
                csvBuf = new StringBuffer();
            }
            List<String[]> allTapNames = getAllTapsForMatching();
            for (int i = 0; i < allTapNames.size(); i++){
                String[] values = allTapNames.get(i);
                csvBuf.append("#\"").append("SAUCER").append("\",\"").append(values[0]).append("\",\"").append(values[1]).append("\",\"").append(values[2]).append("\",\"").append(mStoreNumber).append("\"");
                Log.v(TAG, csvBuf.toString());
                csvBuf = new StringBuffer();
            }
        }
        */

        MatchGroup matchGroup = new MatchGroup().load(getAllTapsForMatching(context), mStoreNumber).load(items);
        mFoundResults = matchGroup.match();

        /** THIS IS FOR CURIOSITY ABOUT UNMATCHED ITEMS */
        ArrayList<MatchItem>leftovers = matchGroup.getLeftoverSaucer();
        for (MatchItem item: leftovers) {
            Log.v(TAG, "\"" + item.getNonStyleTextMatch() + "\", " + item);
        }
        leftovers = matchGroup.getLeftoverUntappd();
        for (MatchItem item: leftovers) {
            Log.v(TAG, "\"" + item.getNonStyleTextMatch() + "\", " + item);
        }

        /*  THIS IS THE OLD WAY TO DO THE MATCHING
        Log.v(TAG, "Looking-up all items in matchUntappdItems().  mAllTapNames was " + mAllTapsNames.size() + " items.");
        ArrayList<UntappdItem> notFoundItems = new ArrayList<UntappdItem>();
        for (UntappdItem item: items) {
            String[] foundResult = lookupBeerName(item); // <<<<<<<<<<<<<<<<<  populates mFoundResults
            if (foundResult == null) notFoundItems.add(item);
        }
        Log.v(TAG, notFoundItems.size() + " items not found out of a total of " + items.size() + ".");

        Set<String> unmatchedKeySet = mAllTapsNames.keySet();
        for (String key: unmatchedKeySet) {
            Log.v(TAG, "Unmatched Tap: " + key + " " + mAllTapsNames.get(key));
        }
        for (int i = 0; i < notFoundItems.size(); i++) {
            Log.v(TAG, "Untappd not found: " + notFoundItems.get(i));
        }
         */
    }
    /*
    void scanNewArrivals(TextBlock item) {
        List<? extends Text> lines = item.getComponents();
        for (int j = 0; j < lines.size(); j++) {
            Log.v(TAG, "line j [" + lines.get(j).getValue() + "]");
            Text linea = lines.get(j);
            OcrMenuItemProcessor processor = new OcrMenuItemProcessor(linea.getValue());
            String[] parseResults = processor.parseLine();
            if (parseResults !=  null) {
                String beerName = parseResults[0];
                String glassSize = parseResults[1]; // <<<<<<<<<<<<<< Probably blank
                String priceString = parseResults[2];
                String abvString = ""; // Added for compatability with Untappd replacement technique.
                lookupBeerName(beerName, glassSize, priceString, abvString, "", ""); // If found, populates mFoundResults and removes an item from mAllTapsNames
            }
        }
        Log.v(TAG, "---------");
    }
    */
    /*
    public void scanTapMenu(TextBlock item) {
        // This technique locks onto the "slash" between the price and the glass size.  'j' is where we're looking for the slash.  The beer name would be in j-1
        List<? extends Text> lines = item.getComponents();
        for (int j = 1; j < lines.size(); j++) {

            Text linea = lines.get(j - 1);
            Text lineb = lines.get(j);
            Text previousLine = ((j > 1)?lines.get(j - 2):null); // previous line is to supply the text for if this is a two-line beer name

            if (linea == null || lineb == null) continue;
            //Log.v(TAG, "linea [" + linea.getValue() + "] lineb [" + lineb.getValue() + "]");
            String previousLineString = ((previousLine == null)?"":previousLine.getValue());

            OcrMenuItemProcessor processor = new OcrMenuItemProcessor(linea.getValue(), lineb.getValue(), previousLineString);
            String[] parseResults = processor.parseLinePair();
            if (parseResults !=  null) {

                String beerName = parseResults[0];
                String glassSize = parseResults[1];
                String priceString = parseResults[2];
                previousLineString = parseResults[3];
                String abvString = ""; // Added for compatability with Untappd replacement technique.

                String[] searchNames = {beerName, previousLineString + " " + beerName}; // This is for stacked (two line) beer names
                int nameCount = ((previousLineString.length() > 0)?2:1); // We either look up one or two names

                for (int m = 0; m < nameCount; m++) {
                    lookupBeerName(searchNames[m], glassSize, priceString, abvString, "", ""); // If found, populates mFoundResults and removes an item from mAllTapsNames
                }
            }
        }
    }
     */

    /* Below is the old way to match
    private String[] lookupBeerName(UntappdItem item) {
        return lookupBeerName(item.getBreweryPrefixedName(), item.getOuncesNumber(), item.getPriceNumber(), item.getAbvNumber(), item.getBeerNumber(), item.getBreweryNumber());
    }

    // Here we populate mFoundResults and use that later in updateFoundTaps()
    private String[] lookupBeerName(String searchName, String glassSize, String priceString, String abvString, String beerNumber, String breweryNumber) {
        String[] foundResult = null;
        // first just look-up the beer...might get lucky with an exact match
        if (mAllTapsNames.containsKey(searchName)) {
            Log.v(TAG, "EXACT MATCH!! " + searchName + " " + glassSize);
            foundResult = new String[] {searchName, glassSize, priceString, abvString, beerNumber, breweryNumber};
            mFoundResults.add(foundResult);
            mAllTapsNames.remove(searchName);
            Log.v(TAG, "--------- found:" + foundResult[0] + " for " + searchName);
            return foundResult;
        } else {
            // There was not an exact match.  Look through all taps for a fuzzy match
            Set<String> tapNames = mAllTapsNames.keySet();
            ArrayList<String> tapsToRemove = new ArrayList<String>();
            OUT: for (String aTap: tapNames) {
                Double score = fuzzy.compare(aTap, searchName) ;
                Double scoreNp = fuzzy.compare(aTap.replaceAll("\\p{Punct}",""), searchName.replaceAll("\\p{Punct}",""));
                //if (searchName.contains("Blueberry")) {
                //    Log.v(TAG, searchName + " ? " + aTap + " " + score + " " + scoreNp);
                //}
                if (score > 0.38D || scoreNp > 0.38D) {
                    if (score > 0.84D || (aTap.length() < 15 && searchName.length() < 15 && score > .65D)) {
                        Log.v(TAG, "FUZZY MATCH!! #" + aTap + "#" + glassSize + "#" + searchName + "#" + df.format(score));
                        foundResult = new String[] {aTap, glassSize, priceString, abvString, beerNumber, breweryNumber};
                        mFoundResults.add(foundResult);
                        tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);
                        break;
                    } if (score > 0.45D) { //DRS 20171128 - This was .58D, but I moved it down
                        // Try removing brewery name and check the score again
                        String brewery = mAllTapsNames.get(aTap);
                        Log.v(TAG, "Checking Brewery Name DB Brewery [" + brewery + "] DB Tap [" + aTap + "] Working on OCR [" + searchName + "]");
                        //int breweryLocInDb = aTap.indexOf(brewery);
                        int breweryLocInDb = aTap.toUpperCase().indexOf(brewery.toUpperCase()); // DRS 20180108 - Find location based on upper case fix "NoDa" vs "Noda"
                        if (breweryLocInDb < 0) { // This should be false most times since they name their beers with brewery name at the beginning
                            Log.v(TAG, "!Trying alternates");
                            String[] alternates = mOcrBreweries.get(brewery);
                            if (alternates != null) {
                                for (String alternate: alternates) {
                                    //breweryLocInDb = searchName.indexOf(alternate);
                                    breweryLocInDb = searchName.toUpperCase().indexOf(alternate.toUpperCase()); // DRS 20180108 - Find location based on upper case fix "NoDa" vs "Noda"
                                    Log.v(TAG, "!Alternate " + alternate + " for " + brewery);
                                    if (breweryLocInDb > -1) {
                                        Log.v(TAG, "!USING [" + alternate + "] instead of [" + brewery + "]");
                                        brewery = alternate;
                                        searchName = searchName.replace(alternate, brewery); // fix it with the good brewery name // DRS 20180108 - Can't use "replace" because the of the possible upper case difference
                                        break;
                                    }
                                }
                            }
                        }

                        int breweryLocInOcr = searchName.toUpperCase().indexOf(brewery.toUpperCase()); // DRS 20180108 - Find location based on upper case fix "NoDa" vs "Noda"
                        int breweryWordSize = brewery.length();
                        if (breweryLocInOcr < 0) { // Try a softer match on the brewery name
                            Log.v(TAG, "Trying alternates");
                            String[] alternates = mOcrBreweries.get(brewery); // Alternates include mispellings
                            if (alternates != null) {
                                for (String alternate: alternates) {
                                    breweryLocInOcr = searchName.toUpperCase().indexOf(alternate.toUpperCase());
                                    Log.v(TAG, "Alternate " + alternate + " for " + brewery);
                                    if (searchName.contains(alternate)) continue;
                                    if (breweryLocInOcr > -1) { // <<<<<<<<<<  Found a mis-spelled brewery name
                                        //searchName = searchName.replace(alternate, brewery); // fix it with the good brewery name // DRS 20180108 - Can't use "replace" because the of the possible upper case difference
                                        searchName = replaceByLocation(searchName, alternate, breweryLocInOcr, breweryWordSize); // DRS 20180108 - Find location based on upper case fix "NoDa" vs "Noda"
                                        Log.v(TAG, "Replacing [" + alternate + "] with [" + brewery + "]");
                                        breweryLocInOcr = searchName.toUpperCase().indexOf(brewery.toUpperCase()); // update the location (was < 0, should now be positive)
                                        break;
                                    }
                                }
                            }
                        }
                        if (breweryLocInDb > -1 && breweryLocInOcr > -1) {
                            Log.v(TAG, "Removing " + brewery + " text from beer names to repeat the match.");
                            //String libraryNoBreweryBeer = aTap.replace(brewery, "").trim();
                            //String foundNoBreweryBeer = searchName.replace(brewery, "").trim();
                            String libraryNoBreweryBeer = replaceByLocation(aTap,"",breweryLocInDb, brewery.length()).trim();
                            String foundNoBreweryBeer = replaceByLocation(searchName, "", breweryLocInOcr, brewery.length()).trim();
                            score = fuzzy.compare(libraryNoBreweryBeer, foundNoBreweryBeer);
                            if (score > 0.55D) {
                                Log.v(TAG, "FUZZY MATCH SPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                foundResult = new String[] {aTap, glassSize, priceString, abvString, beerNumber, breweryNumber};
                                mFoundResults.add(foundResult);
                                tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                break;
                            } else {
                                if (foundNoBreweryBeer.startsWith(libraryNoBreweryBeer) || libraryNoBreweryBeer.startsWith(foundNoBreweryBeer)){
                                    Log.v(TAG, "FUZZY MATCH STARTS WITH: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                    foundResult = new String[] {aTap, glassSize, priceString, abvString, beerNumber, breweryNumber};
                                    mFoundResults.add(foundResult);
                                    tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                    break;
                                } else {
                                    Log.v(TAG, "Low quality match after brewery removal: " + brewery + ":" + libraryNoBreweryBeer + " - " + foundNoBreweryBeer);
                                    Log.v(TAG, "FUZZY SCORE SPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                    String[] genericWords = {"BROWN ALE", "PILSNER", "PLSNER","ALE", "IPA", "NITRO", "(NITRO)"}; // Sort longest to shortest length
                                    for (String styleWord: genericWords) {
                                        int styleWordLoc = foundNoBreweryBeer.toUpperCase().indexOf(styleWord);
                                        //Log.v(TAG, "styleWordLoc " + styleWordLoc + " in [" + foundNoBreweryBeer.toUpperCase() + "] << [" + styleWord + "]");
                                        if (styleWordLoc > 5) { // make sure style is trailing some beer name
                                            foundNoBreweryBeer = foundNoBreweryBeer.substring(0, styleWordLoc).trim();
                                            score = fuzzy.compare(foundNoBreweryBeer, libraryNoBreweryBeer);
                                            Log.v(TAG, "*!*FUZZY SCORE SSSPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                            if (score > 0.55D) {
                                                Log.v(TAG, "*!*FUZZY MATCH SSSPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                                foundResult = new String[] {aTap, glassSize, priceString, abvString, beerNumber, breweryNumber};
                                                mFoundResults.add(foundResult);
                                                tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                                break OUT;
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Log.v(TAG, "Trying fuzzy match on brewery removal");
                            int wordCount = brewery.split(" ").length;
                            String[] searchNameWords = searchName.split(" ");
                            StringBuilder builder = new StringBuilder();
                            for (int i = 0; i < wordCount; i++){
                                builder.append(searchNameWords[i]);
                                builder.append(" ");
                            }
                            String searchNameBrewery = builder.toString().trim();
                            score = fuzzy.compare(brewery, searchNameBrewery);
                            if (score > .85D) {
                                // This is just a misspelled brewery, so remove the number of words and repeat the beer match
                                String libraryNoBreweryBeer = aTap.replace(brewery, "").trim();
                                String foundNoBreweryBeer = searchName.replace(searchNameBrewery, "").trim();
                                score = fuzzy.compare(foundNoBreweryBeer, libraryNoBreweryBeer);
                                if (score > 0.55D) {
                                    Log.v(TAG, "*!*FUZZY MATCH SPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                    foundResult = new String[] {aTap, glassSize, priceString, abvString, beerNumber, breweryNumber};
                                    mFoundResults.add(foundResult);
                                    tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                    break;
                                } else {
                                    if (foundNoBreweryBeer.startsWith(libraryNoBreweryBeer) || libraryNoBreweryBeer.startsWith(foundNoBreweryBeer)){
                                        Log.v(TAG, "*!*FUZZY MATCH STARTS WITH: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                        foundResult = new String[] {aTap, glassSize, priceString, abvString, beerNumber, breweryNumber};
                                        mFoundResults.add(foundResult);
                                        tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                        break;
                                    } else {
                                        Log.v(TAG, "*!*Low quality match after brewery removal: " + brewery + ":" + libraryNoBreweryBeer + " - " + foundNoBreweryBeer);
                                        Log.v(TAG, "*!*FUZZY SCORE SPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                    }
                                }
                            } else {
                                // Try alternate breweries



                                Log.v(TAG, "Could not remove brewery name from both: " + brewery + ":" + aTap + " - " + searchName);
                                Log.v(TAG, "FUZZY SCORE NBR: #" + searchName + "#" + df.format(score) + "#" + aTap);
                            }
                        }
                    } else {
                        Log.v(TAG, "FUZZY SCORE TOO LOW: #" + searchName + "#" + df.format(score) + "#" + aTap);
                    }
                }
            } // END 'for' Loop
            for (String aTap : tapsToRemove) {
                mAllTapsNames.remove(aTap);
            }
            if (foundResult != null) Log.v(TAG, "--------- found:" + foundResult[0] + " for " + searchName);
            else Log.v(TAG, "--------- found:" + foundResult + " for " + searchName);
            return foundResult;
        }
    }
    */

    // "searchName" should contain a word starting at breweryLocInOcr and of length breweryWordSize.  This method replaces that word/phrase with the contents of "alternate"
    private String replaceByLocation(String searchName, String alternate, int breweryLocInOcr, int breweryWordSize) {
        if (breweryLocInOcr < 0 || breweryWordSize < 1) return searchName;
        StringBuffer returnStringValue = new StringBuffer(searchName);
        returnStringValue.replace(breweryLocInOcr, breweryLocInOcr + breweryWordSize, alternate);
        return returnStringValue.toString();
    }

    /* Uses mFoundResults to add tap menu data */
    private int updateFoundTaps(Context context) {
        UfoDatabaseAdapter repository = new UfoDatabaseAdapter(context);
        String[] pullFields = new String[]{SaucerItem.NAME, SaucerItem.BREWER, SaucerItem.ACTIVE, SaucerItem.CONTAINER, SaucerItem.STORE_ID, SaucerItem.BREW_ID, SaucerItem.ABV, SaucerItem.DESCRIPTION};
        String selectionFields = "ACTIVE=? AND CONTAINER=? AND STYLE<>? and STYLE<>? and STORE_ID=? and NAME=?";
        String[] selectionArgs = new String[]{"T", "draught", "Mix", "Flight", mStoreNumber, "(beerNamePlaceholder)"};
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

            // Look-up aBeerName in the main UFO table.  Expect one record (despite the 'for' loop).
            Cursor mainTableCursor = repository.query(       "UFO",          pullFields,        selectionFields,          selectionArgs);
            if (mainTableCursor != null) {
                for (mainTableCursor.moveToFirst(); !mainTableCursor.isAfterLast(); mainTableCursor.moveToNext()) {
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
                        Log.v(TAG, "Inserted record into UFOLOCAL");
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
            resultId = c.getInt(c.getColumnIndex("_id"));
        c.close();
        return resultId;
    }

    private Map getAllTaps(Context context) {
        HashMap<String, String> allTaps = new HashMap<>(100);
        List<String[]> allTapsArrays = getAllTapsForMatching(context);
        for (String[] aTapArray: allTapsArrays) {
            allTaps.put(aTapArray[0], breweryTextCleanup(aTapArray[1]));
        }
        Log.v(TAG, "There were " + allTaps.size() + " taps in the database for " + mStoreNumber);
        return allTaps;
    }

    private List<String[]> getAllTapsForMatching(Context context) {
        UfoDatabaseAdapter repository = new UfoDatabaseAdapter(context);
        UfoDatabaseAdapter.open(context);
        String[] pullFields = new String[]{SaucerItem.NAME, SaucerItem.BREWER, SaucerItem.BREW_ID};
        String selectionFields = "ACTIVE=? AND CONTAINER=? AND STYLE<>? and STYLE<>? and STORE_ID=?";
        String[] selectionArgs = new String[]{"T", "draught", "Mix", "Flight", mStoreNumber};
        Cursor aCursor = repository.query(       "UFO",          pullFields,        selectionFields,          selectionArgs);
        ArrayList<String[]> allTaps = new ArrayList<String[]>();
        if (aCursor == null) {
            Log.e(TAG, "NO DATA FROM THE DATABASE - null cursor");
        } else {
            for (aCursor.moveToFirst(); !aCursor.isAfterLast(); aCursor.moveToNext()) {
                String beerName = aCursor.getString(0);
                String brewer = aCursor.getString(1);
                //brewer = breweryTextCleanup(brewer);
                String brew_id = aCursor.getString(2);
                allTaps.add(new String[]{beerName, brewer, brew_id});
            }
            aCursor.close();
        }
        repository.close();
        return allTaps;
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