package com.sengsational.knurder;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatDelegate;
import android.support.v7.app.AppCompatCallback;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;

public class BeerListActivity extends ListActivity implements AppCompatCallback {
    private SQLiteDatabase db;
    private static Cursor cursor;

    //private static QueryPkg queryPackage;
    private static ListView listBeers;
    private static boolean showDateInList;
    private static boolean hideMixesAndFlights;
    private static boolean refreshRequired;
    private static int listPosition = -1;
    private AppCompatDelegate mAppCompatDelegate;

    private ArrayAdapter<String> mAdapter;
    private String mActivityTitle;
    private ResourceCursorAdapter mResourceCursorAdapter;
    private int[] lastSort = {-1,-1}; //for instance {QueryPackage.NAME, QueryPackage.ASC}

    //private static boolean drawerEnabled = false;

    static Cursor getCursor() {
        return cursor;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mAppCompatDelegate = AppCompatDelegate.create(this, this);
        mAppCompatDelegate.installViewFactory();
        mAppCompatDelegate.onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);

        Log.v("OLD CLASS", "<<<<<<<<<<<<<<<<<<<<<<THIS CLASS NO LONGER USED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        Log.v("OLD CLASS", "<<<<<<<<<<<<<<<<<<<<<<THIS CLASS NO LONGER USED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        Log.v("OLD CLASS", "<<<<<<<<<<<<<<<<<<<<<<THIS CLASS NO LONGER USED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        Log.v("OLD CLASS", "<<<<<<<<<<<<<<<<<<<<<<THIS CLASS NO LONGER USED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        Log.v("OLD CLASS", "<<<<<<<<<<<<<<<<<<<<<<THIS CLASS NO LONGER USED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        Log.v("OLD CLASS", "<<<<<<<<<<<<<<<<<<<<<<THIS CLASS NO LONGER USED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        Log.v("OLD CLASS", "<<<<<<<<<<<<<<<<<<<<<<THIS CLASS NO LONGER USED>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>");
        // create the list
        // create long click listener for the items
        //listBeers = createLongClickListener(getListView());
        listBeers = getListView();


        // This intent carries with it the details of how to query
        Intent intent = getIntent();
        String selectionFields = intent.getStringExtra("selectionFields");
        String selectionArgs[] = intent.getStringArrayExtra("selectionArgs");
        final String pullFields[] = intent.getStringArrayExtra("pullFields");
        final String orderBy = intent.getStringExtra("orderBy");

        showDateInList = intent.getBooleanExtra("showDateInList",false);
        refreshRequired = intent.getBooleanExtra("refreshRequired",false);
        hideMixesAndFlights = intent.getBooleanExtra("hideFlag",true);

        // This is goofy, but it saves to Shared Preferences
        new QueryPkg("UFO", pullFields, selectionFields, selectionArgs, null, null, orderBy, hideMixesAndFlights, "", getApplicationContext());

        //String selectedItems[] = intent.getStringArrayExtra("selectedItems");


        if (pullFields != null) {
            refreshList();
        } else {
            Toast toast = Toast.makeText(this, "No Selection", Toast.LENGTH_SHORT);
            toast.show();
        }

        //if (drawerEnabled) setupDrawerList();

    }

    private ListView createLongClickListener(ListView listView) {
        listView.setLongClickable(true);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                Log.v("sengsational", "Long item click.  position:" + position );
                View databaseKeyView = view.findViewById(R.id.database_key_list_item);
                if (databaseKeyView != null) {
                    String keyString = ((TextView)databaseKeyView).getText().toString();
                    int databaseId = Integer.parseInt(keyString);
                    //BeerSlideFragment.toggleFavorite(databaseId, view, true);
                    Log.v("sengsational", "ERROR...REMOVED toggleFavorite!!!  This is old code and wasn't being referenced, but just in case, I put this");
        }  else {
                    Log.v("sengsational", "dkv was null. View was " + view);
                }
                return true;
            }
        });
        return listView;
    }


    private void refreshList() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hideMix = prefs.getBoolean("mix_switch", true);
        boolean hideFlight = prefs.getBoolean("flight_switch", true);

        if(hideMix && hideMixesAndFlights) {
            QueryPkg.appendSelectionFields(" AND STYLE<>?", this);
            QueryPkg.appendSelectionArgs("Mix", this);
        }
        if(hideFlight && hideMixesAndFlights) {
            QueryPkg.appendSelectionFields(" AND STYLE<>?", this);
            QueryPkg.appendSelectionArgs("Flight", this);
        }
        Log.v("sengsational", "selectionArgs: " + Arrays.toString(QueryPkg.getSelectionArgs(this)));
        Log.v("sengsational", "selectionFields: " + QueryPkg.getSelectionFields(this));

        try {

            /*  Added this block to use for new database access, even though this is an old class that will be thrown out */
            UfoDatabaseAdapter repository = new UfoDatabaseAdapter(this);
            repository.open(this);
            cursor = repository.fetch(this);

            Log.v("sengsational", "BeerListActivity.onCreate() cursor reference from fetch(). " + BeerListActivity.getCursor());
            if (cursor.moveToFirst()) {

                mResourceCursorAdapter = new ResourceCursorAdapter(this, R.layout.my_simple_expandable_list_item_2, cursor) {
                    @Override
                    public void bindView(View view, Context context, Cursor cursor) {
                        TextView tvFirst = (TextView)view.findViewById(android.R.id.text1);
                        TextView tvSecond = (TextView)view.findViewById(android.R.id.text2);
                    }
                };

                listBeers.setAdapter(mResourceCursorAdapter);

                if(listPosition > 0) setSelection(listPosition); // If the list was refreshed and we come back, don't go to the top, go to where they left off.
            } else {
                Toast toast = Toast.makeText(this, "The list was empty....", Toast.LENGTH_SHORT);
                toast.show();
                if(QueryPkg.includesSelection("HIGHLIGHTED", this)){
                    AlertDialog.Builder emptyListDialog = new AlertDialog.Builder(this);
                    emptyListDialog.setMessage("There are currently no beers flagged.  But if you want to flag a beer, get into the beer's details and long-press the description.  You'll see a marker appear.");
                    emptyListDialog.setCancelable(true);
                    emptyListDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            onBackPressed();
                        }
                    });
                    emptyListDialog.create().show();
                }
                if(QueryPkg.includesSelection("NEW_ARRIVAL",this)){
                    AlertDialog.Builder emptyListDialog = new AlertDialog.Builder(this);
                    emptyListDialog.setMessage("You may need to use the 'Refresh Active' function that's using the menu that is activated by clicking the three dots in the upper right corner of the screen.");
                    emptyListDialog.setCancelable(true);
                    emptyListDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            onBackPressed();
                        }
                    });
                    emptyListDialog.create().show();
                }
            }
        } catch (SQLiteException e) {
            Toast toast = Toast.makeText(this, "Database Unavailable", Toast.LENGTH_SHORT);
            toast.show();
        } finally {
            Log.v("sengsational","Not closing cursor.  Will do in onDestroy().");
            // try {cursor.close();} catch(Throwable t){}
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        mAppCompatDelegate.onDestroy();
        Log.v("sengsational", "Closing cursor in onDestroy().");

        if (cursor != null) cursor.close();
        if (db != null) db.close();
    }

    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        Intent intent = new Intent(BeerListActivity.this, BeerSlideActivity.class);
        listView.getFirstVisiblePosition();
        Log.v("sengsational", "BeerListActivity.onListItemClick() position:" + position + " id:" + id);

        intent.putExtra(BeerSlideActivity.EXTRA_POSITION, (int) position);
        //intent.putExtra(BeerSlideActivity.EXTRA_FIRST_VISIBLE_POSITION, (int) listView.getFirstVisiblePosition());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
       //Log.v("sengsational", "BLA.onResume() refreshRequired:" + refreshRequired);
        if (refreshRequired) {
            refreshList();
        }
    }

    public static void setRefreshRequired(boolean refreshRequired) {
        BeerListActivity.refreshRequired = refreshRequired;
        //Log.v("sengsational","BLA.setRefreshRequired("  + refreshRequired + ")");
    }

    public static void setLastListPosition(int listPosition) {
        BeerListActivity.listPosition = listPosition;
    }



    //// Misc Junk ////
    private String unwrap(String[] strangarray) {
        if (strangarray == null) return "";
        StringBuffer buf = new StringBuffer();
        for(String item : strangarray){
            buf.append(item + ",") ;
        }
        return buf.toString();
    }

    public ActionBar getSupportActionBar() {return mAppCompatDelegate.getSupportActionBar();}
    public void setSupportActionBar(@Nullable Toolbar toolbar) {mAppCompatDelegate.setSupportActionBar(toolbar);}
    @Override public MenuInflater getMenuInflater() {return mAppCompatDelegate.getMenuInflater();}
    @Override public void setContentView(@LayoutRes int layoutResID) {mAppCompatDelegate.setContentView(layoutResID);}
    @Override public void setContentView(View view) {mAppCompatDelegate.setContentView(view);}
    @Override public void setContentView(View view, ViewGroup.LayoutParams params) {mAppCompatDelegate.setContentView(view, params);}
    @Override public void addContentView(View view, ViewGroup.LayoutParams params) {mAppCompatDelegate.addContentView(view, params);}
    @Override protected void onPostResume() {super.onPostResume();mAppCompatDelegate.onPostResume();}
    @Override protected void onTitleChanged(CharSequence title, int color) {super.onTitleChanged(title, color);mAppCompatDelegate.setTitle(title);}
    @Override protected void onStop() {super.onStop(); mAppCompatDelegate.onStop();}
    public void invalidateOptionsMenu() { mAppCompatDelegate.invalidateOptionsMenu();}
    @Override public void onSupportActionModeFinished(ActionMode mode) {}
    @Override public void onSupportActionModeStarted(ActionMode mode) {}
    @Override public ActionMode onWindowStartingSupportActionMode(ActionMode.Callback callback) {return null;}


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Log.v("sengsational","Item selected: " + item.getTitle());
        switch (id) {
            case R.id.action_share:
                if (listBeers.getAdapter().isEmpty()){
                    Toast.makeText(getApplicationContext(), "The list was empty...", Toast.LENGTH_SHORT).show();
                    break;
                }
                Log.v("sengsational","In switch: " + item.getTitle());
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_SUBJECT, "KnurderList.csv");
                intent.putExtra(Intent.EXTRA_TEXT, getCsvFromViewCursor(listBeers));
                intent.setType("text/plain");
                startActivity(intent);
                break;
            case R.id.action_sort_abv:
                manageSort(QueryPkg.ABV, true);
                break;
            case R.id.action_sort_name:
                manageSort(QueryPkg.NAME, false);
                break;
            case R.id.action_sort_style:
                manageSort(QueryPkg.STYLE, false);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void manageSearch(int name) {
        Toast.makeText(this, "Not implemented yet.",Toast.LENGTH_SHORT);
    }

    private void manageSort(int fieldNumber, boolean castInteger) {
        // two sorts of the same thing result in swapping asc to desc or vica verca
        int ascDesc = QueryPkg.ASC;
        if(lastSort[0] == fieldNumber && lastSort[1] == QueryPkg.ASC) { ascDesc = QueryPkg.DESC; }
        String sortParameter = QueryPkg.fields[fieldNumber] + " " + QueryPkg.fields[ascDesc];
        if (castInteger) {
            sortParameter = "CAST (" +  QueryPkg.fields[fieldNumber] + " AS INTEGER) " + QueryPkg.fields[ascDesc];
        }
        if(cursor != null) { cursor.close(); }
        cursor = db.query("UFO", QueryPkg.getPullFields(this), QueryPkg.getSelectionFields(this), QueryPkg.getSelectionArgs(this), null, null, sortParameter);
        mResourceCursorAdapter.swapCursor(cursor);
        // update last sort
        lastSort[0] = fieldNumber;
        lastSort[1] = ascDesc;
    }

    private String getCsvFromViewCursor(ListView listBeers) {
        StringBuilder builder = new StringBuilder();
        SQLiteCursor cursor;
        String createdString;
        String highlightedString;
        builder.append("\"Name\",\"Style\",\"ABV\",\"Tasted\",\"Flagged\"\n");
        for (int i = 0; i < BeerListActivity.listBeers.getCount(); i++ ){
            cursor = (SQLiteCursor) BeerListActivity.listBeers.getItemAtPosition(i);
            builder.append("\"").append(cursor.getString(QueryPkg.NAME)).append("\",");
            builder.append("\"").append(cursor.getString(QueryPkg.STYLE)).append("\",");
            builder.append("\"").append(SaucerItem.getPctString(cursor.getString(QueryPkg.ABV))).append("\",");
            createdString = cursor.getString(QueryPkg.CREATED);
            builder.append("\"").append((createdString==null)?"":createdString).append("\",");
            highlightedString = cursor.getString(QueryPkg.HIGHLIGHTED);
            builder.append("\"").append((highlightedString==null)?"":highlightedString).append("\"\n");
        }
        return builder.toString();
    }
}











