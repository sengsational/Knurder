/*
 * Created on Aug 6, 2022
 *
 */
package com.sengsational.ocrreader;

import com.sengsational.ocrreader.camera.Levenshtein;

import java.util.ArrayList;
import java.util.Arrays;

public class MatchComparer {
    
    private final MatchItem saucerItem;
    private final MatchItem untappdItem;
    private String cleanedSaucer;
    private String cleanedUntappd;
    private String lastTechnique = "";

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
