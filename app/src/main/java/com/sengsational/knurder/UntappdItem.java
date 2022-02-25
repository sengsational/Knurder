package com.sengsational.knurder;

import com.sengsational.ocrreader.OcrScanHelper;

public class UntappdItem {
    private final String beerName;
    private final String breweryName;
    private final String ounces;
    private final String price;

    public UntappdItem(String beerName, String breweryName, String ounces, String price) {
        this.beerName = beerName;
        this.breweryName = breweryName;
        this.ounces = ounces;
        this.price = price;
    }

    public String getBreweryPrefixedName() {
        return OcrScanHelper.breweryTextCleanup(breweryName) + " " + beerName;
    }

    public String getOuncesNumber() {
        return ounces.replace("oz","").trim();
    }

    public String getPriceNumber() {
        return price.replace("$","").trim();
    }

    public String toString() {
        return breweryName + ", " + beerName + ", " + ounces + ", " + price;
    }
}

