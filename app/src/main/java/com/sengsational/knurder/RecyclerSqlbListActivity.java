package com.sengsational.knurder;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.SearchView;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;
import java.util.prefs.Preferences;

import static com.sengsational.knurder.BeerSlideActivity.EXTRA_TUTORIAL_TYPE;
import static com.sengsational.knurder.OcrBase.SCAN_COVERAGE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TEXT_RESOURCE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TITLE_RESOURCE;

public class RecyclerSqlbListActivity extends AppCompatActivity implements ShakeDetector.Listener {
    private static final String TAG = RecyclerSqlbListActivity.class.getSimpleName();

    // Callback related
    private static final int EMAIL_COMPLETE = 1959;
    private static final int VALIDATE_CARD = 1957;

    public static final String PREF_SHAKER_TUTORIAL = "prefShakerTutorial";
    public static final String PREF_ON_QUE_TUTORIAL = "prefOnQueTutorial";

    //private static QueryPkg queryPackage;
    //private static boolean refreshRequired;
    private static int listPosition = -1;
    //private AppCompatDelegate mAppCompatDelegate;
    public static final int DETAIL_REQUEST = 0;

    private ResourceCursorAdapter mResourceCursorAdapter;
    private int[] lastSort = {-1,-1}; //for instance {QueryPkg.NAME, QueryPkg.ASC}

    public MybCursorRecyclerViewAdapter beerRecyclerViewAdapter;


    // new stuff
    public UfoDatabaseAdapter repository;
    public MybCursorRecyclerViewAdapter cursorRecyclerViewAdapter; //<<<<<<<<<<<<<<<<<<

    private RecyclerView recyclerView;

    // DRS20171019 - Added 4
    private SearchView searchView;
    private MenuItem searchMenu = null;
    private String mQueryTextContent = "";
    private String mQueryButtonText;
    private boolean mIsTapQuery;
    private boolean mIsFlaggedBeerQuery;
    private boolean mIsLoggedIn;
    private ShakeDetector mShakeDetector;
    private static Random mRandom =  new Random();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //mAppCompatDelegate = AppCompatDelegate.create(this, this);
        //mAppCompatDelegate.installViewFactory();
        //mAppCompatDelegate.onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycler_list);

        recyclerView = (RecyclerView) findViewById(R.id.recyclerView1);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        try {
            repository = new UfoDatabaseAdapter(this);
            repository.open(this);

            Log.v(TAG, "onCreate() is pulling records from the database.");

            // This intent carries with it the details of how to query
            Intent intent = getIntent();

            // DRS20171019 - Added if/else (else around existing code)
            if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
                String query = intent.getStringExtra(SearchManager.QUERY);
                Log.v("sengsational", "THIS NEVER RUNS - onCreate() ACTION_SEARCH!!! " + query);
            } else {
                String selectionFields = intent.getStringExtra("selectionFields");
                String selectionArgs[] = intent.getStringArrayExtra("selectionArgs");
                final String pullFields[] = intent.getStringArrayExtra("pullFields");
                //Log.v(TAG, "pullFields in RecyclerSqlbListActivity " + pullFields);
                final String orderBy = intent.getStringExtra("orderBy");
                boolean showDateInList = intent.getBooleanExtra("showDateInList", false);
                boolean hideMixesAndFlights = intent.getBooleanExtra("hideMixesAndFlights", true);
                mQueryButtonText = intent.getStringExtra("queryButtonText");
                mQueryTextContent = intent.getStringExtra("queryTextContent"); // DRS 20171021 - Will be null the first time, but on screen reorientation will have previous query
                if (mQueryTextContent == null) mQueryTextContent = "";
                //refreshRequired = intent.getBooleanExtra("refreshRequired",false);

                mIsTapQuery = selectionFields.contains("CONTAINER=?"); // sorry for the hack, but this is always matched with "draught"
                mIsFlaggedBeerQuery = selectionFields.contains("HIGHLIGHTED");
                mIsLoggedIn = intent.getBooleanExtra("isLoggedIn", false);
                Log.v(TAG, "hightlighted found " + mIsFlaggedBeerQuery + " isLoggedIn: " + mIsLoggedIn);

                for (int i = 0 ; i < selectionArgs.length; i++){
                    Log.v(TAG, ">>>>>> Selection Args " + selectionArgs[i]);
                    Log.v(TAG, "Container " + selectionFields);
                }
                QueryPkg qp = new QueryPkg("UFO", pullFields, selectionFields, selectionArgs, null, null, orderBy, hideMixesAndFlights, mQueryTextContent, getApplicationContext());

                if (pullFields != null) {
                    Log.v(TAG, "Creating a cursor with 'fetch'");
                    Cursor aCursor = UfoDatabaseAdapter.fetch(this);
                    KnurderApplication.setCursor(aCursor); // DRS 20161201 - Added 1 - Cursors only in application class, save query package for reQuery
                    boolean hasRecords = aCursor.moveToFirst();
                    if (aCursor.getCount() != 0) {
                        Log.v(TAG, "onCreate() running.  Cursor " + (hasRecords?"has records":"has NO RECORDS"));
                        Log.v(TAG, "isFlaggedOnly() " + QueryPkg.includesSelection("HIGHLIGHTED", this));
                        cursorRecyclerViewAdapter = new MybCursorRecyclerViewAdapter(this, aCursor, showDateInList, QueryPkg.includesSelection("HIGHLIGHTED", this));
                        cursorRecyclerViewAdapter.hasStableIds();
                        recyclerView.setAdapter(cursorRecyclerViewAdapter);
                        recyclerView.hasFixedSize();
                        // Maybe show the help dialog
                        boolean showShakerTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_SHAKER_TUTORIAL, true);
                        boolean showOnQueTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_ON_QUE_TUTORIAL, true);
                        if (savedInstanceState == null && showShakerTutorial) {
                            Intent popTutorialIntent = new Intent(RecyclerSqlbListActivity.this, PopTutorial.class);
                            popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.shaker_instructions);
                            popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.shaker_title);
                            popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_SHAKER_TUTORIAL);
                            startActivity(popTutorialIntent);

                        } else if (savedInstanceState == null && showOnQueTutorial && QueryPkg.includesSelection("HIGHLIGHTED", this)) {
                            Intent popTutorialIntent = new Intent(RecyclerSqlbListActivity.this, PopTutorial.class);
                            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                            if ("".equals(prefs.getString(TopLevelActivity.CARD_NUMBER, "")) || "".equals(prefs.getString(TopLevelActivity.CARD_PIN, ""))) {
                                popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.onque_instructions_with_auth);
                            } else {
                                popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.onque_instructions);
                            }
                            popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.onque_title);
                            popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_ON_QUE_TUTORIAL);
                            startActivity(popTutorialIntent);
                        }

                    } else {
                        manageToasts();
                    }
                } else {
                    Toast toast = Toast.makeText(this, "No Selection", Toast.LENGTH_SHORT);
                    toast.show();
                }
            }


        } catch (Exception e) {
            Log.v(TAG, "Exception in onCreate " + e.getMessage());
        }

        // Create shake detector
        getShaker(this).start((SensorManager) getSystemService(SENSOR_SERVICE));
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        Log.v("sengsational", "RecyclerSqlbListActivity.onActivityResult running with requestCode " + requestCode + ", resultCode " + resultCode);
        if (requestCode == VALIDATE_CARD) {
            Log.v(TAG, "Validate Card returned result " + resultCode);
            if (resultCode == 0) {
                // Doing this so that fields changed in the database will be updated.
                finish();
                startActivity(getIntent());
            } else {
                Log.v(TAG, "Unexpected resultCode " + resultCode);

            }
        } else if (requestCode == RecyclerSqlbListActivity.DETAIL_REQUEST) {
            Log.v(TAG, "onActivityResult fired <<<<<<<<<< resultCode:" + resultCode);

            //Integer[] changedPositions = UfoDatabaseAdapter.getChangedPositions();
            //for (Integer changedPosition : changedPositions) {
            //    Log.v(TAG, "changedPosition " + changedPosition);
            //SaucerItem aModel = null;
            //aModel = DbSqlliteAdapter.getById(cursorRecyclerViewAdapter.getItemId(changedPosition) + "");
            //Log.v(TAG, "Pulled from database and found data: " + aModel);
            //cursorRecyclerViewAdapter.notifyItemChanged(changedPosition); // <<<<<<<<<< This does not work
            //    Log.v(TAG, "See if this is really the right way to do this.");
            //Cursor anotherCursor = repository.fetchAll(null);
            //cursorRecyclerViewAdapter = new MyCursorRecyclerViewAdapter(this,anotherCursor);
            //recyclerView.setAdapter(cursorRecyclerViewAdapter);
            //}
            // repository.fetch(queryPackage, hideMixesAndFlights, this); No need to do another query since we do one each change.
            cursorRecyclerViewAdapter.changeCursor(KnurderApplication.getCursor(getApplicationContext()));  // DRS 20161201 - Added 1 - Cursors only in application class
        } else {
            String intentName = "not available";
            if (intent != null) {
                intentName = intent.getClass().getName();
            }
            Log.v(TAG, "onActivityresult with " + requestCode + ", " + resultCode + ", " + intentName);
        }
    }

    // DRS20171019 - Added method
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.v("sengsational", "THIS DOES NOT RUN - onNewIntent() ");
        setIntent(intent);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            Log.v("sengsational", "onNewIntent() ACTION_SEARCH!!! " + query);
        } else {
            Log.v("sengsational", "onNewIntent() some other intent");
        }
    }
    // DRS20171019 - Added method
    @Override
    public boolean onSearchRequested() {
        Log.v("sengsational", "THIS DOES NOT RUN  - onSearchRequested");
        return super.onSearchRequested();
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

    private void manageToasts() {
        try {
            Toast toast = Toast.makeText(this, "The list was empty.......", Toast.LENGTH_SHORT);
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
            if(QueryPkg.includesSelection("NEW_ARRIVAL", this)){
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
        //mAppCompatDelegate.onDestroy();
        Log.v("sengsational", "Closing cursor in onDestroy().");
        KnurderApplication.closeCursor();
        repository.close();
    }

    protected void onListItemClick(ListView listView, View view, int position, long id) {
        Intent intent = new Intent(RecyclerSqlbListActivity.this, BeerSlideActivity.class);
        //listView.getFirstVisiblePosition();
        Log.v("sengsational", "BeerListActivity.onListItemClick() position:" + position + " id:" + id);

        intent.putExtra(BeerSlideActivity.EXTRA_POSITION, (int) position);
        //intent.putExtra(BeerSlideActivity.EXTRA_FIRST_VISIBLE_POSITION, (int) listView.getFirstVisiblePosition());
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v("sengsational", "onResume() running");
        //Log.v("sengsational", "BLA.onResume() refreshRequired HAS NO EFFECT!!!!:" + refreshRequired);
        //if (refreshRequired) {
        //    //refreshList();
        //}
        // Start listening for shaking again
        getShaker(this).start((SensorManager) getSystemService(SENSOR_SERVICE));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v("sengsational", "onPause() running");
        if (!"".equals(mQueryTextContent)) getIntent().putExtra("queryTextContent", mQueryTextContent); // DRS 20171021 - keep text query from getting lost on screen reorientation
        // Stop listening for shaking
        getShaker(this).stop();
    }

    private ShakeDetector getShaker(RecyclerSqlbListActivity recyclerSqlbListActivity) {
        if (mShakeDetector == null) {
            mShakeDetector = new ShakeDetector(this);
        }
        return mShakeDetector;
    }

    @Override
    public void hearShake() {
        if (mRandom == null) mRandom = new Random();
        if (cursorRecyclerViewAdapter != null && cursorRecyclerViewAdapter.getItemCount() > 0) {
            int randomItem = mRandom.nextInt(cursorRecyclerViewAdapter.getItemCount());
            Log.v(TAG, "hearShake() position:" + randomItem );
            Intent intent = new Intent(RecyclerSqlbListActivity.this, BeerSlideActivity.class);
            intent.putExtra(BeerSlideActivity.EXTRA_POSITION, (int) randomItem);
            startActivityForResult(intent, RecyclerSqlbListActivity.DETAIL_REQUEST);
        }
    }

    public static void setRefreshRequired(boolean refreshRequired) {
        //RecyclerSqlbListActivity.refreshRequired = refreshRequired;
        //Log.v("sengsational","BLA.setRefreshRequired("  + refreshRequired + ")");
    }

    public static void setLastListPosition(int listPosition) {
        RecyclerSqlbListActivity.listPosition = listPosition;
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



    /*
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
    @Override public ActionMode onWindowStartingSupportActionMode(ActionMode.Callback callback) {return null;}
    */

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);


        // Here we try to limit showing the sort by price and glass size to times when it's appropriate
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(RecyclerSqlbListActivity.this);
        String hideTitle1 = "";
        String hideTitle2 = "";
        String hideTitle3 = "";
        if(!mIsTapQuery || prefs.getFloat(SCAN_COVERAGE, 0F) < 0.6F) { // If the list is less than 60% covered, don't offer the option to sort
            hideTitle1 = getResources().getString(R.string.action_sort_glass);
            hideTitle2 = getResources().getString(R.string.action_sort_price);
        }
        if (!mIsFlaggedBeerQuery || !mIsLoggedIn) {
            hideTitle3 = getResources().getString(R.string.action_push_to_brews_on_queue);
        }

        for (int i = 0; i < menu.size(); i++){
            MenuItem anItem = menu.getItem(i);
            if (hideTitle1.equals(anItem.getTitle()) || hideTitle2.equals(anItem.getTitle()) || hideTitle3.equals(anItem.getTitle())) {
                menu.getItem(i).setVisible(false);
            } else {
                menu.getItem(i).setVisible(true);
            }
        }

        //DRS20171019 ADDED all to end of method - add search widget per https://developer.android.com/guide/topics/search/search-dialog.html#UsingSearchWidget
        // Get the SearchView and set the searchable configuration
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        Log.v("sengsational", "searchManager.toString(): " + searchManager);
        MenuItem item = menu.findItem(R.id.menu_search);
        Log.v("sengsational", "item.toString(): " + item);


        //SearchView searchView = (SearchView) item.getActionView();
        //Log.v("sengsational", "searchView.toString(): " + searchView); // <<<<<<<<<searchView is null
        searchView = new SearchView(getSupportActionBar().getThemedContext());

        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setQueryRefinementEnabled(true);
        searchView.setSubmitButtonEnabled(true);
        searchView.setIconified(true);
        searchView.setIconifiedByDefault(true);
        MenuItemCompat.setActionView(item, searchView);
        String searchHint = "Search...";
        if (mQueryButtonText != null && mQueryButtonText.length() > 5) {
            searchHint = "Search" + mQueryButtonText.substring(4); // remove "List" and replace with "Search"
        }
        searchView.setQueryHint(searchHint);

        final Context lContext = this;
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                Log.v(TAG, "onQueryTextSubmit");
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.v(TAG, "onQueryTextChange [" + newText + "]");
                mQueryTextContent = newText;
                QueryPkg.setFullTextSearch(newText, lContext);
                Cursor aCursor = UfoDatabaseAdapter.fetch(getApplicationContext());
                KnurderApplication.setCursor(aCursor);
                if (cursorRecyclerViewAdapter != null) {
                    cursorRecyclerViewAdapter.setQueryText(newText);
                    cursorRecyclerViewAdapter.changeCursor(aCursor);
                }

                return false;
            }
        });

        searchMenu = item;
        // Assumes current activity is the searchable activity
        //searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        //searchView.setIconifiedByDefault(false); // Do not iconify the widget; expand it by default
        Log.v("sengsational", "searchView isIconified" + searchView.isIconified());

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Log.v("sengsational","The item selected: " + item.getTitle() + " id:" + id);
        switch (id) {
            case R.id.action_share:
                if (!repository.cursorHasRecords()){
                    Toast.makeText(getApplicationContext(), "The list was empty", Toast.LENGTH_SHORT).show();
                    break;
                }
                Log.v("sengsational","In switch: " + item.getTitle());
                Intent mailIntent = new Intent(Intent.ACTION_SEND);
                mailIntent.setType("message/rfc822");
                mailIntent.putExtra(Intent.EXTRA_SUBJECT, "Knurder Beer List");
                //mailIntent.putExtra(Intent.EXTRA_TEXT, getHtmlFromViewCursor());
                mailIntent.putExtra(Intent.EXTRA_TEXT, getTextFromViewCursor());
                mailIntent.putExtra(android.content.Intent.EXTRA_STREAM, getListContentUri(getCsvFromViewCursor()));
                try {
                    startActivityForResult(Intent.createChooser(mailIntent, "Send email.."), EMAIL_COMPLETE);
                } catch (android.content.ActivityNotFoundException ex) {
                    Log.e(TAG, "Failed to email list. " + ex.getMessage());
                    Toast.makeText(this, "NO EMAIL SERVICE AVAILABLE", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.action_sort_abv:
                manageSort(QueryPkg.ABV, true, -1);
                break;
            case R.id.action_sort_name:
                manageSort(QueryPkg.NAME, false, -1);
                break;
            case R.id.action_sort_style:
                manageSort(QueryPkg.STYLE, false, -1);
                break;
            case R.id.action_sort_glass:
                manageSort(QueryPkg.GLASS_SIZE, true, QueryPkg.GLASS_PRICE);
                break;
            case R.id.action_sort_price:
                manageSort(QueryPkg.GLASS_PRICE, true, QueryPkg.GLASS_SIZE);
                break;
            case R.id.action_push_to_brews_on_queue:
                if (!repository.cursorHasRecords()){
                    Toast.makeText(getApplicationContext(), "The list was empty", Toast.LENGTH_SHORT).show();
                    break;
                }
                Intent intent = new Intent(RecyclerSqlbListActivity.this, LoginPinActivity.class);
                intent.putExtra(Intent.EXTRA_TEXT, getBrewIdsListFromCursor());
                startActivityForResult(intent, VALIDATE_CARD);
                break;
            case R.id.menu_search: // DRS 20171019 - Added case
                Log.v("sengsational", "THIS NEVER RUNS!!!!!!!!!!!  menu_search");
                if (searchView != null) {
                    //SearchView searchView = (SearchView) item; // class cast exception
                    //Log.v("sengsational", "query refinement " + searchView.isQueryRefinementEnabled());
                    //searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
                    //searchView.setQueryRefinementEnabled(true);
                    //searchView.setIconified(false);
                    //searchView.setIconifiedByDefault(false);
                    //MenuItemCompat.setActionView(item, searchView);

                    searchView.setIconified(false);
                    startSearch("", false, null, false);
                    //searchView.setIconifiedByDefault(false);
                } else {
                    Log.v("sengsational", "searchView was null");
                }
                break;
            default:
                Log.v("sengsational", "the id: " + id);

        }
        return super.onOptionsItemSelected(item);
    }

    private void manageSearch(int name) {
        Toast.makeText(this, "Not implemented yet.",Toast.LENGTH_SHORT);
    }

    private void manageSort(int fieldNumber, boolean castInteger, int secondFieldNumber) {
        // two sorts of the same thing result in swapping asc to desc or vica verca
        int ascDesc = QueryPkg.ASC;
        if(lastSort[0] == fieldNumber && lastSort[1] == QueryPkg.ASC) { ascDesc = QueryPkg.DESC; }
        String sortParameter = QueryPkg.fields[fieldNumber] + " " + QueryPkg.fields[ascDesc];
        if (castInteger) {
            sortParameter = "CAST (" +  QueryPkg.fields[fieldNumber] + " AS FLOAT) " + QueryPkg.fields[ascDesc];
        }

        QueryPkg.setOrderBy(sortParameter, getApplicationContext());
        if (secondFieldNumber >= 0) {
            QueryPkg.setSecondSortBy("," + QueryPkg.fields[secondFieldNumber] + " " + QueryPkg.fields[ascDesc], this);
        }

        if (!"".equals(mQueryTextContent)) QueryPkg.setFullTextSearch(mQueryTextContent, this);  //DRS 20171019 - For requery
        Cursor aCursor = UfoDatabaseAdapter.fetch(this);
        KnurderApplication.setCursor(aCursor); // DRS 20161201 - Added 1 - Cursors only in application class, save query package for reQuery
        if (cursorRecyclerViewAdapter != null) cursorRecyclerViewAdapter.changeCursor(aCursor);


        //Cursor cursor = repository.query("UFO", queryPackage.pullFields, queryPackage.selectionFields, queryPackage.selectionArgs, null, null, sortParameter);
        //mResourceCursorAdapter.swapCursor(cursor);

        // update last sort
        lastSort[0] = fieldNumber;
        lastSort[1] = ascDesc;
    }

    private String getBrewIdsListFromCursor() {
        StringBuilder builder = new StringBuilder();
        Cursor cursor = repository.getCursor();
        boolean listHasItems = false;
        try {
            if (cursor.moveToFirst()) listHasItems = true; // The list was empty
        } catch (Exception e) {
            Log.e(TAG, "Unable to get brewIds from the list.");
            return "";
        }

        do {
            //Log.v(TAG, "cursor type for BREW_ID : " + cursor.getType(QueryPkg.BREW_ID) + " << expect FIELD_TYPE_STRING (3)"); // RETURNED 0
            //Log.v(TAG, "cursor type for BREW_ID:: " + cursor.getType(cursor.getColumnIndex("BREW_ID")) + " << expect FIELD_TYPE_STRING (3)"); // RETURNED 3
            //Log.v(TAG, "cursor getString name " + cursor.getString(QueryPkg.NAME)); // RETURNED THE NAME (GOOD DATA)
            //Log.v(TAG, "cursor getString column index name " + cursor.getString(cursor.getColumnIndex("NAME"))); // RETURNED THE NAME (GOOD DATA)
            //Log.v(TAG, "cursor getString brewid " + cursor.getString(QueryPkg.BREW_ID)); // RETURNED null
            //Log.v(TAG, "cursor getString column index brewid " + cursor.getString(cursor.getColumnIndex("BREW_ID"))); // RETURNED THE NUMBER  (GOOD DATA)

            builder.append(cursor.getString(cursor.getColumnIndexOrThrow("BREW_ID"))).append(",");
        } while (cursor.moveToNext());
        Log.v(TAG, "brewIdsList [" + builder.toString() + "]");
        return builder.toString();
    }

    private String getCsvFromViewCursor() {
        StringBuilder builder = new StringBuilder();
        builder.append("\"Name\",\"Style\",\"ABV\",\"Tasted\",\"Flagged\"\n");
        String createdString;
        String highlightedString;
        Cursor cursor = repository.getCursor();
        if (!cursor.moveToFirst()) return "The list was empty.....";
        do {
            builder.append("\"").append(cursor.getString(QueryPkg.NAME)).append("\",");
            builder.append("\"").append(cursor.getString(QueryPkg.STYLE)).append("\",");
            builder.append("\"").append(SaucerItem.getPctString(cursor.getString(QueryPkg.ABV))).append("\",");
            createdString = cursor.getString(QueryPkg.CREATED);
            builder.append("\"").append((createdString==null)?"":createdString).append("\",");
            highlightedString = cursor.getString(QueryPkg.HIGHLIGHTED);
            builder.append("\"").append((highlightedString==null)?"":highlightedString).append("\"\n");
        } while (cursor.moveToNext());
        return builder.toString();
    }

    // The emails refuse to be formatted as table with the default Android email stuff
    private android.text.Spanned getHtmlFromViewCursor() {
        StringBuilder builder = new StringBuilder();
        builder.append("<span><table><tr><th>Name</th><th><th>Style</th><th><th>ABV</th><th><th>Tasted</th></tr>").append("\n");
        Cursor cursor = repository.getCursor();
        String createdString;
        if (!cursor.moveToFirst()) return Html.fromHtml("The list was empty.....");
        do {
            builder.append("<tr><td>").append(cursor.getString(QueryPkg.NAME)).append("</td>");
            builder.append("<td>").append(cursor.getString(QueryPkg.STYLE)).append("</td>");
            builder.append("<td>").append(cursor.getString(QueryPkg.ABV)).append("</td>");
            createdString = cursor.getString(QueryPkg.CREATED);
            builder.append("<td>").append(createdString==null?"":createdString).append("</td></tr>").append("\n");
        } while (cursor.moveToNext());
        builder.append("</table></span>");
        return Html.fromHtml(builder.toString());
    }

    private android.text.Spanned getTextFromViewCursor() {
        StringBuilder builder = new StringBuilder();
        builder.append("<span>\nName  /  Style  /  ABV  /  Tasted<br>").append("\n");
        Cursor cursor = repository.getCursor();
        String createdString;
        String abvString;
        if (!cursor.moveToFirst()) return Html.fromHtml("The list was empty.....");
        do {
            builder.append(cursor.getString(QueryPkg.NAME)).append("  /  ");
            builder.append(cursor.getString(QueryPkg.STYLE)).append("  /  ");
            abvString = cursor.getString(QueryPkg.ABV);
            builder.append("0".equals(abvString)?"":abvString).append("  /  ");
            createdString = cursor.getString(QueryPkg.CREATED);
            builder.append(createdString==null?"":createdString).append("<br>").append("\n");
        } while (cursor.moveToNext());
        builder.append("</span>");
        return Html.fromHtml(builder.toString());
    }

    private Uri getListContentUri(String csvFromViewCursor) {
        try {
            File tempDir = getApplicationContext().getExternalCacheDir();
            String timeStamp = new SimpleDateFormat("MM-dd-mmss").format(new Date());
            File mTempFile = File.createTempFile("Knurder Beer List-" + timeStamp, ".csv", tempDir);
            FileWriter out = new FileWriter(mTempFile);
            out.write(csvFromViewCursor);
            out.close();
            // DRS 20181201 - Comment 1, Add 1
            // return Uri.fromFile(mTempFile);
            return FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", mTempFile);
        } catch (Throwable t) {
            Log.v(TAG, "Failed to getListContentUri " + t.getMessage());
        }
        return null;
    }


}

