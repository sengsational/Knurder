/*
 * Created on Aug 6, 2022
 *
 */
package com.sengsational.ocrreader;

import com.sengsational.ocrreader.camera.Levenshtein;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;

public class MatchComparer {
    
    private final MatchItem saucerItem;
    private final MatchItem untappdItem;
    private String cleanedSaucer;
    private String cleanedUntappd;
    private String lastTechnique = "";

    private static final TreeMap<String, String> HARD_MATCH_MAP = new TreeMap<>();
    private static final String[][] hardMatchArray =
            {   {"WELLS BANANA BREAD","BANANA BREAD BEER"},
                {"BIRDSONG WAKE UP PORTER","WAKE UP COFFEE"},
                {"TERRAPIN WHITE CHOCOLATE MOOHOO","WHITE CHOCOLATE MOO HOO MILK STOUT"},
                {"GUINNESS DRAUGHT","DRAUGHT NITRO"},
                {"SIERRA NEVADA CELEBRATION","CELEBRATION FRESH HOP 2023"},
                {"FOUNDERS RUBAEUS NITRO","RUBAEUS RASPBERRY NITRO"},
                {"WICKED WEED MILK COOKIES","MILK COOKIES IMPERIAL MILK STOUT"},
                {"SMITHWICKS ALE","IRISH ALE"}};
    {
        for (String[] strings : hardMatchArray) {
            HARD_MATCH_MAP.put(strings[0], strings[1]);
        }
    }

    public MatchComparer(MatchItem saucerItem, MatchItem untappdItem) {
        this.saucerItem = saucerItem;
        this.untappdItem = untappdItem;
    }

    public boolean isExactMatch() {
        cleanedSaucer = saucerItem.getExactTextMatch();
        cleanedUntappd = untappdItem.getExactTextMatch();
        lastTechnique = "EXACT";
        return cleanedSaucer.equals(cleanedUntappd);
    }

    public boolean isNonStyleMatch() {
        if (cleanedSaucer == null || cleanedUntappd == null) isExactMatch();
        cleanedSaucer = saucerItem.getNonStyleTextMatch();
        cleanedUntappd = untappdItem.getNonStyleTextMatch();
        lastTechnique = "STYLE_REMOVED";
        return cleanedSaucer.equals(cleanedUntappd);
    }

    public boolean isFullyContained() {
        if (cleanedSaucer == null || cleanedUntappd == null) isNonStyleMatch();
        lastTechnique = "CONTAINED_ALL";
        if (cleanedSaucer.contains(cleanedUntappd) || cleanedUntappd.contains(cleanedSaucer)) return true;
        else return hasAllWords(cleanedSaucer, cleanedUntappd);
    }
    public boolean isHardMatch() {
        if (cleanedSaucer == null || cleanedUntappd == null) return false;
        lastTechnique = "HARD_MATCH";
        String untappdLookupMatch = HARD_MATCH_MAP.get(cleanedSaucer);
        if (untappdLookupMatch != null && cleanedUntappd.equals(untappdLookupMatch)) return true;
        else return false;
    }

    private static boolean hasAllWords(String stringOne, String stringTwo) {
        ArrayList<String> listOne= new ArrayList<String>(Arrays.asList(stringOne.split(" ")));
        ArrayList<String> listTwo= new ArrayList<String>(Arrays.asList(stringTwo.split(" ")));
        boolean allFound = true;
        for (String word : listOne) {
            if (!listTwo.contains(word)) {
                allFound = false;
                break;
            }
        }
        if (allFound) return true;
        allFound = true;
        for (String word : listTwo) {
            if (!listOne.contains(word)) {
                allFound = false;
                break;
            }
        }
        return allFound;
    }

    public boolean isFuzzyMatch(float minValue) {
        double value = Levenshtein.compare(cleanedSaucer, cleanedUntappd);
        lastTechnique = "FUZZY_" + Math.round(value * 100D)/100D;
        return value > minValue;
    }
    
    public double getFuzzyMatchScore() {
        double value = Levenshtein.compare(cleanedSaucer, cleanedUntappd);
        lastTechnique = "FUZZY_" + Math.round(value * 100D)/100D;
        return value;
    }

    public String getLastTechnique() {
        return lastTechnique;
    }
 
    public MatchItem getUntappdItem() {
        return this.untappdItem;
    }

    public MatchItem getSaucerItem() {
        return this.saucerItem;
    }

}
