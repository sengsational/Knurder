package com.sengsational.knurder;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Date;

import static com.sengsational.knurder.BeerSlideActivity.EXTRA_TUTORIAL_TYPE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TEXT_RESOURCE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TITLE_RESOURCE;
import static com.sengsational.knurder.TopLevelActivity.ONE_DAY_IN_MS;
import static com.sengsational.knurder.TopLevelActivity.USER_NAME;
import static com.sengsational.knurder.UfoDatabaseAdapter.fractionTapsWithMenuData;


/**
 * Created by Owner on 5/17/2016.
 */
public class BeerSlideFragment extends Fragment {
    private static final String TAG = BeerSlideFragment.class.getSimpleName();
    private static MybCursorRecyclerViewAdapter mCursorRecyclerViewAdapter;

    private static final String ARG_POSITION = "position";
    private static final String ARG_POSITION_FIRST_VISIBLE = "position_first_visible";
    private static final String ARG_LAYOUT_ID = "layout_id";
    private static boolean REFRESH_REQUIRED = false;
    private static int listPosition = -1;
    private static int mLayoutId;
    private String mBeerName;
    public static final String PREF_DETAILS_TUTORIAL = "prefDetailsTutorial";

    //private static Cursor mCursor; // DRS 20161130

    public static BeerSlideFragment create(int position, int firstVisible) {
        BeerSlideFragment fragment = new BeerSlideFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        args.putInt(ARG_POSITION_FIRST_VISIBLE, firstVisible);
        Log.v("sengsational", "create first visible : " + firstVisible);
        fragment.setArguments(args);
        return fragment;
    }

    public static BeerSlideFragment newInstance(int position, int layoutId) {
        Log.v(TAG, "newInstance 1");
        mLayoutId = layoutId;
        //mCursor = cursor; // DRS 20161201 - Commented 1 - Cursors only in application class
        BeerSlideFragment fragment = new BeerSlideFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        args.putInt(ARG_LAYOUT_ID, layoutId);
        fragment.setArguments(args);
        return fragment;
    }

    public static BeerSlideFragment newInstance(int position, int firstVisible , int unusedConstructor) {
        Log.v(TAG, "newInstance 2");
        BeerSlideFragment fragment = new BeerSlideFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        args.putInt(ARG_POSITION_FIRST_VISIBLE, firstVisible);
        Log.v("sengsational", "new instance first visible : " + firstVisible);
        fragment.setArguments(args);
        REFRESH_REQUIRED = false;
        return fragment;
    }

    public BeerSlideFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        //Log.v("sengsational", "BeerSlideFragment.onCreate() ARG_POSITION position: " + getArguments().getInt(ARG_POSITION));
        if (savedInstanceState != null) {
            mLayoutId = savedInstanceState.getInt(ARG_LAYOUT_ID);
        }

        if (mCursorRecyclerViewAdapter == null) {
            mCursorRecyclerViewAdapter = new MybCursorRecyclerViewAdapter(getActivity(), KnurderApplication.getCursor(getActivity()), false, false); //Pull cursor from the application class
            mCursorRecyclerViewAdapter.hasStableIds();
        }
        if (KnurderApplication.getTutorialLock()) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
            boolean showTutorial =  prefs.getBoolean(PREF_DETAILS_TUTORIAL, true);
            boolean hasUntappdUrl = !"".equals(UntappdHelper.getInstance().getUntappdUrlForCurrentStore("", getContext()));
            if (savedInstanceState == null && hasUntappdUrl && showTutorial) {

                long lastListDate = prefs.getLong(TopLevelActivity.LAST_LIST_DATE, 0L);
                long msSinceRefreshList = new Date().getTime() - lastListDate;
                Intent popTutorialIntent = new Intent(getContext(), PopTutorial.class);
                if (msSinceRefreshList > 3600000L) { // refresh older than one hour ago, so tell them to refresh
                    popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.details_instructions_refresh_needed);
                } else {
                    popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.details_instructions);
                }
                popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.details_title);
                popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_DETAILS_TUTORIAL);
                startActivity(popTutorialIntent);
            }
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Log.v(TAG, "onActivityCreated() running.");
        Object activity = getActivity();
        if (activity != null ) {
            getActivity().setTitle("Knurder");
        }
        if (savedInstanceState != null){
            int layoutIdFromSave = savedInstanceState.getInt(ARG_LAYOUT_ID, -1);
            if (layoutIdFromSave > 0) mLayoutId = layoutIdFromSave;
            else Log.v(TAG, "mLayoutId was " + mLayoutId);
        }
    }

    public String getBeerNameTempTest() {
        return mBeerName;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.v(TAG, "onCreateView() with mLayoutId " + mLayoutId);
        ViewGroup aViewGroup = null;
        try {
            aViewGroup = (ViewGroup) inflater.inflate(mLayoutId, container, false); // occasionally throws android.content.res.Resources$NotFoundException
            if (aViewGroup == null) {
                int layoutId = savedInstanceState.getInt(ARG_LAYOUT_ID);
                if (layoutId == 0) throw new Exception("Could not get the layoutId from the savedInstanceState");
                aViewGroup = (ViewGroup) inflater.inflate(layoutId, container, false);
            }
        } catch (Exception e) {
            Log.v("sengsational", "Could not define the rootView.  This is bad.  mLayoutId was " + mLayoutId + " Error: " + e.getMessage());
        }

        if (aViewGroup == null) aViewGroup = container; // Total BS...  I don't know what to do if this happens.

        final ViewGroup rootView = aViewGroup;

        listPosition = getArguments().getInt(ARG_POSITION);
        Cursor aCursor = KnurderApplication.getCursor(getContext());
        aCursor.moveToPosition(listPosition);
        final SaucerItem modelItem = new SaucerItem(KnurderApplication.getCursor(getContext()));
        //Log.v("sengsational", "BeerSlideFragment.onCreateView() Cursor moving to ARG_POSITION position: " + listPosition + " and finding " + modelItem.getName());
        //int databaseId = Integer.parseInt(mCursor.getString(0));
        //Log.v("sengsational", "BeerSlideFragment.onCreateView() ListView cursor said to query database id: " + databaseId  );
        // DRS 20161130 - Commented 1, Added 11 - set view elements from model
        // populateView(databaseId, rootView); <<<  This opens a database instead of using an existing cursor and doesn't use a model
        ((TextView)rootView.findViewById(R.id.database_key)).setText(modelItem.getIdString());
        ((TextView)rootView.findViewById(R.id.beername)).setText(modelItem.getName());
        mBeerName = modelItem.getName();
        ((TextView)rootView.findViewById(R.id.description)).setText(modelItem.getDescription());
        ((TextView)rootView.findViewById(R.id.abv)).setText(SaucerItem.getPctString(modelItem.getAbv()));
        ((TextView)rootView.findViewById(R.id.city)).setText(modelItem.getCity());
        ((TextView)rootView.findViewById(R.id.style)).setTypeface(null, Typeface.BOLD_ITALIC);
        ((TextView)rootView.findViewById(R.id.style)).setText(modelItem.getStyle());
        String createdText = modelItem.getCreated();
        ((TextView)rootView.findViewById(R.id.created)).setText((createdText==null || "null".equals(createdText))?"":("Tasted " + createdText));

        ImageView highlighted = (ImageView)rootView.findViewById(R.id.highlighted);
        if (highlighted != null) ViewUpdateHelper.setHighlightIconInView(highlighted, modelItem.getHighlighted(), this.getContext());
        ViewUpdateHelper.setNewArrivalStyleInView((TextView)rootView.findViewById(R.id.new_arrival), modelItem.getNewArrival(), getContext());
        ViewUpdateHelper.setActiveStyleInView((TextView)rootView.findViewById(R.id.beername), modelItem.getActive(), getContext());
        ViewUpdateHelper.setActiveStyleInView((TextView)rootView.findViewById(R.id.description), modelItem.getActive(), getContext());

        ViewUpdateHelper.setGlassShapeIconInView((ImageView)rootView.findViewById(R.id.glass_icon_details), modelItem.glassSize, this.getContext());
        ViewUpdateHelper.setGlassPriceInView((TextView)rootView.findViewById(R.id.glass_price_details), modelItem.glassPrice, this.getContext());

        // This will set bold, bold-italic, italic or normal font for the beer name in the beer detail page.
        String newArrivalState = modelItem.getNewArrival();
        String tastedState = modelItem.getTasted();
        String fontDeterminer = (newArrivalState!=null?newArrivalState:"F") + (tastedState!=null?tastedState:"F");
        ViewUpdateHelper.setNameTextStyle(fontDeterminer, (TextView)rootView.findViewById(R.id.beername));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        String userName = prefs.getString(USER_NAME, "");
        boolean darkMode = prefs.getBoolean("dark_mode_switch", false);
        ViewUpdateHelper.setQueuedMessageInView((TextView)rootView.findViewById(R.id.queuedMessage), modelItem.getQueText(getContext()), userName, darkMode, getContext());

        final Context context = this.getContext();
        TextView description = (TextView)rootView.findViewById(R.id.description);
        description.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // This code was duplicated in the long click of MybCursorRecyclerViewAdapter, now uses updateViewHighlightState()
                // This code was duplicated in the long click of BeerSlideFragment, now uses updateViewHighlightState()
                // DRS 20161130 - Added new implementation
                String highlightState = modelItem.getHighlighted();
                Log.v(TAG, "iconView was visible? " + (((ImageView)((RelativeLayout)v.getParent()).findViewById(R.id.highlighted)).getVisibility() == View.VISIBLE) + " modelHighlighted letter: " + highlightState);

                ImageView viewToManage = (ImageView)rootView.findViewById(R.id.highlighted);
                if (viewToManage == null) viewToManage = (ImageView)rootView.findViewById(R.id.highlighted_list_item);

                ViewUpdateHelper.updateViewHighlightState(viewToManage, modelItem, false, context);

                Log.v(TAG, "updating the database from the changed model with position " + listPosition);
                UfoDatabaseAdapter.update(modelItem, listPosition, context);

                KnurderApplication.reQuery(getContext());

                boolean longClickConsumed = true;
                return longClickConsumed;
            }
        });

        TextView beerName = (TextView)rootView.findViewById(R.id.beername);
        beerName.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                boolean longClickConsumed = true;
                String beerNumber =  modelItem.getUntappdBeer();
                Log.v(TAG, "long click on beerName.  Untappd beer number: " + beerNumber);
                if (beerNumber == null) {
                    Log.v(TAG, "no beer number for " + modelItem.name);
                    String storeNumber = modelItem.getStore_id();
                    return informUserAboutMenuScanOrUntappedMatching(storeNumber, v.getContext());
                }

                // First try to use locally installed untappd app
                Uri untappdBeerUri = Uri.parse("untappd://beer/" + modelItem.getUntappdBeer());
                Intent untappdIntent = new Intent(Intent.ACTION_DEFAULT, untappdBeerUri);
                boolean activityFound = true;
                try {
                    startActivity(untappdIntent);
                } catch (ActivityNotFoundException n) {
                    Log.v(TAG,  n.getClass().getName() + " " + n.getMessage());
                    activityFound = false;
                } catch (Throwable t) {
                    Log.v(TAG, t.getClass().getName() + " message: " + t.getMessage());
                    activityFound = false;
                }

                if (activityFound) return longClickConsumed;

                Log.v(TAG, "Failed to open untappd app.  Trying html.");
                // If the above fails, try to use browser to get to untapped beer
                untappdBeerUri = Uri.parse("https://untappd.com/beer/" + modelItem.getUntappdBeer());
                untappdIntent = new Intent(Intent.ACTION_DEFAULT, untappdBeerUri);
                try {
                    startActivity(untappdIntent);
                    activityFound = true;
                } catch (ActivityNotFoundException n) {
                    Log.v(TAG,  n.getClass().getName() + " " + n.getMessage());
                    activityFound = false;
                } catch (Throwable t) {
                    Log.v(TAG, t.getClass().getName() + " message: " + t.getMessage());
                    activityFound = false;
                }
                if (!activityFound) Log.v(TAG, "ERROR: Unable to open untappd app and unable to open browser.");

                return longClickConsumed;
            }
        });

        return rootView;
    }

    @Override
    public void onPause(){
        super.onPause();
        Log.v("sengsational", "BSF.onPause() refresh:" + REFRESH_REQUIRED);
        Log.v(TAG, "Context from Application: " + KnurderApplication.getContext());
        BeerListActivity.setRefreshRequired(REFRESH_REQUIRED);
        if (REFRESH_REQUIRED) {
            BeerListActivity.setLastListPosition(getArguments().getInt(ARG_POSITION_FIRST_VISIBLE));
        } else {
            BeerListActivity.setLastListPosition(-1);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mLayoutId > 0) outState.putInt(ARG_LAYOUT_ID, mLayoutId);
        super.onSaveInstanceState(outState);
    }

    public void toggleFavorite(int databaseKey, View rootView, boolean refreshRequired) {
        REFRESH_REQUIRED = refreshRequired;
        Cursor cursor = null;
        SQLiteDatabase db = null;
        //Create a cursor to read the one record by database _id, passed here by EXTRA_ID
        try {
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getActivity());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            // DRS 20160809 - Added another highlight state "X" for 'unavailable'
            String highlightState = "F";
            cursor = db.query("UFO", new String[] {"HIGHLIGHTED"}, "_id = ?", new String[] {Integer.toString(databaseKey)}, null, null, null);
            if (cursor.moveToFirst()) {
                highlightState = cursor.getString(0);
            }
            cursor.close();
            Log.v("sengsational", "Highlighted state: " + highlightState);
            ImageView viewToManage = (ImageView)rootView.findViewById(R.id.highlighted);
            if (viewToManage == null) viewToManage = (ImageView)rootView.findViewById(R.id.highlighted_list_item);
            updateHighlightState(viewToManage, highlightState, db, databaseKey, this.getContext());

        } catch (SQLiteException e) {
            Toast toast = Toast.makeText(getContext(), "Database unavailable. "+ e.getMessage(), Toast.LENGTH_LONG)   ;
        } finally {
            try {cursor.close();} catch (Throwable t) {}
            try {db.close();} catch (Throwable t) {}
            }
    }

    private static void updateHighlightState(ImageView viewToManage, String highlightState, SQLiteDatabase db, int databaseKey, Context context) {
        if (highlightState == null) highlightState = "F";
        switch (highlightState) {
            case "F":
                highlightState = "T";
                break;
            case "T":
                highlightState = "X";
                break;
            case "X":
                highlightState = "F";
            break;
        }
        db.execSQL("update UFO set HIGHLIGHTED='" + highlightState + "' where _id = " + databaseKey);
        ViewUpdateHelper.setHighlightIconInView(viewToManage, highlightState, context);
    }

    private boolean informUserAboutMenuScanOrUntappedMatching(String storeNumber, Context context) {
        Log.v("sengsational", "informUserAboutMenuScanOrUntappedMatching");
        Log.v(TAG, "storeNumber for beer was " + storeNumber);
        boolean isConsumed = false;
        if (storeNumber == null || storeNumber.equals("null")) return isConsumed;

        StringBuilder message = new StringBuilder();
        android.app.AlertDialog.Builder untappdProblemDialog = new android.app.AlertDialog.Builder(context);
        Float fractionUntappdPopulated = fractionTapsWithMenuData(storeNumber, context);
        Log.v(TAG, "fractionUnappdPopulated " + fractionUntappdPopulated);
        boolean untappedUrlPresent = !UntappdHelper.getInstance().getUntappdUrlForCurrentStore("", context).equals("");

        if (!untappedUrlPresent || fractionUntappdPopulated < 0.1f) {
            // Probably have never got menu info, so tell them how to scan the touchless QR code, which will populate untappd keys, and then enable untappd.
            if (!untappedUrlPresent) {
                message.append("Normally this would open the 'Untappd' page for this beer, but we need to have the Touchless QR code scanned to match up the Saucer list with Untappd.\n\nTo do so, ");
            } else {
                message.append("Normally this would open the 'Untappd' page for this beer, but we haven't yet matched up the tap list with Untappd.\n\nTo do so, ");
            }
            message.append("click below, or you can go the main page's 3 dot menu, select 'Scan the Menu', then 'Scan the Toucheless Menu'. ");
            message.append("Finally, point your camera at the Touchless QR code on the table at the restaurant.\n\nThis will enable the link to Untappd ");
            message.append("and also give you glass size and price.");

            untappdProblemDialog.setMessage(message.toString());
            untappdProblemDialog.setNegativeButton("OK", null);
            untappdProblemDialog.setPositiveButton("Scan Touchless QR Code Now", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                    Intent intentOcrBase = new Intent(context, OcrBase.class);
                    startActivity(intentOcrBase);
                }
            });
            isConsumed = true;
        } else if (fractionUntappdPopulated < 0.4f) {
            message.append("Normally this action would open the 'Untappd' page for this beer, but this tap, and quite a few other taps, ");
            message.append("haven't been matched up with the Saucer's tap list.\n\n");
            message.append("You can try to re-scan the Touchless QR code that's on the table at the restaurant to get more menu information added.");
            untappdProblemDialog.setMessage(message.toString());
            untappdProblemDialog.setNegativeButton("OK", null);
            isConsumed = true;
        } else {
            message.append("Normally this action would open the 'Untappd' page for this beer, but this tap ");
            message.append("hasn't been matched up with the Saucer's tap list.\n\n");
            message.append("Unfortunately, the descriptions between The Saucer and Untappd aren't the same or even similar enough ");
            message.append("at times to guarantee finding a match on the brewery and beer name.");
            untappdProblemDialog.setMessage(message.toString());
            isConsumed = false;
        }
        if (isConsumed) {
            Log.v("sengsational", "about to show.");
            untappdProblemDialog.create().show();
        }
        return isConsumed;
    }


    private void populateView(int id, View rootView) {
        Cursor cursor = null;
        SQLiteDatabase db = null;
        //Create a cursor to read the one record by database _id, passed here by EXTRA_ID
        try {
            Log.v("sengsational","Running query with id: " + id);
            Log.v(TAG, "THIS METHOD UNUSED!!!");
            Log.v(TAG, "THIS METHOD UNUSED!!!");
            Log.v(TAG, "THIS METHOD UNUSED!!!");
            Log.v(TAG, "THIS METHOD UNUSED!!!");
            Log.v(TAG, "THIS METHOD UNUSED!!!");
            Log.v(TAG, "THIS METHOD UNUSED!!!");
            Log.v(TAG, "THIS METHOD UNUSED!!!");
            UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(getContext()) ;
            db = ufoDatabaseAdapter.openDb(getActivity());                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            cursor = db.query("UFO", new String[] {"NAME", "DESCRIPTION", "ABV", "CITY", "STYLE", "CREATED", "HIGHLIGHTED", "NEW_ARRIVAL", "USER_REVIEW"}, "_id = ?", new String[] {Integer.toString(id)}, null, null, null);

            //Move to first record in the cursor (should be just one)
            if (cursor.moveToFirst()) {
                // Get beer details from the cursor
                String nameText = cursor.getString(0);
                String descriptionText = cursor.getString(1);
                String abvText = cursor.getString(2);
                String cityText = cursor.getString(3);
                String styleText = cursor.getString(4);
                String createdText = cursor.getString(5);
                String highlightedText = cursor.getString(6);
                String newArrivalText = cursor.getString(7);
                String userReview = cursor.getString(8);

                // Populate the database key (hidden)
                TextView databaseKey = (TextView)rootView.findViewById(R.id.database_key);
                databaseKey.setText("" + id);

                // Populate the beer name
                TextView name = (TextView)rootView.findViewById(R.id.beername);
                name.setText(nameText);


                // Populate the beer description
                TextView description = (TextView)rootView.findViewById(R.id.description);
                String testAppend = "";
                if (null != userReview && !"null".equals(userReview)) testAppend = "   >>  " + userReview + "<< ";
                description.setText(descriptionText + testAppend);

                // Populate the beer abv
                TextView abv = (TextView)rootView.findViewById(R.id.abv);
                abv.setText(SaucerItem.getPctString(abvText));

                // Populate the beer city
                TextView city = (TextView)rootView.findViewById(R.id.city);
                city.setText(cityText);

                // Populate the style
                TextView style = (TextView)rootView.findViewById(R.id.style);
                style.setTypeface(null, Typeface.BOLD_ITALIC);
                style.setText(styleText);

                TextView created = (TextView) rootView.findViewById(R.id.created);
                if (createdText==null || "null".equals(createdText)){
                    created.setText("");
                } else {
                    created.setText("Tasted " + createdText);
                }

                // Manage Visibility of Highlighted
                Log.v("sengsational", "highlightedText: " + highlightedText);
                ImageView highlightedView = (ImageView)rootView.findViewById(R.id.highlighted);
                ViewUpdateHelper.setHighlightIconInView(highlightedView, highlightedText, this.getContext());

                // Manage Visibility of NewArrival
                TextView newArrivalView = (TextView)rootView.findViewById(R.id.new_arrival);
                if ("T".equals(newArrivalText)) {
                    name.setTypeface(null, Typeface.ITALIC);
                    newArrivalView.setText("New Arrival");
                    newArrivalView.setVisibility(View.VISIBLE);
                } else {
                    name.setTypeface(null, Typeface.NORMAL);
                    newArrivalView.setVisibility(View.GONE);
                }

            }
        } catch (SQLiteException e) {
            Toast toast = Toast.makeText(this.getContext(), "Database unavailable. "+ e.getMessage(), Toast.LENGTH_LONG)   ;
        } finally {
            try {cursor.close();} catch (Throwable t) {}
            try {db.close();} catch (Throwable t) {}
        }

    }
}
