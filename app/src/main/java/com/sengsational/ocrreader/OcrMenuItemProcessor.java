package com.sengsational.ocrreader;

import android.util.Log;

import java.text.DecimalFormat;
import java.util.HashMap;

/**
 * Created by Owner on 11/8/2017.
 */


class OcrMenuItemProcessor {
    static final String TAG = OcrMenuItemProcessor.class.getSimpleName();
    String mLinea;
    String mLineb;
    String mPreviousLine;
    static final DecimalFormat DF = new DecimalFormat("#.##");{
        DF.setMinimumFractionDigits(2);
    }

    // This table has common "misspellings" of the glass size.  It will probably need to be expanded
    //TODO: Expand the table with glass sizes from other Saucer locations.  I know they have 11.  I saw one menu once that had 11.5.
    //TODO: Add a way for them to tell me / show me different glass sizes.
    static String[][] mOuncesLookup = {
        {"/13oz","10oz","/16oz","13ez","130z","130","13oz","160z","16Goz","16ox","16oz","16az","1Goz","16 O2","16 02","i6oz","Igoz","16o:","a6oz","z6oz", "1302","1602","CI00z","1002","(100zl","1100z","C10oz","100z","10az","100Z","I00z","I00Z","13ox","1602z","160x","160Z","16io","16ioz","16o","16o2","16oa","16or","16oZ","5/16oz","5/1Gor","I3oz","ı3oz","I60z","I6oz", "16a", "1662"},
        {   "13",  "10",   "16",  "13",  "13", "13",  "13",  "16",   "16",  "16",  "16",  "16",  "16",   "16",   "16",  "16",  "16",  "16",  "16",  "16",   "13",  "16",   "10",  "10",    "10",   "10",   "10",  "10",  "10",  "10",  "10",  "10",  "13",   "16",  "16",  "16",  "16",   "16", "16",  "16",  "16",  "16",  "16",    "16",    "16",  "13",  "13",  "16",  "16",  "16",   "16"}
    };

    // This table has common "misspellings" of the brewery name. (for some reason the OCR likes to leave off the first letter or two).
    // This populates OcrMenuItemProcessor.mBreweryNameMap Key: misspelled brewery Value: correct brewery.
    // This map is used early, when parsing the line pair.

    // There is another map OcrScanHelper.mOcrBreweries Key: correct brewery Value: {misspelling_1, misspelling_2, etc}
    // This other map is used when we know the correct brewry name (from the tap list, and we're trying to replace brewery name in the OCR

    // Both tables are legit, but do have overlap in function.
    static String[][] mBreweryNameLookup = {
            {" ogfish Head",    "nger",  "ighland"},
            {"Dogfish Head", "Ayinger", "Highland"}
    };

    static HashMap<String, String> mOuncesMap;
    static HashMap<String, String> mBreweryNameMap;

    public OcrMenuItemProcessor(String linea, String lineb, String previousLine) {
        if (linea != null) mLinea = linea.trim(); // ie "Bell's Oberon"
        if (lineb != null) mLineb = lineb.trim() + " "; // ie "$5.75/16oz Kalamzoo, MI (6.5%)
        if (previousLine != null) mPreviousLine = previousLine.trim(); // often empty an empty string, but could be the first of a two-line beer name
        else mPreviousLine = "";
        loadIfNeeded();  // Setup static tables
    }

    public OcrMenuItemProcessor(String linea) {
        if (linea != null) mLinea = linea.trim(); // ie "Bell's Oberon"
        loadIfNeeded();
    }


    /**
     * This method returns the OCR'd name (often wrong), along with the price, and maybe glass size.
     * If the price is not found, this method returns null.
     * @return String array with OCR'd beer name, price, glass size.
     */
    public String[] parseLine() {
        // needs to have two dashes to qualify
        String dashSplit[] = mLinea.split("-");
        if (dashSplit.length < 3) return null;

        // the price should be between the last two dashes
        float priceNumber = -1f;
        String price = getPriceFromLine(dashSplit[dashSplit.length-2]);
        if (price != null) {
            priceNumber = Float.parseFloat(price);
            Log.v(TAG, "Ended with: [" + price + "]");
        } else {
            Log.v(TAG, ">>>>>>>>>>>>> Not able to get price!!+++++ " + priceNumber + " ocr:" + mLineb + "   on this beer: " + mLinea);
            return null;
        }

        // Combine beers that have a dash in their name.  Normally, dashSplit.length == 3, so for loop doesn't run
        String beerName = dashSplit[0];
        for (int i = 1; i < dashSplit.length - 2; i++){
            beerName = beerName + "-" + dashSplit[i];
        }

        // if there is an open parenthesis at the end, then try to get a glass size
        String glassOunces = "";
        int parenLoc = mLinea.indexOf("(");
        if (parenLoc > (mLinea.length() - 10) && parenLoc < mLinea.length() - 2) {
            String ouncesParsed = mLinea.substring(parenLoc + 1, mLinea.length());
            if (ouncesParsed.endsWith(")")) ouncesParsed = ouncesParsed.substring(0,ouncesParsed.length() - 1);
            if (ouncesParsed.endsWith("l")) ouncesParsed = ouncesParsed.substring(0,ouncesParsed.length() - 1);
            if (ouncesParsed.endsWith("]")) ouncesParsed = ouncesParsed.substring(0,ouncesParsed.length() - 1);
            glassOunces = mOuncesMap.get(ouncesParsed);
            if (glassOunces == null) {
                Log.v(TAG, ">>>> mOuncesMap did not have entry for [" + ouncesParsed + "] - The line was ["  + mLinea + "]"); //<<<<<< Enable this output to tune the contents of the mOuncesLookup table.
                glassOunces = "";
            }
        }
        Log.v(TAG, ">>NEW ARRIVALS>>>>>>>>> :" + mLinea + ":" + glassOunces + ":" + DF.format(priceNumber) + ":" + mLineb + ":" + mPreviousLine);
        return new String[]{beerName, glassOunces, DF.format(priceNumber),""};
    }

    /**
     * This method returns the OCR'd name (often wrong), along with the glass size.
     * If the glass size is not found, this method returns null.
     * @return String array with OCR'd beer name, price, glass size, contents of the row above the beer name (for two-line beer names...uncommon).
     */
    public String[] parseLinePair() {
        if (mLinea == null || mLinea.length() < 7) return null; // the text beer description must be 7 or more in length.
        if (mLineb == null || mLineb.indexOf("/") < 0 || mLineb.endsWith("/")) return null; // the data line must have a slash in it.
        mPreviousLine = ((mPreviousLine.length() < 10)?"":mPreviousLine);  // Set mPreviousLine to empty string if it's not going to be helpful

        int slashLoc = mLineb.indexOf("/");
        int spaceAfterSlashLoc = mLineb.indexOf(" ", slashLoc);
        Log.v(TAG, "spaceAfterSlashLoc " + spaceAfterSlashLoc + ", slashLoc " + slashLoc + " mLineb [" + mLineb + "]");
        if ((spaceAfterSlashLoc - slashLoc) == 1) {
            mLineb = mLineb.replace("/ ","/");
            spaceAfterSlashLoc = mLineb.indexOf(" ", slashLoc);
        }
        if ((spaceAfterSlashLoc - slashLoc) < 2) return null; // if there is no space following the slash, can't parse the glass size.

        String ouncesParsed = mLineb.substring(slashLoc + 1, spaceAfterSlashLoc);
        String glassOunces = mOuncesMap.get(ouncesParsed);
        Log.v(TAG, "ouncesParsed #" + ouncesParsed + "# glassOunces #" + glassOunces + "#"); //<<<<<< Enable this output to tune the contents of the mOuncesLookup table.
        if (glassOunces == null) return null;

        float priceNumber = -1f;
        String price = getPriceFromLine(mLineb.substring(0, slashLoc));
        if (price != null) {
            priceNumber = Float.parseFloat(price);
        } else {
            Log.v(TAG, "***********>>>>>>>> Good ounces, but not able to get price!!+++++ " + priceNumber + " ocr:" + mLineb + "   on this beer: " + mLinea);
            return null;
        }

        // If the first word in the mLinea is in our brewery name map, then replace that word.
        String[] splitString = mLinea.split(" ", 2);
        if (mBreweryNameMap.containsKey(splitString[0])) {
            mLinea = mBreweryNameMap.get(splitString[0]) + " " + splitString[1];
        }

        Log.v(TAG, ">>>>>>>>>>> :" + mLinea + ":" + glassOunces + ":" + DF.format(priceNumber) + ":" + mLineb + ":" + mPreviousLine);
        return new String[]{mLinea, glassOunces, DF.format(priceNumber), mPreviousLine};
    }

    private String getPriceFromLine(String price) {
        Log.v(TAG, "Starting with: [" + price + "]");
        // OCR often reads "5" as "s" or "S", so fix that first
        if (price.startsWith("s.") || price.startsWith("S.")) {
            price = "5." + price.substring(2);
        }

        if ((price.startsWith("$") || price.startsWith("S") || price.startsWith("s")) && price.length() > 1) { //ie  "$5.95" or "$8" or "s6.50"
            price = price.substring(1);
        }

        if (price.contains("o") || price.contains("O")){
            price = price.replaceAll("o", "0").replaceAll("O","0");
        }
        if (price.contains("s") || price.contains("S")){
            price = price.replaceAll("s", "5").replaceAll("S","5");
        }

        float priceNumber = -1;
        try {
            priceNumber = Float.parseFloat(price);
            if (priceNumber > 2f && priceNumber < 15f) {
                price = DF.format(priceNumber);
            } else {
                price = null;
            }
        } catch (Throwable t) {
            Log.v(TAG, "***********>>>>>>>> Not able to get price!!!!!!! " + priceNumber);
            price = null;
        }

        if (price == null && priceNumber > 300f) { // Missed decimal point?
            priceNumber = priceNumber / 100f;
            if (priceNumber > 2f && priceNumber < 15f) {
                price = DF.format(priceNumber);
            } else {
                price = null;
            }
        }
        return price;
    }

    private void loadIfNeeded() {
        // Load the ounces table (one time)
        if (mOuncesLookup != null) {
            mOuncesMap = new HashMap<>(20);
            for (int i = 0; i < mOuncesLookup[0].length; i++){
                //Log.v(TAG, "putting " + mOuncesLookup[0][i] + ":" + mOuncesLookup[1][i]);
                mOuncesMap.put(mOuncesLookup[0][i], mOuncesLookup[1][i]);
            }
            mOuncesLookup = null;
        }

        // Load the brewery name table (one time)
        if (mBreweryNameLookup != null) {
            mBreweryNameMap = new HashMap<>(20);
            for (int i = 0; i < mBreweryNameLookup[0].length; i++){
                mBreweryNameMap.put(mBreweryNameLookup[0][i], mBreweryNameLookup[1][i]);
            }
            mBreweryNameLookup = null;
        }
    }

}
