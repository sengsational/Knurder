package com.sengsational.knurder;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;

import static com.sengsational.knurder.TopLevelActivity.STORE_NUMBER_LIST;

public class RecyclerOcrListActivity extends AppCompatActivity {
    private static final String TAG = RecyclerOcrListActivity.class.getSimpleName();

    private RecyclerView recyclerView;
    public RecyclerOcrViewAdapter cursorRecyclerViewAdapter; //<<<<<<<<<<<<<<<<<<
    private Cursor mCursor;
    private UfoDatabaseAdapter mRepository;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recycler_ocr_list);

        recyclerView = (RecyclerView) findViewById(R.id.recyclerView2);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mCursor = queryUfoLocal(mRepository, this);

        cursorRecyclerViewAdapter = new RecyclerOcrViewAdapter(this, mCursor);
        cursorRecyclerViewAdapter.hasStableIds();
        recyclerView.setAdapter(cursorRecyclerViewAdapter);
        recyclerView.hasFixedSize();
    }

    public static Cursor queryUfoLocal(UfoDatabaseAdapter repository, Context context) {
        if (repository == null) repository = new UfoDatabaseAdapter(context);
        SQLiteDatabase db = repository.openDb(context);
        String tableWithJoin = "UFO LEFT OUTER JOIN UFOLOCAL ON UFO.NAME=UFOLOCAL.NAME";
        String[] pullFields = new String[]{"UFO._id", "UFO.NAME", "UFOLOCAL.GLASS_SIZE", "UFOLOCAL.LAST_UPDATED_DATE", "UFOLOCAL.ADDED_NOW_FLAG", "UFOLOCAL.GLASS_PRICE", "UFOLOCAL._id"};
        String selectionFields = "ACTIVE=? AND CONTAINER=? AND STYLE<>? and STYLE<>? and UFO.STORE_ID=?";
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String storeId = prefs.getString(STORE_NUMBER_LIST,"13888");
        String[] selectionArgs = new String[]{"T", "draught", "Mix", "Flight", storeId};
        return db.query(tableWithJoin, pullFields,selectionFields, selectionArgs,null,null,"UFO.NAME ASC", null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        cursorRecyclerViewAdapter.changeCursor(KnurderApplication.getCursor(getApplicationContext()));
    }

    @Override
    public void onBackPressed() {
        Log.v(TAG, "onBackPressed() in RecuclerOcrListActivity");
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCursor != null) mCursor.close();
        if (mRepository != null) mRepository.close();
    }
}
