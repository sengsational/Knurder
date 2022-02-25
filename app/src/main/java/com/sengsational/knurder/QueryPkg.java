package com.sengsational.knurder;


import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;

public class QueryPkg implements Serializable {
    private static final String TAG = QueryPkg.class.getSimpleName();

    public static final String PULL_FIELDS = "prefPullFields";
    public static final String HIDE_MIXES_AND_FLIGHTS = "prefHideMixesAndFlights";
    public static final String SELECTION_FIELDS = "prefSelectionFields";
    public static final String SELECTION_ARGS = "prefSelectionArgs";
    public static final String ORDER_BY = "prefOrderBy";
    public static final String SECOND_ORDER_BY  = "prefSecondOrderBy";
    public static final String FULL_TEXT_SEARCH = "prefFullTextSearch";

    // DRS 20160617 - Added static final ints
    // Align these with pullFields from TopLevelActivity.onClickShowList()
    //String[] pullFields = new String[]{"_id", "NAME", "DESCRIPTION", "CITY", "ABV", "STYLE", "CREATED", "HIGHLIGHTED","NEW_ARRIVAL","ACTIVE"};
    public static final int _id = 0;
    public static final int NAME = 1;
    public static final int DESCRIPTION = 2;
    public static final int CITY = 3;
    public static final int ABV = 4;
    public static final int STYLE = 5;
    public static final int CREATED = 6;
    public static final int HIGHLIGHTED = 7;
    public static final int NEW_ARRIVAL = 8;
    public static final int ACTIVE = 9;
    public static final int GLASS_SIZE = 10;
    public static final int GLASS_PRICE = 11;
    public static final int USER_REVIEW = 12; // DRS 20181023
    public static final int USER_STARS = 13; // DRS 20181023
    public static final int REVIEW_FLAG =  14; // DRS 20181023
    public static final int BREW_ID = 15;
    public static final int ASC = 16; // DRS 20181023 incremented
    public static final int DESC = 17; // DRS 20181023 incremented
    public static final String[] fields = {"_id", "NAME", "DESCRIPTION", "CITY", "ABV", "STYLE", "CREATED", "HIGHLIGHTED", "NEW_ARRIVAL", "ACTIVE", "GLASS_SIZE", "GLASS_PRICE", "USER_REVIEW", "USER_STARS", "REVIEW_FLAG", "BREW_ID", "ASC", "DESC"};

    private static final String delimiter = "%;%;";


    //private String[] pullFields;
    //private String selectionFields;
    //private String[] selectionArgs;
    //private boolean hideMixesAndFlights;


    //public static QueryPkg qpInstance;

    public QueryPkg(String table, String[] pullFields, String selectionFields, String[] selectionArgs, Object o, Object o1, String orderBy, boolean hideMixesAndFlights, String fullTextSearch, Context context) {
        setPullFields(pullFields, context);
        setSelectionFields(selectionFields, context);
        setSelectionArgs(selectionArgs, context);
        setOrderBy(orderBy, context);
        setHideMixesAndFlights(hideMixesAndFlights, context);
        setFullTextSearch(fullTextSearch, context);
    }

    public QueryPkg(Context context) {

        setPullFields(new String[]{"_id", "NAME", "DESCRIPTION", "CITY", "ABV", "STYLE", "CREATED", "HIGHLIGHTED","NEW_ARRIVAL", "ACTIVE", "IS_IMPORT", "TASTED", "BREWER", "GLASS_SIZE", "GLASS_PRICE", "USER_REVIEW", "USER_STARS", "REVIEW_FLAG", "BREW_ID"}, context);
        setSelectionFields("ACTIVE=?", context);
        setSelectionArgs(new String[]{"T"}, context);
        setOrderBy("NAME", context);
        setHideMixesAndFlights(true, context);
        setFullTextSearch("", context);

    }

    public static void setOrderBy(String orderBy, Context context) {
        //if (qpInstance == null) restorePackage();
        //QueryPkg.orderBy = orderBy;
        if (orderBy == null) orderBy = "NAME";
        Context contextToUse = context;
        if (contextToUse == null) contextToUse = KnurderApplication.getContext();
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(contextToUse);
        SharedPreferences.Editor editor = sharedPrefs.edit();
        editor.putString(ORDER_BY, orderBy);
        editor.apply();
    }

    public static String getOrderBy(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(ORDER_BY, "NAME");
    }


    public static void setSecondSortBy(String secondOrderBy, Context context) {
        //if (qpInstance == null) restorePackage();
        //QueryPkg.secondOrderBy = secondOrderBy;
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(SECOND_ORDER_BY, secondOrderBy);
        editor.apply();
    }

    public static String getSecondOrderBy(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SECOND_ORDER_BY, "");
    }

    public static void setFullTextSearch(String fullTextSearch, Context context) {
        //if (qpInstance == null) restorePackage();
        //QueryPkg.fullTextSearch = fullTextSearch;
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(FULL_TEXT_SEARCH, fullTextSearch);
        editor.apply();
    }

    public static String getFullTextSearch(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(FULL_TEXT_SEARCH, "");
    }

    public static void setPullFields(String[] pullFields, Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        String pullFieldsString  = makeString(pullFields);
        Log.v(TAG, "setPullFields in preferences: " + pullFieldsString);
        editor.putString(PULL_FIELDS, pullFieldsString);
        editor.apply();
    }

    public static String[] getPullFields(Context context) {
        String selectionArgsString = PreferenceManager.getDefaultSharedPreferences(context).getString(PULL_FIELDS, "");
        return makeArray(selectionArgsString);
    }

    public static void setSelectionFields(String selectionFields, Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(SELECTION_FIELDS, selectionFields);
        editor.apply();
    }

    public static String getSelectionFields(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getString(SELECTION_FIELDS, "");
    }

    public static void appendSelectionFields(String additionalSelection, Context context) { // ie " AND STYLE<>?"
        setSelectionFields(getSelectionFields(context) + additionalSelection, context);
    }



    public static void setSelectionArgs(String[] selectionArgs, Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString(SELECTION_ARGS, makeString(selectionArgs));
        editor.apply();
    }

    public static String[] getSelectionArgs(Context context) {
        Log.v(TAG, "context passed in was: " + context.getClass().getSimpleName());
        String selectionArgsString = PreferenceManager.getDefaultSharedPreferences(context).getString(SELECTION_ARGS, "");
        return makeArray(selectionArgsString);
    }

    public static void appendSelectionArgs(String additionalArg, Context context) { // ie "Mix"
        ArrayList<String> selectionArgsArray = new ArrayList<>();
        selectionArgsArray.addAll(Arrays.asList(getSelectionArgs(context)));
        selectionArgsArray.add(additionalArg);
        setSelectionArgs(selectionArgsArray.toArray(new String[0]), context);
    }


    public static void setHideMixesAndFlights(boolean hideMixesAndFlights, Context context) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean(HIDE_MIXES_AND_FLIGHTS, hideMixesAndFlights);
        editor.apply();
    }

    public static boolean getHideMixesAndFlights(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(HIDE_MIXES_AND_FLIGHTS, true);
    }

    public static boolean includesSelection(String columnName, Context context) {
        return getSelectionFields(context).contains(columnName);
    }

    // Support for saving string array in preferences
    private static String makeString(String[] stringArray) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stringArray.length; i++) {
            sb.append(stringArray[i]).append(delimiter);
        }
        return sb.toString();
    }

    private static String[] makeArray(String delimitedString) {
        return delimitedString.split(delimiter);
    }






    /*
    private static void restorePackage() {
        qpInstance = getFromPreferences(KnurderApplication.getContext());
        if (qpInstance == null) qpInstance = new QueryPkg();
    }
    */

    /*
    public static void saveToPreferences(Context context) {
        try {
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
            String serializedQueryPackage = ObjectSerializerHelper.objectToString(qpInstance);
            editor.putString("QueryPackage", serializedQueryPackage);
            editor.apply();
            Log.v(TAG, "serializedQueryPackage placed into preferences " + serializedQueryPackage.length() + " characters.");
        } catch (Throwable t) {
            Log.v(TAG, "Unable to save query package in shared preferences. " + t.getMessage());
        }
    }
    */

    /*
    public static QueryPkg getFromPreferences(Context context) {
        try {
            String serializedQueryPackage = PreferenceManager.getDefaultSharedPreferences(context).getString("QueryPackage", "");
            Log.v(TAG, "serializedQueryPackage pulled from preferences " + serializedQueryPackage.length() + " characters.");
            if (serializedQueryPackage.length() > 0) {
                QueryPkg qpInstance = (QueryPkg) ObjectSerializerHelper.stringToObject(serializedQueryPackage);
                if (qpInstance != null) {
                    try {
                        QueryPkg.checkStatic();
                        return qpInstance;
                    } catch (Throwable t) {
                        return new QueryPkg();
                    }
                } else {
                    return new QueryPkg();
                }
            } else {
                Log.e(TAG, "ERROR: serializedQueryPackage was not in shared preferences.");
                return new QueryPkg();
            }
        } catch (Throwable t) {
            Log.v(TAG, "Unable to load query package from shared preferences. " + t.getMessage());
            return new QueryPkg();
        }
    }
    */

    /*
    public static void checkStatic() {
        // Trying to solve the mystery of the disappearing static class
    }
    */

    /*
    public static void validate(Context context) {
        if (QueryPkg.selectionArgs == null || QueryPkg.selectionFields == null || QueryPkg.fullTextSearch == null || QueryPkg.pullFields == null || QueryPkg.orderBy == null || QueryPkg.secondOrderBy == null) {
            qpInstance = QueryPkg.getFromPreferences(context);
        }
    }
    */


    public static class ObjectSerializerHelper {
        static public String objectToString(Serializable object) {
            String encoded = null;
            try {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                objectOutputStream.writeObject(object);
                objectOutputStream.close();
                encoded = new String(Base64.encodeToString(byteArrayOutputStream.toByteArray(),0));
            } catch (IOException e) {
                e.printStackTrace();
            }
            return encoded;
        }

        @SuppressWarnings("unchecked")
        static public Serializable stringToObject(String string){
            byte[] bytes = Base64.decode(string,0);
            Serializable object = null;
            try {
                ObjectInputStream objectInputStream = new ObjectInputStream( new ByteArrayInputStream(bytes) );
                object = (Serializable)objectInputStream.readObject();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
            return object;
        }

    }
}








