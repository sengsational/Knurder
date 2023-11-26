/*
 * Created on Aug 6, 2022
 *
 */
package com.sengsational.ocrreader;

import android.util.Log;

import com.sengsational.knurder.ConcurrentHashSet;
import com.sengsational.knurder.UntappdItem;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Once populated, this class contains the list of saucer match items and the list of untappd match items.
 * As matches are found, each match is removed from both lists.
 */
public class MatchGroup {

    private final ArrayList<MatchItem> mSaucerMatchItems = new ArrayList<>();
    private final ArrayList<MatchItem> mUntappdMatchItems = new ArrayList<>();
    private final ConcurrentHashSet<String[]> mFoundResults = new ConcurrentHashSet<>(100);
    private static final String TAG = MatchGroup.class.getSimpleName();

    public MatchGroup load(List<String[]> allTapsNames, String storeNumber) {
        for (int i = 0; i < allTapsNames.size(); i++){
            mSaucerMatchItems.add(new MatchItem(allTapsNames.get(i), storeNumber));
        }
        return this;
    }

    public MatchGroup load(ArrayList<UntappdItem> untappdItems) {
        for (UntappdItem item: untappdItems) {
            mUntappdMatchItems.add(new MatchItem(item.getMatchingValues()));
        }
        return this;
    }

    ConcurrentHashSet<String[]> match() {
        ArrayList<MatchItem> saucerItemsFound = new ArrayList<>();
        ArrayList<MatchItem> untappdItemsFound = new ArrayList<>();

        // Visit each saucer item one time
        Iterator<MatchItem> itemsIterator = mSaucerMatchItems.iterator();
        while (itemsIterator.hasNext()) {
            MatchItem saucerItem = itemsIterator.next();
            Iterator<MatchItem> matchIterator = mUntappdMatchItems.iterator();
            ArrayList<MatchComparer> fuzzyFoundList = new ArrayList<>();
            MatchComparer comparer;
            boolean found = false;
            // Try to match every untappd item to our current saucer item
            while (matchIterator.hasNext()) {
                MatchItem untappdItem = matchIterator.next();
                comparer = new MatchComparer(saucerItem, untappdItem);
                if (comparer.isExactMatch() || comparer.isNonStyleMatch()) {
                    saucerItemsFound.add(saucerItem);
                    untappdItemsFound.add(untappdItem);
                    //System.out.println("MatchGroup.match() Found match " + comparer.getLastTechnique() + " : " +  saucerItem.getOriginal() + " <> " + untappdItem.getOriginal());
                    mFoundResults.add(untappdItem.matchFieldArray(saucerItem.getName()));
                    mUntappdMatchItems.remove(untappdItem);
                    found = true;
                    break;
                }
            }
            
            if (found) continue;
            
            // try iterating once more with fully contained;
            // the reason we don't do this above is that it could fail on exact match and hit on fully contained when a later one would catch on exact match.
            matchIterator = mUntappdMatchItems.iterator();
            while (matchIterator.hasNext()) {
                MatchItem untappdItem = matchIterator.next();
                comparer = new MatchComparer(saucerItem, untappdItem);
                if (comparer.isFullyContained() || comparer.isHardMatch()) {
                    saucerItemsFound.add(saucerItem);
                    untappdItemsFound.add(untappdItem);
                    //System.out.println("MatchGroup.match() Found match " + comparer.getLastTechnique() + " : " +  saucerItem.getOriginal() + " <> " + untappdItem.getOriginal());
                    mFoundResults.add(untappdItem.matchFieldArray(saucerItem.getName()));
                    mUntappdMatchItems.remove(untappdItem);
                    found = true;
                    break;
                }
                if (!found && comparer.isFuzzyMatch(0.7f)) {
                    MatchComparer savedComparer = new MatchComparer(saucerItem, untappdItem);
                    savedComparer.isNonStyleMatch(); // Populates fields
                    fuzzyFoundList.add(savedComparer);
                }
            }
            
            // ^We have looped all of the untappd items against this saucer item, twice

            if (!found && fuzzyFoundList.size() > 0) {
                // we have not found a matching untappd item for this saucer item, but we DO have one or more fuzzy matches.  We want to take the best fuzzy match.
                MatchComparer highestMatchComparer = null;
                double highestScore = 0.0F;
                for (MatchComparer fuzzyComparer : fuzzyFoundList) {
                    double score = fuzzyComparer.getFuzzyMatchScore();
                    if (score > highestScore) {
                        highestMatchComparer = fuzzyComparer;
                        highestScore =score;
                    }
                }
                if (highestMatchComparer != null) {
                    saucerItemsFound.add(saucerItem);
                    untappdItemsFound.add(highestMatchComparer.getUntappdItem());
                    //System.out.println("MatchGroup.match() Found fuzzy match " + highestMatchComparer.getLastTechnique() + " : " +  saucerItem + " <> " + highestMatchComparer.getUntappdItem());
                    mFoundResults.add(highestMatchComparer.getUntappdItem().matchFieldArray(saucerItem.getName()));
                    mUntappdMatchItems.remove(highestMatchComparer.getUntappdItem());
                }
            }
        }
        
        // Remove the items we found 
        itemsIterator = saucerItemsFound.iterator();
        while (itemsIterator.hasNext()) {
            mSaucerMatchItems.remove(itemsIterator.next());
        }

        itemsIterator = untappdItemsFound.iterator();
        while (itemsIterator.hasNext()) {
            mUntappdMatchItems.remove(itemsIterator.next());
        }
        return mFoundResults;
    }

    ArrayList<MatchItem> getLeftoverSaucer() {
        Log.v(TAG, "There were " + this.mSaucerMatchItems.size() + " saucer items not matched.");
        return this.mSaucerMatchItems;
    }
    ArrayList<MatchItem> getLeftoverUntappd() {
        Log.v(TAG, "There were " + this.mUntappdMatchItems.size() + " intappd items not matched.");
        return this.mUntappdMatchItems;
    }

    /*  BELOW IS FOR OFFLINE VALIDATION AND TESTING

    private String mFileName;

    public MatchGroup(String fileName) {
        this.mFileName = fileName;
    }

    public static void main(String[] args) throws Exception {
       //String fileName = "CharlotteMatching.csv";
       //String fileName= "CordovaMatching.csv";
       //String fileName = "ForWorthMatching.csv";
       //String fileName = "HoustonMatching.csv";
       //String fileName = "LittleRockMatching.csv";
       //String fileName = "RaleighMatching.csv";
       String fileName = "SanAntonioMatching.csv";

        MatchGroup group = new MatchGroup(fileName);
        group.load();
        group.match();
        
        group.printLeftoverSaucer();
        group.printLeftoverUntappd();

        boolean examineOutput = false;
        if (examineOutput) {
            TreeSet<String> cleanBrewerySet = new TreeSet<String>();
            Iterator itemsIterator = group.mSaucerItems.iterator();
            boolean allOut = false;
            while (itemsIterator.hasNext()) {
                if (allOut) {
                    System.out.println("Matchgroup.main(): " + itemsIterator.next());
                } else {
                    MatchItem item = (MatchItem)itemsIterator.next();
                    cleanBrewerySet.add(item.getBreweryCleaned());
                }
            }
            itemsIterator = group.mUntappdItems.iterator();
            while (itemsIterator.hasNext()) {
                if (allOut) {
                    System.out.println("Matchgroup.main(): " + itemsIterator.next());
                } else {
                    MatchItem item = (MatchItem)itemsIterator.next();
                    cleanBrewerySet.add(item.getBreweryCleaned());
                }
            }
            System.out.println("MatchGroup.main(): " + cleanBrewerySet);
        }
    }

    public void load() throws Exception {
        FileReader in = new FileReader(mFileName);
        CSVReader reader = new CSVReader(in);
        reader.readHeader();

        Map<String, String> map;
        while ((map = reader.readValues()) != null) {
            MatchItem item = new MatchItem(map);
            if (item.isSaucer()) {
                mSaucerItems.add(item);
            } else if (item.isUntappd()) {
                mUntappdItems.add(item);
            } else {
                System.out.println("Unknown source: " + map);
            }
        }
        reader.close();
    }

    private void printLeftoverSaucer() {
        Iterator<MatchItem> itemsIterator = mSaucerItems.iterator();
        while (itemsIterator.hasNext()) {
            MatchItem item = itemsIterator.next();
            System.out.println("printLeftover(): \"" + item.getNonStyleTextMatch() + "\", " + item);
        }
    }

    private void printLeftoverUntappd() {
        Iterator<MatchItem> itemsIterator = mUntappdItems.iterator();
        while (itemsIterator.hasNext()) {
            MatchItem item = itemsIterator.next();
            System.out.println("printLeftover(): \"" + item.getNonStyleTextMatch() + "\", " + item);
        }
    }


    */
}
