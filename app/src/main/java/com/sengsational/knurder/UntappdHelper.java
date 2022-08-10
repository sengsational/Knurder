package com.sengsational.knurder;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static com.sengsational.knurder.KnurderApplication.getContext;
import static com.sengsational.knurder.TopLevelActivity.STORE_NAME;

public class UntappdHelper {
    private static final String TAG =  UntappdHelper.class.getSimpleName();
    private static final String PREF_UNTAPPD_URL_MAP = "prefUntappdUrlMap";
    private static UntappdHelper mUntappdHelper;
    public static UntappdHelper getInstance() {
        if (mUntappdHelper == null){
            mUntappdHelper = new UntappdHelper();
        }
        return mUntappdHelper;
    }
    private UntappdHelper() {
    }

    private static String mUntappdUrlMapString = "" +
            "13888,https://business.untappd.com/locations/35529/themes/137645/js," + //https://saucerknurd.com/touchless-menu/?store=FSCHAR
            "13883,https://business.untappd.com/locations/35463/themes/137381/js," + //https://saucerknurd.com/touchless-menu/?store=FSCOR
            "18686214,," +                                                          //https://saucerknurd.com/touchless-menu/?store=FSCYPRESS works, but has no untappd data
            "18262641,," +                                                          //https://saucerknurd.com/touchless-menu/?store=FSDFW works, but has no untappd data
            "13891,https://business.untappd.com/locations/35055/themes/135749/js," + //https://saucerknurd.com/touchless-menu/?store=FSFW
            "13880,https://business.untappd.com/locations/34604/themes/133945/js," + //https://saucerknurd.com/touchless-menu/?store=FSHOU
            "13885,https://business.untappd.com/locations/35530/themes/137649/js," + //https://saucerknurd.com/touchless-menu/?store=FSLR
            "13881,https://business.untappd.com/locations/35533/themes/137661/js," + //https://saucerknurd.com/touchless-menu/?store=FSMEM
            "13877,https://business.untappd.com/locations/35528/themes/137641/js," + //https://saucerknurd.com/touchless-menu/?store=FSRAL
            "13882,https://business.untappd.com/locations/35531/themes/137653/js," + //https://saucerknurd.com/touchless-menu/?store=FSSAN
            "13879,," + //Have not found touchless page.  Tried SL,SUGAR,SLAND,SUGLAND,FSSL,FSSUGAR,FSSLAND,FSSUGLAND,SUGARFS,SLANDFS,SUGLANDFS,SUGARLANDFS
            "13884,," + //Have not found touchless page.  Tried LAKE,THELAKE,TL,TLAKE,FSLAKE,FSTHELAKE,FSTL,FSTLAKE,LAKEFS,THELAKEFS,TLFS,TLAKEFS
            "";

    public static String getUntappdUrlForCurrentStore(String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        return getUntappdUrlForStoreName(prefs.getString(STORE_NAME, defaultValue));
    }

    // Returns "" if not nvp not populated or store not in list
    public static String getUntappdUrlForStoreName(String storeName) {
        return getUntappdUrlForStoreNumber(StoreNameHelper.getStoreNumberFromName(storeName));
    }

    public static String getUntappdUrlForStoreNumber(String storeNumber) {
        Map<String, String> urlMap = getUntappdUrlStoreNumberMap();
        Set<String> keys = urlMap.keySet();
        for (String key: keys) {
            if (key.equals(storeNumber)) {
                return urlMap.get(key);
            }
        }
        return "";
    }


    public static void saveUntappdUrlForCurrentStore(String untappdUrl) {
        String currentStoreNumber = StoreNameHelper.getCurrentStoreNumber("0");
        if ("0".equals(currentStoreNumber)) return;

        Map<String, String> urlMap = getUntappdUrlStoreNumberMap();
        String untappdUrlFromMap = urlMap.put(currentStoreNumber, untappdUrl);

        if (untappdUrlFromMap != null && !untappdUrlFromMap.equals(untappdUrl)) {
            Log.v(TAG, "Value [" + untappdUrlFromMap + "] was overwritten by [" + untappdUrl + "]");
        }
        saveUntappdUrlStoreNumberMap(urlMap);
    }

    private static Map<String, String> getUntappdUrlStoreNumberMap() {
        String prefUntappdUrlMapString = PreferenceManager.getDefaultSharedPreferences(getContext()).getString(PREF_UNTAPPD_URL_MAP,"");
        // We use the pre-defined URL's only one time.  From then on out, the user can maintain their own by scanning the Touchless QR code.
        if ("".equals(prefUntappdUrlMapString)){
            Log.v(TAG, "one time push of untappd url mapping into PREF_UNTAPPD_URL_MAP");
            prefUntappdUrlMapString = mUntappdUrlMapString;
            SharedPreferences.Editor prefEdit = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
            prefEdit.putString(PREF_UNTAPPD_URL_MAP, mUntappdUrlMapString);
            prefEdit.apply();
        }
        String[] storeUrlArray = prefUntappdUrlMapString.split(",");
        TreeMap<String, String> returnMap = new TreeMap<String, String>();
        for (int i = 0; i < storeUrlArray.length - 1; i = i + 2){
            returnMap.put(storeUrlArray[i], storeUrlArray[i+1]);
        }
        Log.v(TAG, "Returning map with " + returnMap.size() + " items.");
        return returnMap;
    }

    public static void saveUntappdUrlStoreNumberMap(Map<String, String> storeUrlMap) {
        StringBuffer saveStringBuffer = new StringBuffer();
        Set<String> keys = storeUrlMap.keySet();
        for (String key: keys) {
            saveStringBuffer.append(key).append(",").append(storeUrlMap.get(key));
        }
        SharedPreferences.Editor preferenceEditor = PreferenceManager.getDefaultSharedPreferences(getContext()).edit();
        preferenceEditor.putString(UntappdHelper.PREF_UNTAPPD_URL_MAP, saveStringBuffer.toString());
        preferenceEditor.apply();
    }


}
