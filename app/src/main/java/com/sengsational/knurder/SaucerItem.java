package com.sengsational.knurder;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.jsoup.Jsoup;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

import javax.microedition.khronos.opengles.GL;

import static com.sengsational.knurder.StoreNameHelper.getStoreIdsForState;

/**
 * Created by Owner on 5/13/2016.
 */
public class SaucerItem {
    private static final String TAG = SaucerItem.class.getSimpleName();
    public static final long FOUR_HOURS = 14400000L;

    String mRawInputString;
    String mStoreName;   //set during database population and used in parsing city
    boolean mOverrideTap = false;   //DRS 20160823 - Improve data quality if (CAN) or (BTL) is in the description
    boolean mOverrideFlight = false;
    boolean mOverrideMix = false;
    public static final String[] BREWERY_CLEANUP = {"Winery & Distillery","Beverage Associates","der Trappisten van","Brewing Company","Artisanal Ales","Hard Cider Co.","& Co. Brewing","Craft Brewery","Beer Company","Gosebrauerei","Brasserie d'","and Company","Cooperative","Brewing Co.","Brewing Co","& Son Co.","Brasserir","Brasserie","Brasseurs","Brau-haus","Brouwerji","Brauerei","BrewWorks","Breweries","Brouwerj","and Co.","Brewery","Brewing","Beer Co","Company","& Sohn","(Palm)","and Co","Cidery","& Sons","Beers","& Son","Ales","Brau","GmbH","Co.","Ltd","LTD","& co"};

    SaucerItem saucerItem;    //this object

    static final SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");
    static final SimpleDateFormat ndf = new SimpleDateFormat("yyyy MM dd");
    static final SimpleDateFormat qdf = new SimpleDateFormat("yyyy MM dd HH mm");

    public SaucerItem() {
        setId(0L);
    }

    public SaucerItem(Cursor cursor) {
        populate(cursor);
    }

    public void setStoreName(String storeName) {
        mStoreName = storeName;
    }

    public void setStoreNameAndNumber(String storeNumber, SQLiteDatabase db) {
        store_id = storeNumber;
        setStoreName(StoreNameHelper.getInstance().getStoreNameFromNumber(storeNumber, db));
    }

    /**********************plain gets and sets **********************/
    public Long getId() {  return _id; }
    public String getIdString() {
        if (_id != null) return Long.toString(_id);
        else return "-1";
    }

    public void setId(Long id) {_id = id;}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = unescapeJavaString(name).trim();
        try {
            if (this.name.contains("(CAN)") || this.name.contains("(BTL")) mOverrideTap = true; //DRS 20160823 - Improve data quality
            else mOverrideTap = false;
        } catch (Throwable t) {}
        try {
            String alphabeticOnly = this.name.replaceAll("[^a-zA-Z]", "").toLowerCase();
            if (alphabeticOnly.endsWith("flight")) mOverrideFlight = true; //DRS 20161201 - Improve data quality
            else if (alphabeticOnly.contains("new arrivals")) mOverrideFlight = true; // DRS 20190329 - Improve data quality
            else mOverrideFlight = false;
        } catch (Throwable t) {}
        try {
            if (this.name.endsWith("Float")) mOverrideMix = true; // DRS 20161208 - Improve data quality
            else mOverrideMix = false;
        } catch (Throwable t) {}
    }

    public String getStore_id() {
        return store_id;
    }

    public void setStore_id(String store_id) {
        this.store_id = store_id;
    }

    public String getBrew_id() {
        return brew_id;
    }

    public void setBrew_id(String brew_id) {
        this.brew_id = brew_id;
    }

    public String getBrewer() {
        return brewer;
    }

    public String getCleanBrewer() {
        if (brewer == null) return null;
        if (brewer.equals(name)) return name.substring(0, name.indexOf(" ")).trim();
        String cleanBrewer = brewer;
        for (String match : BREWERY_CLEANUP) {
            int matchLoc = brewer.indexOf(match);
            if ( matchLoc == 0) {
                cleanBrewer = cleanBrewer.substring(match.length()).trim();
                break;
            } else if (matchLoc > 0) {
                // "Highland Brewing Company" - match loc = 9
                cleanBrewer = cleanBrewer.substring(0, matchLoc).trim();
                break;
            }
        }
        return cleanBrewer;
    }

    public void setBrewer(String brewer) {
        this.brewer = brewer;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        String saveCity = city;
        if (null != city && !"null".equals(city)){
            city = city.trim();
            if (city.endsWith(",") || city.endsWith(".")) {
                city = city.substring(0,city.length()-1);
            }
            this.city = city;
            setIsLocal(city);
        } else {
            this.city = "";
        }
        //Log.v(TAG, "setCity starting [" + saveCity + "] now [" + this.city + "]");
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        if(country!=null && !country.trim().equals("UnitedStates") && !country.trim().equals("United States") && !country.trim().equals("USA") && !country.trim().equals("None")) {
            this.isImport = "T";
        } else {
            this.isImport = "F";
        }
        this.country = country;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
        if (mOverrideTap && "draught".equals(this.container)) this.container = "bottled"; //DRS 20160823 - improve data quality
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        if(style.contains("\\")){
            style = unescapeJavaString(style);
        }
        if ("Brew Fusion".equals(style)) mOverrideMix = true;

        this.style = style;
        if (mOverrideFlight && "draught".equals(this.container)) this.style = "Flight";
        if (mOverrideMix && "draught".equals(this.container)) this.style = "Mix";

    }



    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {

        if(description.contains("\\")){
            description = unescapeJavaString(description);
        }

        // Interpret HTML to text
        description = Jsoup.parse(description).text();

        int badCharLoc = description.indexOf("p>");
        if (badCharLoc > 2) {
            StringBuffer buf = new StringBuffer(description);
            buf.delete(badCharLoc - 2, badCharLoc + 2);
            description = buf.toString();
        }

        this.description = description;

        // Pull ABV from description
        setAbv(description);
    }

    public String getStars() {
        return stars;
    }

    public void setStars(String stars) {this.stars = stars; }

    public String getReviews() {
        return reviews;
    }

    public void setReviews(String reviews) {
        this.reviews = reviews;
    }

    public String getUserReview() { return this.userReview; }

    public void setUserReview(String userReview) {
        if (!"null".equals(userReview) && userReview != null) {
            this.userReview = unescapeJavaString(userReview);
        }
    }

    public String getUserStars() { return userStars;}

    public void setUserStars(String userStars) {
        if (!"null".equals(userStars) && userStars!= null) {
            this.userStars = userStars;
        }
    }


    public String getReviewId() { return this.reviewId;}

    public void setReviewId(String reviewId) { this.reviewId = reviewId; }

    public String getReviewFlag() {return this.reviewFlag;}

    public void setReviewFlag(String reviewFlag) {this.reviewFlag = reviewFlag;}

    public String getTimestamp() { return this.timestamp;}

    public void setTimestamp(String timestamp) { this.timestamp = timestamp;}

    public void setBrew_plate(String brew_plate) {this.brew_plate = brew_plate;}  // Used in loading tasted. Not in database

    public void setUser_plate(String user_plate) {this.user_plate = user_plate;}  // Used in loading tasted. Not in database

    public boolean isOnCurrentPlate() { // Used in loading tasted.
        // user_plate is their last finished plate
        // brew_plate is the plate they are working on
        // if brew_plate is greater than user_plate, the beer is on their current plate
        try {
            if (Integer.parseInt(brew_plate) > Integer.parseInt(user_plate)) return true;
            else return false;
        } catch (Throwable t) {
            Log.v(TAG, "Problem confirming if the beer was on the current plate. " + brew_plate + " / " + user_plate + " : " + t.getMessage() );
            return true; // default to showing the beer
        }
    }

    public String getCreated() {
        return created;
    }

    public void setCreated(String created) {
        this.created = created;
        setCreatedDate(created);
    }

    public String getCreatedDate() { return createdDate;}

    public void setCreatedDate(String created) {
        Calendar cal = Calendar.getInstance();
        this.createdDate = "1970 01 01";
        try {
            cal.setTime(sdf.parse(created));
            this.createdDate= ndf.format(cal.getTime());
        } catch (Exception e) {}
    }

    public void setAbv(String description){
        this.abv = "0";
        try {
            int oneHundredPercentLoc = description.indexOf("100%");
            if (oneHundredPercentLoc > -1) {
                description = description.replace("100%", "100pct") ;
            }
            int abvLoc = description.toUpperCase().indexOf("ABV");
            this.abv = description.substring(Math.max(0,abvLoc-10), Math.min(abvLoc+3, description.length()));
            int pctLoc = this.abv.indexOf("%");
            if (pctLoc > 2) {
                int blankLoc = this.abv.lastIndexOf(" ", pctLoc-2);
                this.abv = this.abv.substring(blankLoc + 1);
            }
            pctLoc = description.lastIndexOf("%");
            if(pctLoc > -1 && this.abv.length() == 2) {
                this.abv = description.substring(Math.max(0,pctLoc-10), pctLoc + 1);
            }
            if (this.abv.length() == 2) {
                int percentLoc = description.toUpperCase().indexOf("PERCENT");
                if (percentLoc > -1) {
                    this.abv = description.substring(Math.max(0,percentLoc-10), percentLoc + 1);
                }
                if (this.abv.length() == 2) this.abv = "0";
            }

            this.abv = this.abv.replaceAll("[^\\d.]", "");
            if (this.abv.startsWith(".")) this.abv = this.abv.substring(1);
            if (this.abv.trim().length() == 0) this.abv = "0";
            StringBuffer buf = new StringBuffer(description.toUpperCase());

            if (this.abv == "0" || abv.trim().length() == 0) {
                int percentWordLoc = buf.indexOf("PERCENT");
                int abvLoc2 = buf.indexOf("ABV");
                int percentLoc = buf.indexOf("%");

                int locMax = Math.max(percentWordLoc,  Math.max(abvLoc2, percentLoc));
                String chunk = description.substring(Math.max(0,locMax-12),Math.min(locMax+12, description.length()));
                int ibuLoc = chunk.indexOf("IBU");
                if (locMax > 0 && ibuLoc < 0) {
                    String afterTest = chunk.replaceAll("[^\\d.]", "");
                    afterTest = afterTest.replace("."," ").trim();
                    afterTest = afterTest.replace(" ", ".");
                    this.abv = afterTest;
                }
            }
            if (this.abv.equals("0") || abv.trim().length() == 0) {

                int firstKeyLocation = -1;
                if (buf.indexOf("PERCENT") > 0 && buf.indexOf("PERCENT") > firstKeyLocation) firstKeyLocation = buf.indexOf("PERCENT");
                if (buf.indexOf("ABV") > 0 && buf.indexOf("ABV") > firstKeyLocation) firstKeyLocation = buf.indexOf("ABV");
                if (buf.indexOf("%") > 0 && buf.indexOf("%") > firstKeyLocation) firstKeyLocation = buf.indexOf("%");

                if (firstKeyLocation > 0) {
                    String chunk = description.substring(Math.max(0,firstKeyLocation-12),Math.min(firstKeyLocation+12, description.length()));
                    int ibuLoc = chunk.indexOf("IBU");
                    if (ibuLoc < 0) {
                        String afterTest = chunk.replaceAll("[^\\d.]", "");
                        afterTest = afterTest.replace("."," ").trim();
                        afterTest = afterTest.replace(" ", ".");
                        this.abv = afterTest;
                    }
                }
            }
            if(this.abv.equals("0") || abv.trim().length() == 0) {

                abvLoc = description.toUpperCase().indexOf("ALCOHOL-BY-VOLUME");
                String chunk = description.substring(Math.max(0,abvLoc-10), Math.min(abvLoc+25, description.length()));
                pctLoc = chunk.indexOf("%");
                if (pctLoc > 2) {
                    String afterTest = chunk.replaceAll("[^\\d.]", "");
                    afterTest = afterTest.replace("."," ").trim();
                    afterTest = afterTest.replace(" ", ".");
                    this.abv = afterTest;
                }
            }
        } catch (Throwable t) {
            Log.v(TAG, "failed: " + t.getMessage());// let it stay "0"
            t.printStackTrace();
        }

    }

    public static String getPctString(String abv) {
        boolean tryAgain  = false;
        String returnString = abv + "%";
        try {
            if (abv.trim().length() == 0) returnString = "";
            float abvFloat = Float.parseFloat(abv);
            if (abvFloat == 0F) return "";
            if (abvFloat > 25F) return "";
            returnString = new DecimalFormat("##.0").format(abvFloat) + "%";
        } catch (Throwable e) {
            tryAgain = true;
        }
        if (tryAgain) {
            try {
                int firstDot = abv.indexOf(".");
                int lastDot = abv.lastIndexOf(".");
                if (firstDot > 0 && lastDot > 0 && (firstDot < (lastDot-1))) {
                    abv = abv.substring(0, firstDot + 2);
                    float abvFloat = Float.parseFloat(abv);
                    if (abvFloat == 0F) return "";
                    if (abvFloat > 25F) return "";
                    returnString = new DecimalFormat("##.0").format(abvFloat) + "%";
                }
            } catch (Throwable e) {
                //System.out.println("getPctString failed and left " + abv);// accept whatever we've been able to do so far.
            }
        }
        return returnString;
    }


    public String getAbv() {
        return abv;
    }

    public void setIsLocal(String city) {
        // Make sure getInstance is called before setting fields
        //String storeName = prefs.getString("lastStoreName","- Select a value -");
        //Log.v("sengsational", "setIsLocal storeName " + storeName);
        String statesDelim = StoreNameHelper.getInstance().lookupStatesForStoreName(mStoreName);
        //Log.v("sengsational", "setIsLocal statesDelim " + statesDelim);

        this.isLocal = "F";
        if (city != null) {
            if (statesDelim != null){
                String states[] = statesDelim.split(";");
                for (String state : states){
                    //Log.v("sengsational", "this.setIsLocal " + state + " ? " + city);
                    if (city.endsWith(state)) {
                        this.isLocal = "T";
                        break;
                    }
                }
            }
        }
    }

    public String getIsLocal(){
        return this.isLocal;
    }

    public void setActive(Boolean active) {
        this.active = active?"T":"F";
    }
    public void setActive(String active) {
        this.active = active;
    }

    public String getActive() {
        return active;
    }

    public void setTasted(boolean tasted) {
        this.tasted = tasted?"T":"F";
    }
    public void setTasted(String tasted) {
        this.tasted = tasted;
    }

    public String getTasted() {
        return tasted;
    }

    public void setHighlighted(boolean highlighted) {
        this.highlighted = highlighted?"T":"F";
    }
    public void setHighlighted(String highlighted) {
        this.highlighted = highlighted;
    }

    public String getHighlighted() {
        return highlighted;
    }

    public void setNewArrival(boolean newArrival) {
        this.newArrival = newArrival?"T":"F";
    }
    public void setNewArrival(String newArrival) {
        this.newArrival = newArrival;
    }

    public String getNewArrival() { return newArrival; }

    public String getIsImport() { return isImport;}

    //DRS 20171128 - Menu scan
    public String getGlassSize() {return glassSize;}
    public void setGlassSize(String glassSize) {this.glassSize = glassSize;}

    //DRS 20171128 - Menu scan
    public String getGlassPrice() {return glassPrice;}
    public void setGlassPrice(String glassPrice) {this.glassPrice = glassPrice;}

    //DRS 20220730 - Untapped Menu scan
    public String getUntappdBeer() {return untappdBeer;}
    public void setUntappdBeer(String untappdBeer) {this.untappdBeer = untappdBeer;}

    //DRS 20220730 - Untapped Menu scan
    public String getUntappdBrewery() {return untappdBrewery;}
    public void setUntappdBrewery(String untappdBrewery) {this.untappdBrewery = untappdBrewery;}

    //DRS 20230323 - Show queued beer accepted
    public String getQueStamp() {return this.queStamp;}
    //public void setQueStamp(String queStamp) {this.queStamp = queStamp;}

    //public void setCurrentlyQueued(boolean currentlyQueued) {
    //    this.currentlyQueued = currentlyQueued?"T":"F";
    //}
    //public void setCurrentlyQueued(String currentlyQueued) {
    //    this.currentlyQueued = currentlyQueued;
    //}

    public String getCurrentlyQueued() { return currentlyQueued; }

    public String getQueText(Context context) {
        Log.v(TAG, "SaucerItem.getQueText() called");
        if (queStamp == null) return "";
        String returnQueText = "";
        try {
            long ageInMs = new Date().getTime() - qdf.parse(queStamp).getTime();
            Log.v(TAG, "ageInMs " + ageInMs + " FOUR_HOURS " + FOUR_HOURS + " queued [" + getCurrentlyQueued() + "] queStamp [" + queStamp + "]");
            if (ageInMs > 0 && ageInMs < FOUR_HOURS) {
                if (getCurrentlyQueued().equals("T")) {
                    returnQueText = "    " + context.getString(R.string.queuedBeerMessage);// [QUEUED]
                } else {
                    returnQueText = "    " + context.getString(R.string.droppedFromQueMessage);// [APPLIED]
                }
            }
        } catch (Throwable t) {
            Log.v(TAG, "unable to parse brews on que timestamp. " + t.getClass().getName() + " " + t.getMessage());
            // not worth crashing
        }
        Log.v(TAG, "SuacerItem.getQueText() returning " + returnQueText);
        return returnQueText;
    }


    public String toString() {
        return getActive() + getTasted() + getHighlighted() + getNewArrival() + ", " +
                getName() + ", " +
                getContainer() + ", " +
                getStore_id() + ", " +
                getBrew_id() + ", " +
                getBrewer() + ", " +
                getCity() + ", " +
                getCountry() + ", " +
                getIsLocal() + ", " +
                getStyle() + ", " +
                getDescription() + ", " +
                getAbv()  + ", " +
                getStars() + ", " +
                getReviews() + ", userReview:" +
                getUserReview() + ", stars:" + // DRS 20181023
                getUserStars() + ", " + // DRS 20181023
                getReviewId() + ", " + // DRS 20181023
                getReviewFlag() + ", " + // DRS 20181023
                getTimestamp() + ", " +  // DRS 20181023
                getCreated() + ", active:" +
                getActive() + ", tasted:" +
                getTasted() + ", highlighted:" +
                getHighlighted() + ", new_arrival:" +
                getNewArrival() + ", glass_size:" +
                getGlassSize() + ", glass_price:" +  //DRS 20171128 - Menu scan
                getGlassPrice() + ", untappd_beer:" +                      //DRS 20171128 - Menu scan
                getUntappdBeer() + ", untappd_brewery:" +  //DRS 20220730
                getUntappdBrewery() + ", que_stamp:" +  //DRS 20220730
                getQueStamp() + ", currently_queued:" +  //DRS 20230323
                getCurrentlyQueued()                     //DRS 20230323
                ;
    }


/* -------------------------------- Loading and parsing --------------------------------------*/


    public void load(String string) {
        // "name":"21st Amendment Back in Black IPA (CAN)","store_id":"13888","brew_id":"7233936","brewer":"21st Amendemnt Brewery","city":"San Francisco","country":"United States","container":"bottled","style":"Black IPA","description":"Back in Black pours a midnight black with a very dark tan head. Brewed like an American IPA, and coming in at 65 IBUs, the addition of rich, dark malts grants this brew has all the flavor and hop character you expect with a smooth, mellow finish.","stars":3,"reviews":"0"
        StringBuffer buf = new StringBuffer(string);
        if (buf.substring(0,2).equals("[{")){
            buf.delete(0,2);
        }
        int starsLoc = buf.indexOf("stars\":");
        buf.insert(starsLoc + 7, "\"");
        buf.insert(starsLoc + 9, "\"");
        mRawInputString = buf.toString();
        parse();
        localize();
    }

    private void localize() {
        if (store_id == null) return;
        if (getStoreIdsForState("NC").contains(store_id)) {
            if (brewer.contains("Sierra Nevada")) {
                setCity("Mills River, NC");
            } else if (brewer.contains("Oskar Blues")) {
                setCity("Brevard, NC");
            }
        } else if(getStoreIdsForState("TX").contains(store_id)){
            if (brewer.contains("Oskar Blues")) {
                setCity("Austin, TX");
            }
        }
    }

    public void parse() {

        //"name":"21st Amendment, Back in Black IPA (CAN)","store_id":"13888"
        //                      ^
        //           can be commas inside the quotes

        if (mRawInputString == null) {
            System.out.println("nothing to parse");
            return;
        }

        //Log.v(TAG, "mRawInputString [" + mRawInputString + "]");
        mRawInputString = mRawInputString.replaceAll("\"\\:null,", "\"\\:\"null\",");
        String[] nvpa = mRawInputString.split("\",\"");
        for (String nvpString : nvpa) {
            String[] nvpItem = nvpString.split("\":\"");
            if (nvpItem.length < 2) continue;
            String identifier = nvpItem[0].replaceAll("\"", "");
            String content = nvpItem[1].replace("\\\"u", "u"); // "backslash quote u" will become "backslash u", so can't have that.
            content = content.replaceAll("\"", ""); // Remove quotes from within the content.  I don't know why we're doing this any more.

            switch (identifier) {
                case "name":
                    setName(content);
                    break;
                case "store_id":
                    setStore_id(content);
                    break;
                case "brew_id":
                    setBrew_id(content);
                    break;
                case "brewer":
                    setBrewer(content);
                    break;
                case "city":
                    setCity(content);
                    break;
                case "country":
                    setCountry(content);
                    break;
                case "container":
                    setContainer(content);
                    break;
                case "style":
                    setStyle(content);
                    break;
                case "description":
                    setDescription(content);
                    break;
                case "stars":
                    setStars(content);
                    break;
                case "reviews":
                    setReviews(content);
                    break;
                case "review":
                    setUserReview(content);  // DRS 20181023
                    break;
                case "uid":
                    break;
                case "review_id":
                    setReviewId(content);  // DRS 20181023
                    break;
                case "created":
                    setCreated(content);
                    break;
                case "user_star":
                    setUserStars(content);  // DRS 20181023
                    break;
                case "brew_plate":
                    setBrew_plate(content);
                    break;
                case "user_plate":
                    setUser_plate(content);
                    break;
                case "time_stamp":
                    setTimestamp(content); // DRS 20181023
                    break;

                default:
                    System.out.println("nowhere to put [" + nvpItem[0] + "] " + nvpString + " raw: " + mRawInputString);
                    break;
            }
        }
    }


    public static String unescapeJavaString(String st) {
        if (st == null) return null;

        StringBuilder sb = new StringBuilder(st.length());
        // [the rain \"In Spain\" is G\u2020one\.] st.length():37
        // [the rain \"In Spain\" is G\u2020one.\] st.length():37
        for (int i = 0; i < st.length(); i++) {
            char ch = st.charAt(i);
            if (ch == '\\') {
                char nextChar = (i == st.length() - 1) ? '\\' : st.charAt(i + 1);
                // Octal escape?
                if (nextChar >= '0' && nextChar <= '7') {
                    String code = "" + nextChar;
                    i++;
                    if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                        code += st.charAt(i + 1);
                        i++;
                        if ((i < st.length() - 1) && st.charAt(i + 1) >= '0' && st.charAt(i + 1) <= '7') {
                            code += st.charAt(i + 1);
                            i++;
                        }
                    }
                    sb.append((char) Integer.parseInt(code, 8));
                    continue;
                }
                switch (nextChar) {
                    case '\\':
                        ch = '\\';
                        break;
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case '\"':
                        ch = '\"';
                        break;
                    case '\'':
                        ch = '\'';
                        break;
                    // Hex Unicode: u????
                    case 'u':
                        if (i >= st.length() - 5) {
                            ch = 'u';
                            break;
                        }
                        String fourChars = "    ";
                        try {
                            fourChars = "" + st.charAt(i + 2) + st.charAt(i + 3)
                                    + st.charAt(i + 4) + st.charAt(i + 5);
                            int code = Integer.parseInt(fourChars, 16);
                            sb.append(Character.toChars(code));
                        } catch (Throwable t) {
                            Log.v("sengsational", "Error parsing unicode string [" + fourChars + "]");
                        }
                        i += 5;
                        continue;
                }
                i++;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /* --------------------- Simple one-per field things ---------------------------------------- */

    public void clear() {
        _id = 0L;
        mRawInputString = null;
        name = null;
        store_id = null;
        brew_id = null;
        brewer = null;
        city = null;
        isLocal = null;
        country = null;
        container = null;
        style = null;
        description = null;
        abv = null;
        stars = null;
        reviews = null;
        created = null;
        createdDate = null;
        newArrival = null;
        isImport = null;

        mOverrideTap = false;
        active = null;
        tasted = null;
        highlighted = null;

        userReview = null;  // DRS 20181023
        userStars = null;  // DRS 20181023
        reviewId = null;  // DRS 20181023
        reviewFlag = null; // DRS 20181023
        timestamp = null; // DRS 20181023

        glassSize = null;  //DRS 20171128 - Menu scan
        glassPrice = null; //DRS 20171128 - Menu scan

        untappdBeer = null;  //DRS 20220730
        untappdBrewery = null; //DRS 20220730

        queStamp = null;  //DRS 20230323
        currentlyQueued = null; //DRS 20230323


    }

    // Normally the only thing that changes (changed by the user) is the highlighted, but we put everything back except null items
    public ContentValues fillModelValues(SaucerItem model){
        ContentValues values = new ContentValues();
        values.put("NAME", model.getName());
        values.put("STORE_ID", model.getStore_id());
        values.put("BREW_ID",  model.getBrew_id());
        values.put("BREWER", model.getBrewer());
        values.put("CITY", model.getCity());
        values.put("IS_LOCAL", model.getIsLocal());
        values.put("COUNTRY", model.getCountry());
        values.put("CONTAINER", model.getContainer());
        values.put("STYLE", model.getStyle());
        values.put("DESCRIPTION", model.getDescription());
        values.put("ABV", model.getAbv());
        values.put("STARS", model.getStars());
        values.put("REVIEWS", model.getReviews());
        values.put("CREATED", model.getCreated());
        values.put("ACTIVE", model.getActive());
        values.put("TASTED", model.getTasted());
        values.put("HIGHLIGHTED", model.getHighlighted());
        values.put("CREATED_DATE", model.getCreatedDate());
        values.put("NEW_ARRIVAL", model.getNewArrival());
        values.put("IS_IMPORT", model.getIsImport());
        values.put("GLASS_SIZE", model.getGlassSize()); //DRS 20171128 - Menu scan
        values.put("GLASS_PRICE", model.getGlassPrice()); //DRS 20171128 - Menu scan
        values.put("USER_REVIEW", model.getUserReview()); //DRS 20181023
        values.put("USER_STARS", model.getUserStars()); //DRS 20181023
        values.put("REVIEW_ID", model.getReviewId()); //DRS 20181023
        values.put("REVIEW_FLAG", model.getReviewFlag()); //DRS 20181023
        values.put("TIMESTAMP", model.getTimestamp()); //DRS 20181023
        values.put("UNTAPPD_BEER", model.getUntappdBeer()); //DRS 20220730
        values.put("UNTAPPD_BREWERY", model.getUntappdBrewery()); //DRS 20220730
        values.put("QUE_STAMP", model.getQueStamp()); //DRS 20230323
        values.put("CURRENTLY_QUEUED", model.getCurrentlyQueued()); //DRS 20230323
        Iterator<String> iterator = values.keySet().iterator();
        ArrayList<String> removeList = new ArrayList<>();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (null == values.get(key)) removeList.add(key);
        }
        for (String removeItem: removeList) {
            values.remove(removeItem);
        }
        return values;

    }

    public ContentValues getContentValues() {
        ContentValues values = new ContentValues();
        if (name != null) values.put("NAME", name);
        if (store_id != null) values.put("STORE_ID", store_id);
        if (brew_id != null) values.put("BREW_ID",  brew_id);
        if (brewer != null) values.put("BREWER", brewer);
        if (city != null) values.put("CITY", city);
        if (isLocal != null) values.put("IS_LOCAL", isLocal);
        if (country != null) values.put("COUNTRY", country);
        if (container != null) values.put("CONTAINER", container);
        if (style != null) values.put("STYLE", style);
        if (description != null) values.put("DESCRIPTION", description);
        if (abv != null) values.put("ABV", abv);
        if (stars != null) values.put("STARS", stars);
        if (reviews != null) values.put("REVIEWS", reviews);
        if (created != null) values.put("CREATED", created);
        if (active != null) values.put("ACTIVE", active);
        if (tasted != null) values.put("TASTED", tasted);
        if (highlighted != null) values.put("HIGHLIGHTED", highlighted);
        if (createdDate != null) values.put("CREATED_DATE", createdDate);
        if (newArrival != null) values.put("NEW_ARRIVAL", newArrival);
        if (isImport != null) values.put("IS_IMPORT", isImport);
        if (glassSize != null) values.put("GLASS_SIZE", glassSize); //DRS 20171128 - Menu scan
        if (glassPrice != null) values.put("GLASS_PRICE", glassPrice); //DRS 20171128 - Menu scan
        if (userReview != null) values.put("USER_REVIEW", userReview); // DRS 20181023
        if (userStars != null) values.put("USER_STARS", userStars); // DRS 20181023
        if (reviewId != null) values.put("REVIEW_ID", reviewId); // DRS 20181023
        if (reviewFlag != null) values.put("REVIEW_FLAG", reviewFlag); // DRS 20181023
        if (timestamp != null) values.put("TIMESTAMP", timestamp); // DRS 20181023
        if (untappdBeer != null) values.put("UNTAPPD_BEER", untappdBeer); //DRS 20220730
        if (untappdBrewery != null) values.put("UNTAPPD_BREWERY", untappdBrewery); //DRS 20220730
        if (queStamp != null) values.put("QUE_STAMP", queStamp); //DRS 20230323
        if (currentlyQueued != null) values.put("CURRENTLY_QUEUED", currentlyQueued); //DRS 20230323
        return values;
    }


    public static String getDatabaseTableCreationCommand() {
        return "CREATE TABLE UFO (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "NAME TEXT, " +
                "STORE_ID TEXT, " +
                "BREW_ID TEXT, " +
                "BREWER TEXT, " +
                "CITY TEXT, " +
                "IS_LOCAL TEXT, " +
                "COUNTRY TEXT, " +
                "CONTAINER TEXT, " +
                "STYLE TEXT, " +
                "DESCRIPTION TEXT, " +
                "ABV TEXT, " +
                "STARS TEXT, " +
                "REVIEWS TEXT, " +
                "CREATED TEXT, " +
                "ACTIVE TEXT, " +
                "TASTED TEXT, " +
                "HIGHLIGHTED TEXT, " +
                "CREATED_DATE TEXT, " +
                "NEW_ARRIVAL TEXT, " +
                "IS_IMPORT TEXT, " +
                "GLASS_SIZE TEXT, " +  //DRS 20171128 - Menu scan
                "GLASS_PRICE TEXT, " + //DRS 20171128 - Menu scan
                "USER_REVIEW TEXT, " + // DRS 20181023
                "USER_STARS TEXT, " + // DRS 20181023
                "REVIEW_ID TEXT, " + // DRS 20181023
                "REVIEW_FLAG TEXT, " + // DRS 20181023
                "TIMESTAMP TEXT, " + // DRS 20181023
                "UNTAPPD_BEER TEXT, " +  // DRS 20220730
                "UNTAPPD_BREWERY TEXT, " +  // DRS 20220730
                "QUE_STAMP TEXT, " +  // DRS 20230323
                "CURRENTLY_QUEUED TEXT" + // DRS 20230323
                ");";
                // NOTE: MUST HAVE "...TEXT, " <<< note comma and space!!
    }

    public static String getDatabaseAppendTableCreationCommand() {
        return "CREATE TABLE UFOLOCAL (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "NAME TEXT, " +
                "STORE_ID TEXT, " +
                "BREW_ID TEXT, " +
                "GLASS_SIZE TEXT, " +
                "GLASS_PRICE TEXT, " +
                "ADDED_NOW_FLAG TEXT, " +
                "LAST_UPDATED_DATE TEXT, " +
                "ABV TEXT, " +  // DRS 20220726
                "UNTAPPD_BEER TEXT, " +  // DRS 20220730
                "UNTAPPD_BREWERY TEXT" +  // DRS 20220730
                ");";
                // NOTE: MUST HAVE "...TEXT, " <<< note comma and space!!
    }

    /* non-persistant variables */
    private String user_plate;
    private String brew_plate;


    /* database column variables */
    Long _id;           //automatically assigned
    String name;        //from web
    String store_id;    //from web
    String brew_id;     //from web
    String brewer;      //from web
    String city;        //from web
    String isLocal;     //calculate from city
    String country;     //from web
    String container;   //from web but description could override
    String style;       //from web
    String description; //from web
    String abv;         //calculated from description
    String stars;       //from web
    String reviews;     //from web
    String created;     //from web
    String userReview;  //from web DRS 20181023
    String userStars;   //from web DRS 20181023
    String reviewId;    //from web DRS 20181023
    String timestamp;   //from web DRS 20181023
    String createdDate; //calculated from created
    String newArrival;  //added during database population
    String isImport;    //added during database population
    String reviewFlag;  //added during database population DRS 20181023

    String active;      //added during database population
    String tasted;      //added during database population
    String highlighted; //added by user

    String glassSize;   //added by menu scan process //DRS 20171128 - Menu scan
    String glassPrice;  //added by menu scan process //DRS 20171128 - Menu scan
    String untappdBeer;     //added by menu scan process //DRS 20220730
    String untappdBrewery;  //added by menu scan process //DRS 20220730
    String queStamp;  //added by send to brews on queue //DRS 20230323
    String currentlyQueued; //added by send to brews on queue //DRS 20230323

    /* end database column variables */


    /* database field names */
    static final String ID = "_id";// INTEGER PRIMARY KEY AUTOINCREMENT, " +
    public static final String NAME =         "NAME";// TEXT, " +
    public static final String STORE_ID =             "STORE_ID";// TEXT, " +
    public static final String BREW_ID =         "BREW_ID";// TEXT, " +
    public static final String BREWER =         "BREWER";//  TEXT, " +
    static final String CITY =         "CITY";//  TEXT, " +
    static final String IS_LOCAL =         "IS_LOCAL";//  TEXT, " +
    static final String COUNTRY =         "COUNTRY";//  TEXT, " +
    public static final String CONTAINER =         "CONTAINER";//  TEXT, " +
    static final String STYLE =        "STYLE";//  TEXT, " +
    public static final String DESCRIPTION =         "DESCRIPTION";// TEXT, " +
    public static final String ABV =         "ABV";//  TEXT, " +
    static final String STARS =         "STARS";//  TEXT, " +
    static final String REVIEWS =         "REVIEWS";//  TEXT, " +
    static final String CREATED =         "CREATED";//  TEXT, " +
    public static final String ACTIVE =         "ACTIVE";//  TEXT, " +
    static final String TASTED =         "TASTED";//  TEXT," +
    static final String HIGHLIGHTED =         "HIGHLIGHTED";//  TEXT," +
    static final String CREATED_DATE =         "CREATED_DATE";//  TEXT," +
    static final String NEW_ARRIVAL =         "NEW_ARRIVAL";//  TEXT" +
    static final String IS_IMPORT =         "IS_IMPORT";//  TEXT" +
    static final String GLASS_SIZE =         "GLASS_SIZE";//  TEXT" +         //DRS 20171128 - Menu scan
    static final String GLASS_PRICE =         "GLASS_PRICE";//  TEXT" +       //DRS 20171128 - Menu scan
    static final String USER_REVIEW = "USER_REVIEW"; // DRS 20181023
    static final String USER_STARS = "USER_STARS"; // DRS 20181023
    static final String REVIEW_ID = "REVIEW_ID"; // DRS 20181023
    static final String REVIEW_FLAG = "REVIEW_FLAG"; // DRS 20181023
    static final String TIMESTAMP = "TIMESTAMP"; // DRS 20181023
    static final String UNTAPPD_BEER = "UNTAPPD_BEER"; // DRS 20181023
    static final String UNTAPPD_BREWERY = "UNTAPPD_BREWERY"; // DRS 20181023
    static final String QUE_STAMP = "QUE_STAMP"; // DRS 20230323
    static final String CURRENTLY_QUEUED = "CURRENTLY_QUEUED"; // DRS 20230323
    /* END database field names */

    @SuppressLint("Range")
    public SaucerItem populate(Cursor cursor) {
        try {
            clear();
            String[] columnNames = cursor.getColumnNames();
            //StringBuffer columnNamesStringBuff = new StringBuffer();
            for (String columnName: columnNames) {
                //columnNamesStringBuff.append(columnName).append(",");
                switch  (columnName) {
                    case NAME:
                        name = cursor.getString(cursor.getColumnIndex(NAME));
                        break;
                    case STORE_ID:
                        store_id = cursor.getString(cursor.getColumnIndex(STORE_ID));
                        break;
                    case BREW_ID:
                        brew_id = cursor.getString(cursor.getColumnIndex(BREW_ID));
                        break;
                    case BREWER:
                        brewer = cursor.getString(cursor.getColumnIndex(BREWER));
                        break;
                    case CITY:
                        city = cursor.getString(cursor.getColumnIndex(CITY));
                        break;
                    case IS_LOCAL:
                        isLocal = cursor.getString(cursor.getColumnIndex(IS_LOCAL));
                        break;
                    case COUNTRY:
                        country = cursor.getString(cursor.getColumnIndex(COUNTRY));
                        break;
                    case CONTAINER:
                        container = cursor.getString(cursor.getColumnIndex(CONTAINER));
                        break;
                    case STYLE:
                        style = cursor.getString(cursor.getColumnIndex(STYLE));
                        break;
                    case DESCRIPTION:
                        description = cursor.getString(cursor.getColumnIndex(DESCRIPTION));
                        break;
                    case ABV:
                        abv = cursor.getString(cursor.getColumnIndex(ABV));
                        break;
                    case STARS:
                        stars = cursor.getString(cursor.getColumnIndex(STARS));
                        break;
                    case REVIEWS:
                        reviews = cursor.getString(cursor.getColumnIndex(REVIEWS));
                        break;
                    case CREATED:
                        created = cursor.getString(cursor.getColumnIndex(CREATED));
                        break;
                    case ACTIVE:
                        active = cursor.getString(cursor.getColumnIndex(ACTIVE));
                        break;
                    case TASTED:
                        tasted = cursor.getString(cursor.getColumnIndex(TASTED));
                        break;
                    case HIGHLIGHTED:
                        highlighted = cursor.getString(cursor.getColumnIndex(HIGHLIGHTED));
                        break;
                    case CREATED_DATE:
                        createdDate = cursor.getString(cursor.getColumnIndex(CREATED_DATE));
                        break;
                    case NEW_ARRIVAL:
                        newArrival = cursor.getString(cursor.getColumnIndex(NEW_ARRIVAL));
                        break;
                    case IS_IMPORT:
                        isImport = cursor.getString(cursor.getColumnIndex(IS_IMPORT));
                        break;
                    case GLASS_SIZE:
                        glassSize = cursor.getString(cursor.getColumnIndex(GLASS_SIZE)); // DRS 20171128 - Menu scan
                        break;
                    case GLASS_PRICE:
                        glassPrice = cursor.getString(cursor.getColumnIndex(GLASS_PRICE)); // DRS 20171128 - Menu scan
                        break;
                    case USER_REVIEW:
                        userReview = cursor.getString(cursor.getColumnIndex(USER_REVIEW)); // DRS 20181023
                        break;
                    case USER_STARS:
                        userStars = cursor.getString(cursor.getColumnIndex(USER_STARS)); // DRS 20181023
                        break;
                    case REVIEW_ID:
                        reviewId = cursor.getString(cursor.getColumnIndex(REVIEW_ID)); // DRS 20181023
                        break;
                    case REVIEW_FLAG:
                        reviewFlag = cursor.getString(cursor.getColumnIndex(REVIEW_FLAG)); // DRS 20181023
                        break;
                    case TIMESTAMP:
                        timestamp = cursor.getString(cursor.getColumnIndex(TIMESTAMP)); // DRS 20181023
                    case UNTAPPD_BEER:
                        untappdBeer = cursor.getString(cursor.getColumnIndex(UNTAPPD_BEER)); // DRS 20171128 - Menu scan
                        break;
                    case UNTAPPD_BREWERY:
                        untappdBrewery = cursor.getString(cursor.getColumnIndex(UNTAPPD_BREWERY)); // DRS 20171128 - Menu scan
                        break;
                    case QUE_STAMP:
                        queStamp = cursor.getString(cursor.getColumnIndex(QUE_STAMP)); // DRS 20230323 - Show queued beers accepted
                        break;
                    case CURRENTLY_QUEUED:
                        currentlyQueued = cursor.getString(cursor.getColumnIndex(CURRENTLY_QUEUED)); // DRS 20230323 - Show queued beers accepted
                        break;
                    case ID:
                        _id = cursor.getLong(cursor.getColumnIndex(ID));
                        break;
                    default:
                        Log.v(TAG, "Not sure what to do about " + columnName);

                }
            }
            //System.out.println("debug column name " + columnNamesStringBuff.toString());
        } catch (Throwable t) {
            Log.v(TAG, "Failed to complete model item from database cursor. " + t.getMessage())    ;
        }
        return saucerItem;
    }


}
