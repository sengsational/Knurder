package com.sengsational.knurder;

import com.sengsational.ocrreader.OcrScanHelper;

public class UntappdItem {
    private final String beerName;
    private final String breweryName;
    private final String ounces;
    private final String price;
    private final String abv; // Looks like "4.7% ABV"
    private final String beerNumber; // confirmed integer or ""
    private final String breweryNumber; // confirmed integer or ""

    public UntappdItem(String beerName, String breweryName, String ounces, String price, String abv, String beerNumber, String breweryNumber) {
        this.beerName = beerName;
        this.breweryName = breweryName;
        this.ounces = ounces;
        this.price = price;
        this.abv = abv;
        this.beerNumber = beerNumber;
        this.breweryNumber = breweryNumber;
    }
    public String getCleanedBreweryName() {
        return OcrScanHelper.breweryTextCleanup(breweryName);
    }

    public String getBeerName() {
        return beerName;
    }

    public String getBreweryPrefixedName() {
        return getCleanedBreweryName() + " " + beerName;
    }

    public String getOuncesNumber() {
        return ounces.replaceAll("[^0-9.]", "");
    }

    public String getPriceNumber() {
        return price.replaceAll("[^0-9.]", "");
    }

    public String getAbvNumber() {
        String abvCleaned = abv.replace("%","");
        return abvCleaned.replace("ABV","").trim();
    }

    public String getBeerNumber() {
        return this.beerNumber;
    }

    public String getBreweryNumber() {
        return this.breweryNumber;
    }

    public String toString() {
        return breweryName + ", " + beerName + ", " + getOuncesNumber() + ", " + getPriceNumber() + ", " + getAbvNumber() + ", " + beerNumber + ", " + breweryNumber;
    }

    public String[] getMatchingValues() {
        return new String[]{beerName, breweryName, beerNumber, breweryNumber, getOuncesNumber(), getPriceNumber(), getAbvNumber()};
    }

    public String getMatchingCsv() {
        StringBuffer buf = new StringBuffer();
        buf.append("\"").append(beerName).append("\",");
        buf.append("\"").append(breweryName).append("\",");
        buf.append("\"").append(beerNumber).append("\",");
        buf.append("\"").append(breweryNumber).append("\"");
        return buf.toString();
    }
}

