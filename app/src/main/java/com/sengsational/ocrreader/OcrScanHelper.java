package com.sengsational.ocrreader;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewParent;

import com.sengsational.knurder.ConcurrentHashSet;
import com.sengsational.knurder.SaucerItem;
import com.sengsational.knurder.TopLevelActivity;
import com.sengsational.knurder.UfoDatabaseAdapter;
import com.sengsational.knurder.UntappdItem;
import com.sengsational.ocrreader.camera.GraphicOverlay;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.sengsational.ocrreader.camera.Levenshtein;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static com.sengsational.knurder.SaucerItem.BREWERY_CLEANUP;
import static com.sengsational.knurder.TopLevelActivity.STORE_NUMBER;
import static com.sengsational.knurder.TopLevelActivity.prefs;

public class OcrScanHelper {
    private static final String TAG = OcrScanHelper.class.getSimpleName();
    private final Context mContext;
    private static final SimpleDateFormat NDF = new SimpleDateFormat("yyyy MM dd");

    private static ConcurrentHashSet<String[]> mFoundResults;
    private static Map<String, String> mAllTapsNames;
    private static Levenshtein fuzzy = new Levenshtein();
    private static final DecimalFormat df = new DecimalFormat("#.##");
    //private static final String[] breweryCleanup = {"& Son Co.","Ltd","Brouwerij","& Sohn","& Co. Brewing","(Palm)","Brasserie","Brewing Company","Brewing Co.","Brewing Co","Brewing","Beer Company", "Hard Cider Co.","Craft Brewery","Brewery","Artisanal Ales","Ales","Cidery"};
    private static final Map<String, String[]> mOcrBreweries = new HashMap<>();
    private static String mStoreNumber = "13888"; // better than nothing
    private int mPopulatedActiveTapsCount;
    private static OcrScanHelper mOcrScanHelper;

    public static OcrScanHelper getInstance(Context context){
        if (mOcrScanHelper == null) {
            mOcrScanHelper = new OcrScanHelper(context);
        }
        return mOcrScanHelper;
    }

    private OcrScanHelper(Context context) {
        Log.v(TAG, ">>>>>>>>>>>> OcrScanHelperCreated <<<<<<<<<<<<<<< (this should happen once per UI action)");
        mStoreNumber = prefs.getString(STORE_NUMBER,"13888");
        mContext = context;
        mAllTapsNames = getAllTaps();
        populateOcrMispellings();
        mFoundResults = new ConcurrentHashSet<>(100);
    }

    public int[] getResults(Context context) {
        int populatedActiveTapsCount = updateFoundTaps(context);
        /*
        Iterator iterator = mFoundResults.iterator();
        while (iterator.hasNext()) {
            String[] aTap = (String[])iterator.next();
            //Log.v(TAG, "#FOUND#" + aTap[0] + "#" + aTap[1]);
        }
        */
        /*
        // This doesn't really work right.  Not sure why.
        for (String aTap: mAllTapsNames.keySet()) {
            Log.v(TAG, "#NOTFOUND#" + aTap + "#");
        }
        */

        Log.v(TAG, "mPouplatedActiveTapsCount " + populatedActiveTapsCount);
        Log.v(TAG, "mFoundResults.size() " + mFoundResults.size());
        return new int[] {populatedActiveTapsCount, mFoundResults.size(), mAllTapsNames.size()};
    }

    public void matchUntappdItems(ArrayList<UntappdItem> items) {
        Log.v(TAG, "Looking-up all items in matchUntappdItems()");
        for (UntappdItem item: items) {
            lookupBeerName(item);
        }
        Log.v(TAG, "All items looked-up in matchUntappdItems()");
    }

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
                lookupBeerName(beerName, glassSize, priceString); // If found, populates mFoundResults and removes an item from mAllTapsNames
            }
        }
        Log.v(TAG, "---------");
    }

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

                String[] searchNames = {beerName, previousLineString + " " + beerName}; // This is for stacked (two line) beer names
                int nameCount = ((previousLineString.length() > 0)?2:1); // We either look up one or two names

                for (int m = 0; m < nameCount; m++) {
                    lookupBeerName(searchNames[m], glassSize, priceString); // If found, populates mFoundResults and removes an item from mAllTapsNames
                }
            }
        }
    }

    private void lookupBeerName(UntappdItem item) {
        lookupBeerName(item.getBreweryPrefixedName(), item.getOuncesNumber(), item.getPriceNumber());
    }

    private void lookupBeerName(String searchName, String glassSize, String priceString) {
        // first just look-up the beer...might get lucky with an exact match
        if (mAllTapsNames.containsKey(searchName)) {
            Log.v(TAG, "EXACT MATCH!! " + searchName + " " + glassSize);
            mFoundResults.add(new String[] {searchName, glassSize, priceString});
            mAllTapsNames.remove(searchName);
            return;
        } else {
            // There was not an exact match.  Look through all taps for a fuzzy match
            Set<String> tapNames = mAllTapsNames.keySet();
            ArrayList<String> tapsToRemove = new ArrayList<String>();
            for (String aTap: tapNames) {
                Double score = fuzzy.compare(aTap, searchName) ;
                if (score > 0.40D) {
                    if (score > 0.84D || (aTap.length() < 15 && searchName.length() < 15 && score > .65D)) {
                        Log.v(TAG, "FUZZY MATCH!! #" + aTap + "#" + glassSize + "#" + searchName + "#" + df.format(score));
                        mFoundResults.add(new String[] {aTap, glassSize, priceString});
                        tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);
                        return;
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
                                mFoundResults.add(new String[] {aTap, glassSize, priceString});
                                tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                break;
                            } else {
                                if (foundNoBreweryBeer.startsWith(libraryNoBreweryBeer) || libraryNoBreweryBeer.startsWith(foundNoBreweryBeer)){
                                    Log.v(TAG, "FUZZY MATCH STARTS WITH: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                    mFoundResults.add(new String[] {aTap, glassSize, priceString});
                                    tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                    break;
                                } else {
                                    Log.v(TAG, "Low quality match after brewery removal: " + brewery + ":" + libraryNoBreweryBeer + " - " + foundNoBreweryBeer);
                                    Log.v(TAG, "FUZZY SCORE SPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                    String[] genericWords = {"BROWN ALE", "PILSNER", "PLSNER","ALE", "IPA", "NITRO", "(NITRO)"}; // Sort longest to shortest length
                                    OUT: for (String styleWord: genericWords) {
                                        int styleWordLoc = foundNoBreweryBeer.toUpperCase().indexOf(styleWord);
                                        //Log.v(TAG, "styleWordLoc " + styleWordLoc + " in [" + foundNoBreweryBeer.toUpperCase() + "] << [" + styleWord + "]");
                                        if (styleWordLoc > 5) { // make sure style is trailing some beer name
                                            foundNoBreweryBeer = foundNoBreweryBeer.substring(0, styleWordLoc).trim();
                                            score = fuzzy.compare(foundNoBreweryBeer, libraryNoBreweryBeer);
                                            Log.v(TAG, "*!*FUZZY SCORE SSSPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                            if (score > 0.55D) {
                                                Log.v(TAG, "*!*FUZZY MATCH SSSPECIAL: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                                mFoundResults.add(new String[] {aTap, glassSize, priceString});
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
                                    mFoundResults.add(new String[] {aTap, glassSize, priceString});
                                    tapsToRemove.add(aTap);//mAllTapsNames.remove(aTap);mAllTapsNames.remove(aTap);
                                    break;
                                } else {
                                    if (foundNoBreweryBeer.startsWith(libraryNoBreweryBeer) || libraryNoBreweryBeer.startsWith(foundNoBreweryBeer)){
                                        Log.v(TAG, "*!*FUZZY MATCH STARTS WITH: #" + foundNoBreweryBeer + "#" + df.format(score) + "#" + libraryNoBreweryBeer);
                                        mFoundResults.add(new String[] {aTap, glassSize, priceString});
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
                        Log.v(TAG, "FUZZY SCORE: #" + searchName + "#" + df.format(score) + "#" + aTap);
                    }
                }
            }
            for (String aTap : tapsToRemove) {
                mAllTapsNames.remove(aTap);
            }
        }
    }

    // "searchName" should contain a word starting at breweryLocInOcr and of length breweryWordSize.  This method replaces that word/phrase with the contents of "alternate"
    private String replaceByLocation(String searchName, String alternate, int breweryLocInOcr, int breweryWordSize) {
        if (breweryLocInOcr < 0 || breweryWordSize < 1) return searchName;
        StringBuffer returnStringValue = new StringBuffer(searchName);
        returnStringValue.replace(breweryLocInOcr, breweryLocInOcr + breweryWordSize, alternate);
        return returnStringValue.toString();
    }


    private int updateFoundTaps(Context context) {
        UfoDatabaseAdapter repository = new UfoDatabaseAdapter(context);
        String[] pullFields = new String[]{SaucerItem.NAME, SaucerItem.BREWER, SaucerItem.ACTIVE, SaucerItem.CONTAINER, SaucerItem.STORE_ID, SaucerItem.BREW_ID};
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
            selectionArgs[5] = aBeerName;

            // Look-up aBeerName in the main UFO table.  Expect one record (despite the 'for' loop).
            Cursor mainTableCursor = repository.query(       "UFO",          pullFields,        selectionFields,          selectionArgs);
            if (mainTableCursor != null) {
                for (mainTableCursor.moveToFirst(); !mainTableCursor.isAfterLast(); mainTableCursor.moveToNext()) {
                    String beerName = mainTableCursor.getString(0);
                    String brewer = mainTableCursor.getString(1);
                    String store_id = mainTableCursor.getString(4);
                    String brew_id = mainTableCursor.getString(5);
                    //Log.v(TAG, "For input into other table: [" + beerName + ", " + aGlassSize + ", " + brewer + ", " + store_id + ", " + brew_id + ", " + " ]");

                    // Populate the UFOLOCAL table - up until now, it's been in mFoundResults<String[]>
                    ContentValues values = new ContentValues();
                    values.put("NAME", beerName);
                    values.put("STORE_ID", store_id);
                    values.put("BREW_ID", brew_id);
                    values.put("GLASS_SIZE", aGlassSize);
                    values.put("GLASS_PRICE", aPriceString);
                    values.put("LAST_UPDATED_DATE", lastUpdated);
                    values.put("ADDED_NOW_FLAG", "Y");

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

    private Map getAllTaps() {
        UfoDatabaseAdapter repository = new UfoDatabaseAdapter(mContext);
        repository.open(mContext);
        String[] pullFields = new String[]{SaucerItem.NAME, SaucerItem.BREWER, SaucerItem.ACTIVE, SaucerItem.CONTAINER, SaucerItem.STORE_ID, SaucerItem.BREW_ID};
        String selectionFields = "ACTIVE=? AND CONTAINER=? AND STYLE<>? and STYLE<>? and STORE_ID=?";
        String[] selectionArgs = new String[]{"T", "draught", "Mix", "Flight", mStoreNumber};
        //                          query(String table, String[] pullFields, String selectionFields, String[] selectionArgs)
        Cursor aCursor = repository.query(       "UFO",          pullFields,        selectionFields,          selectionArgs);
        HashMap<String, String> allTaps = new HashMap<>(100);
        if (aCursor == null) {
            Log.e(TAG, "NO DATA FROM THE DATABASE - null cursor");
        } else {
            for (aCursor.moveToFirst(); !aCursor.isAfterLast(); aCursor.moveToNext()) {
                String beerName = aCursor.getString(0);
                String brewer = aCursor.getString(1);
                allTaps.put(beerName, breweryTextCleanup(brewer));
                Log.v(TAG, "Active Tap Beer Name: [" + beerName + ", " + allTaps.get(beerName) + "]");
            }
            aCursor.close();
        }
        repository.close();
        return allTaps;
    }

    public static String breweryTextCleanup(String brewer) {
        for (String companyBrewer: BREWERY_CLEANUP) {
            int wordLoc = brewer.indexOf(companyBrewer);
            if (wordLoc > -1) {
                //Log.v(TAG, "companyBrewer " + companyBrewer + " --  " + brewer);
                brewer = brewer.replace(companyBrewer, "").trim();
                break;
            }
        }
        return brewer;
    }

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

}