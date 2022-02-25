package com.sengsational.knurder;

import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;


public class SelectStoreActivity extends ListActivity {

    private SharedPreferences prefs;
    private HashMap<String, String> storeMap = new HashMap<String, String>();
    //private String mCardValue;
    private final Intent mIntent = new Intent();
    private TextView textView;
    private List<String> storeNames;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_store);

        // Define size of pop-up window
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = (int)(dm.widthPixels * 0.6);
        int height = (int)(dm.heightPixels * 0.6);
        getWindow().setLayout(width,height);

        // Pull data from preferences
        //prefs = PreferenceManager.getDefaultSharedPreferences(this);
        //final String defaultStoreNumber = prefs.getString(TopLevelActivity.STORE_NUMBER, "");
        //mCardValue = prefs.getString(TopLevelActivity.CARD_NUMBER, "");
        StoreNameHelper storeNameHelper = StoreNameHelper.getInstance();
        storeNameHelper.confirmLocationsLoadedInDatabase();
        storeNames = storeNameHelper.getSortedStoreNames();
        storeMap = StoreNameHelper.getInstance().getStoreMap();

        ArrayAdapter<String> myAdapter = new ArrayAdapter<String>(this, R.layout.row_layout, R.id.listText, storeNames);
        setListAdapter(myAdapter);

    }

    @Override
    protected void onListItemClick(ListView list, View view, int position, long id){
        super.onListItemClick(list, view, position, id);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        String selectedItem = (String)getListView().getItemAtPosition(position);
        String selectedNumber = StoreNameHelper.getInstance().getStoreNumberFromName(selectedItem);
        String previousStoreName = prefs.getString(TopLevelActivity.STORE_NAME, TopLevelActivity.DEFAULT_STORE_NAME);
        Log.v("sengsational", "selectedItem: " + selectedItem + "  existingItem: " + previousStoreName);
        if (previousStoreName.equals(selectedItem)){
            Toast.makeText(this, "Same Location!", Toast.LENGTH_SHORT);
            finish();
        } else {
            SharedPreferences.Editor edit = prefs.edit();
            Log.v("sengsational","store name put " + selectedItem);
            edit.putString(TopLevelActivity.STORE_NAME, selectedItem);
            edit.putString(TopLevelActivity.STORE_NUMBER, selectedNumber);
            edit.apply();
            mIntent.putExtra("store_name", selectedItem);
            mIntent.putExtra("store_number", selectedNumber);
            mIntent.putExtra("check_quiz", "N"); // TopLevelActivity.onActivityResult
            setResult(RESULT_OK, mIntent);
            finish();
        }
    };

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Log.v("sengsational", "SelectStoreActivity.onBackPressed fired");
        mIntent.putExtra("clear_tasted", false);
        setResult(RESULT_CANCELED, mIntent);
        finish();
    }

}
