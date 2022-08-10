package com.sengsational.ocrreader;
/*
 * Created on Aug 6, 2022
 *
 */

import java.text.Normalizer;
import java.util.Map;
import java.util.regex.Pattern;

public class MatchItem {

    private String source = "";
    private String beer = "";
    private String brewery = "";
    private String beerNumber = "";
    private String breweryNumber = "";
    private String ounces;
    private String price;
    private String abv;
    private String storeNumber = "";
    private String breweryCleaned = "";
    private String upperUniform;
    private String nonStyleUniform;
    private static final Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");


    private static final String[] BREWERY_CLEANING = {" Brewing Co. (Utah, Colorado)"," Brewing & Distilling Co.","  & Sons Brewing Company", " Brew's Brewing Company"," Brewers Cooperative"," Brewing Cooperative",
            " Brewing  Company"," Brewing Company","  & Sons Brewery", "Brewing Project","Privat-Brauerei "," de Brandandere"," Brewing Co-Op"," Hard Cider Co.", " Craft Brewery"," Hard Kombucha", "Brouwerij De "," Artisan Ales"," Beer Company", " Brewing Co."," Brewing Co","Hard Cider",
            " Ale Works"," Beerworks"," Salisbury","Bières de ","Brasserie "," Brau-haus"," Brewery -","Brouwerij "," Beer Co.", " Cider Co","Brauerei "," Beer Co"," Brew Co"," Brewery","Brewery "," Brewing"," Company"," & Sohn"," Cidery",
            " Brews"," & Sons", " & Son"," &Sons", " &Son"," Co-op"," Ales"," Beer "," Brau"," COOP"," Co."," LTD","The "};

    private static final String[] STYLE_REMOVE = {"IMPERIAL HAZY IPA", "BEER COMPANY", "WEST COAST IPA", "HEFEWEIZEN", "HARD CIDER","HEFE WEISS","IMPERIAL IPA", "HEFE WEIZEN", "SESSION SOUR","SESSION IPA","BEER CO.","BEER CO", "QUADRUPEL","TRIPEL", "BREWING", "HAZY PALE ALE", "IRISH ALE", "HAZY IPA", "PALE ALE", "PILSNER","PORTER", "KOLSCH", "LAGER", "SOUR", "CIDER","BEER ","HAZY","COOP", "GOSE", "QUAD", "HEFE", "PILS", "ALE", "IPA"};


    public MatchItem(Map<String, String> map) {
        //"Source", "Beer",  "Brewery", "BeerNumber","BreweryOrStore"
        this.source = map.get("Source");
        this.beer = map.get("Beer");
        if (this.beer == null) this.beer = "";
        this.brewery = map.get("Brewery");
        if (this.brewery == null) this.brewery = "";
        this.beerNumber = map.get("BeerNumber");
        if ("UNTAPPD".equals(source)) {
            this.breweryNumber =  map.get("BreweryOrStore");
        } else {
            this.storeNumber = map.get("BreweryOrStore");
        }
        setCleanedBrewery();
    }

    public MatchItem(String[] saucerValues, String storeNumber) {
    //            csvBuf.append("#\"").append("SAUCER").append("\",\"").append(values[0]).append("\",\"").append(values[1]).append("\",\"").append(values[2]).append("\",\"").append(mStoreNumber).append("\"");
        this.source = "SAUCER";
        this.beer = saucerValues[0];
        this.brewery = saucerValues[1];
        this.beerNumber = saucerValues[2];
        this.storeNumber = storeNumber;
        setCleanedBrewery();
    }

    public MatchItem(String[] matchingValues) {
        this.source = "UNTAPPD";
        this.beer = matchingValues[0];
        this.brewery = matchingValues[1];
        this.beerNumber = matchingValues[2];
        this.breweryNumber = matchingValues[3];
        this.ounces = matchingValues[4];
        this.price = matchingValues[5];
        this.abv = matchingValues[6];
        setCleanedBrewery();
    }

    private void setCleanedBrewery() {
        if (brewery == null) return;
        breweryCleaned = brewery;
        //if (breweryCleaned.contains("Oud")) System.out.println("1[" + breweryCleaned + "]");
        for (int i = 0; i < BREWERY_CLEANING.length; i++) {
            int preSize = breweryCleaned.length();
            breweryCleaned = breweryCleaned.replace(BREWERY_CLEANING[i],"");
            //if (preSize != breweryCleaned.length() && breweryCleaned.contains("Rahr")) System.out.println(">>> " + BREWERY_CLEANING[i]);
        }
        //if (breweryCleaned.contains("Rahr")) System.out.println("2[" + breweryCleaned + "]");
        breweryCleaned = breweryCleaned.trim();
        breweryCleaned = breweryCleaned.replace("'s", "s");
        //if (breweryCleaned.contains("Rahr")) System.out.println("3[" + breweryCleaned + "]");
    }

    public String getBreweryCleaned() {
        return breweryCleaned;
    }

    public boolean isSaucer() {
        return "SAUCER".equals(source);
    }

    public boolean isUntappd() {
        return "UNTAPPD".equals(source);
    }

    public String getExactTextMatch() {
        if (isSaucer()) {
            upperUniform = deAccent(beer).toUpperCase().replaceAll("-"," ").replaceAll("[^A-Z0-9 ]+", "").trim().replaceAll(" +", " ");;
        }   else {
            upperUniform = deAccent(breweryCleaned + " " + beer).toUpperCase().replaceAll("-"," ").replaceAll("[^A-Z0-9 ]+", "").trim().replaceAll(" +", " ");;
        }
        return upperUniform;
    }

    public String getNonStyleTextMatch() {
        if (upperUniform == null) getExactTextMatch();
        String nonStyleLocal = upperUniform;
        for (int i = 0; i < STYLE_REMOVE.length; i++) {
            nonStyleLocal = nonStyleLocal.replace(STYLE_REMOVE[i],"").trim().replaceAll(" +", " ");;
        }
        // Don't remove the style if it's going to shrink down to just the brewery name.
        if (brewery != null) {
            int extraLengthBeyondBrewery = nonStyleLocal.length() - brewery.length();
            if (extraLengthBeyondBrewery < 3) {
                nonStyleUniform = upperUniform;
                return nonStyleUniform;
            }
        }
        nonStyleUniform = nonStyleLocal;
        return nonStyleUniform;
    }

    private static String deAccent(String str) {
        str = str.replace("æ", "AE"); //Rubæus!!
        String nfdNormalizedString = Normalizer.normalize(str, Normalizer.Form.NFD);
        return pattern.matcher(nfdNormalizedString).replaceAll("");
    }

    public String getName() {
        return beer;
    }

    public String getOriginal() {
        if (nonStyleUniform == null) getNonStyleTextMatch();
        return "[" + source + ", " + beer + ", " + brewery + "{" + nonStyleUniform +"}]";
    }

    public String[] matchFieldArray(String saucerName) {
        return new String[] {saucerName, ounces, price, abv, beerNumber, breweryNumber};
    }

    public String toString() {
        return source + ", " + beer + ", "+ brewery + ", " + beerNumber + ", " + breweryNumber + ", " + storeNumber + ", " + breweryCleaned;
    }

}
