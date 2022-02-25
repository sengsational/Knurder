package com.sengsational.knurder;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;

import java.util.prefs.Preferences;

/**
 * Created by Dale Seng on 8/15/2016.
 */
public class KnurderApplication extends Application {
    private static final String TAG = KnurderApplication.class.getSimpleName();

    public static boolean oldListCheck = true;
    public static boolean reviewUploadWarning = true;
    private static Cursor mCursor;

    private static String mPresentationMode;
    private static Context mContext;
    private static Boolean mWebUpdateLocked = false;
    private static int denyCount = 0;

    public KnurderApplication() {
        Log.v("sengsational", "KnurderApplication constructor.");
    }

    public static void setContext(Context context) {
        mContext = context;
    }

    public static boolean checkContext() {
        return mContext != null;
    }

    public static Context getContext() {
        if (mContext == null ) {
            Log.v(TAG, "mContext was null");
        }
        return mContext;
    }

    // Web Update Lock is not really required since background threads are run serially by default by AsyncTask for API 13+
    public static synchronized Boolean getWebUpdateLock(String identifier) {
        if (!mWebUpdateLocked) {        // Not locked
            mWebUpdateLocked = true;    // Got the lock
            Log.v(TAG, "Web update lock granted to " + identifier + ".");
            denyCount = 0;
            return true;
        } else {                        // Was locked
            denyCount++;
            if (denyCount > 20) {        // If something weird keeps happening, let it go without the lock after 10 seconds
                mWebUpdateLocked = true;
                denyCount = 0;
                Log.v(TAG, "Web update granted, but FORCED for " + identifier + ".");
                return true;
            }
            Log.v(TAG, "Web update lock denied for " + identifier + ". Sleeping 1/2 second.");
            try {Thread.sleep(500);} catch (Exception e){}
            return false;
        }
    }

    public static synchronized void releaseWebUpdateLock(String identifier) {
        Log.v(TAG, "Web update lock released by " + identifier);
        mWebUpdateLocked =  false;      // Release the lock
    }

    @Override
    public void onTerminate() {
        Log.v(TAG, "onTerminate() has been called.");
        super.onTerminate();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // DRS 20200924 - Day Night Theme Added
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(KnurderApplication.this);
        if (prefs.getBoolean("dark_mode_switch", false)) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        Log.v(TAG, "KnurderApplication onCreate.");
    }

    public static void setCursor(Cursor cursor) {
        if (mCursor != null) {
            Log.v(TAG, "setCursor is closing existing cursor.");
            mCursor.close();
        }
        mCursor = cursor;
        //if (queryPkg != null) QueryPkg = queryPkg;
    }

    public static Cursor getCursor(Context context) {

        if (mCursor != null && !mCursor.isClosed()) {
            return mCursor;
        } else {
            Log.v(TAG, "ERROR: The cursor was null!!!");
            if (mContext != null) mCursor = reQuery(mContext);
            else if (context != null) mCursor = reQuery(context);
        }
        if (mCursor == null) {
            Log.e(TAG, "ERROR: mCursor was null, despite all efforts to get a good one. Using a generic one.");
            mCursor = UfoDatabaseAdapter.getCursor();
        }
        return mCursor;
    }

    public static void closeCursor() {
        if (mCursor != null) {
            Log.v(TAG, "closeCursor()");
            mCursor.close();
        }
    }

    public static void addSearchTextToQueryPackage(String searchText, Context context) {
        QueryPkg.setFullTextSearch(searchText, context);
    }

    public static Cursor reQuery(Context context) {
        Cursor aCursor = UfoDatabaseAdapter.fetch(context);
        setCursor(aCursor);
        return aCursor;
    }

    public static String getPresentationMode() {
        return mPresentationMode;
    }

    public static void setPresentationMode(String presentationMode) {
        mPresentationMode = presentationMode;
    }

}
