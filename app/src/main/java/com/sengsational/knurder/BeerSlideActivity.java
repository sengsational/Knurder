package com.sengsational.knurder;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.sengsational.knurder.PopTutorial.EXTRA_TEXT_RESOURCE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TITLE_RESOURCE;
import static com.sengsational.knurder.PositionActivity.RESULT_FAILED;
import static com.sengsational.knurder.TopLevelActivity.SAVE_PICTURE_EXTERNAL;

/**
 * Created by Owner on 5/17/2016.
 */
public class BeerSlideActivity extends AppCompatActivity {
    private static final String TAG = BeerSlideActivity.class.getSimpleName();
    //private static Cursor mCursor; // DRS 20161130

    public static final int PINT_GLASS = 0;
    public static final int SNIFTER_GLASS = 1;
    public static final String EXTRA_GLASS_TYPE = "extra_glass_type";
    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_FILENAME = "fileName";

    private static final int POST_PICTURE_INTENT = 1959;
    private static final int POST_HELP_TAKE_PICTURE_INTENT = 1957;
    private static final int POST_POSITION_INTENT = 1992;
    private static final int POST_HELP_RATE_BEER_INTENT = 1664;

    public static final String EXTRA_DB_KEY = "extra_db_key";
    public static final String PREF_TAKE_PICTURE_TUTORIAL = "takePictureTutorial";
    public static final String PREF_RATE_BEER_TUTORIAL = "rateBeerTutorial";
    public static final String EXTRA_TUTORIAL_TYPE = "tutorialType";
    public static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1942;
    public static final String CURRENT_FILE_NAME_KEY = "mCurrentFileName";

    private String mCurrentFileName;
    private RelativeLayout mBeerLayout;
    private int mPosition = 0;
    private int mGlassType = PINT_GLASS;


    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mCurrentFileName = savedInstanceState.getString(CURRENT_FILE_NAME_KEY);
        }

        Log.v("sengsational", "BeerSlideActivity.onCreate().  position: " + (Integer) getIntent().getExtras().get(EXTRA_POSITION));

        // DRS 20161130 - Added 3
        try { mPosition = (Integer) getIntent().getExtras().get(EXTRA_POSITION); } catch (Throwable t) {Log.e(TAG, "unable to get " + EXTRA_POSITION);}
        //mCursor = UfoDatabaseAdapter.getCursor();
        if (KnurderApplication.getCursor(getApplicationContext()) != null) {
            Cursor cursor = KnurderApplication.getCursor(getApplicationContext());
            cursor.moveToPosition(mPosition);
        }

        setContentView(R.layout.activity_screen_slide);
        ViewPager mPager = (ViewPager)findViewById(R.id.pager);
        PagerAdapter mPagerAdapter = new BeerSlidePageAdapter(getSupportFragmentManager(), getApplicationContext());
        mPager.setAdapter(mPagerAdapter);
        mPager.setCurrentItem(mPosition);
        Log.v(TAG, "mPager current item: " + mPager.getCurrentItem());

        // DRS 20161130 firstVisiblePosition = (int) getIntent().getExtras().get(EXTRA_FIRST_VISIBLE_POSITION);

    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putString(CURRENT_FILE_NAME_KEY, mCurrentFileName);
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean canTakePictures = PreferenceManager.getDefaultSharedPreferences(BeerSlideActivity.this).getBoolean(SAVE_PICTURE_EXTERNAL, true);
        if (canTakePictures) {
            getMenuInflater().inflate(R.menu.menu_display_description_activity, menu);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        Log.v("sengsational", "Item selected: " + item.getTitle());

        // Must set the current position in order for the right beer description page to be used
        ViewPager mPager = (ViewPager)findViewById(R.id.pager);
        mPosition = mPager.getCurrentItem();

        switch (item.getItemId()) {
            case R.id.take_picture:

                boolean takePictureTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_TAKE_PICTURE_TUTORIAL, true);
                Log.v(TAG, "looked up " + PREF_TAKE_PICTURE_TUTORIAL + " and got back " + takePictureTutorial);
                if (takePictureTutorial) {
                    Intent popTutorialIntent = new Intent(BeerSlideActivity.this, PopTutorial.class);
                    popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.take_picture_instructions);
                    popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.take_picture_title);
                    popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_TAKE_PICTURE_TUTORIAL);
                    startActivityForResult(popTutorialIntent, POST_HELP_TAKE_PICTURE_INTENT);
                } else {
                    onActivityResult(POST_HELP_TAKE_PICTURE_INTENT, RESULT_OK, null);
                }
                break;

            case R.id.rate_beer:
                boolean rateBeerTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_RATE_BEER_TUTORIAL, true);
                Log.v(TAG, "looked up " + PREF_RATE_BEER_TUTORIAL + " and got back " + rateBeerTutorial);
                if (rateBeerTutorial) {
                    Intent popTutorialIntent = new Intent(BeerSlideActivity.this, PopTutorial.class);
                    popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.rate_beer_instructions);
                    popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.rate_beer_title);
                    popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_RATE_BEER_TUTORIAL);
                    startActivityForResult(popTutorialIntent, POST_HELP_RATE_BEER_INTENT);
                } else {
                    onActivityResult(POST_HELP_RATE_BEER_INTENT, RESULT_OK, null);
                }
                break;
            case R.id.use_default:
                try {
                    FileHelper fileHelper = new FileHelper();
                    if (Math.random() > 0.01) {
                        mCurrentFileName = (fileHelper.getFileNameForTesting("Snifter"));
                        mGlassType = SNIFTER_GLASS;
                    } else {
                        mCurrentFileName = (fileHelper.getFileNameForTesting("Pint"));
                        mGlassType = PINT_GLASS;
                    }
                    Log.v(TAG, "mCurrentFileName: " + mCurrentFileName);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.v(TAG, "Problem using default picture: " + e.getMessage());
                    mCurrentFileName = null;
                }
                Log.v(TAG, "Starting PositionActivity intent.");
                Intent intent = new Intent(BeerSlideActivity.this, PositionActivity.class);
                intent.putExtra(EXTRA_FILENAME, mCurrentFileName);
                intent.putExtra(EXTRA_POSITION, mPosition);
                intent.putExtra(EXTRA_GLASS_TYPE, mGlassType);
                startActivityForResult(intent, POST_POSITION_INTENT);
                break;
        }
        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.v(TAG, "onActivityResult");
        switch (requestCode) {
            case POST_PICTURE_INTENT:
                if (resultCode == RESULT_OK) {
                    Log.v(TAG, "Starting PositionActivity intent.");
                    Intent intent = new Intent(BeerSlideActivity.this, PositionActivity.class);
                    intent.putExtra(EXTRA_FILENAME, mCurrentFileName);
                    intent.putExtra(EXTRA_POSITION, mPosition);
                    intent.putExtra(EXTRA_GLASS_TYPE, mGlassType);
                    startActivityForResult(intent, POST_POSITION_INTENT);
                } else {
                    // Not using the picture.  Try to delete it.
                    FileHelper.deleteTheFile();
                }
                break;
            case POST_HELP_TAKE_PICTURE_INTENT:
                if (resultCode == RESULT_OK) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);

                    builder.setTitle("Select Glass Shape");
                    builder.setMessage("Please select the type of glass that you are about to take a picture of.");
                    builder.setPositiveButton("Pint", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mGlassType = PINT_GLASS;
                            runPermissionsForPictureIntent();
                        }
                    });
                    builder.setNegativeButton("Snifter", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            mGlassType = SNIFTER_GLASS;
                            runPermissionsForPictureIntent();
                        }
                    });
                    builder.show();

                } else {
                    Log.v(TAG, "Post help intent on activity result. NO GO. resultCode: " + resultCode);
                }

                break;
            case POST_POSITION_INTENT:
                if (resultCode == RESULT_OK) {
                    String fileName = data.getStringExtra(EXTRA_FILENAME);
                    Log.v(TAG, "Starting Share intent.  fileName: " + fileName);
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("image/jpeg");

                    //share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(fileName))); // Line caused android.os.FileUriExposedException
                    File filePassedIn = new File(fileName);
                    Log.v(TAG, "exists: " + filePassedIn.exists() + " canRead: " + filePassedIn.canRead());
                    Uri uriForFile = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", filePassedIn);
                    share.putExtra(Intent.EXTRA_STREAM, uriForFile);
                    share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                    Intent chooser = Intent.createChooser(share, "Share Image");
                    List<ResolveInfo> resInfoList = this.getPackageManager().queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY);

                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        this.grantUriPermission(packageName, uriForFile, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    startActivity(chooser);
                } else if (resultCode == RESULT_FAILED) {
                    android.app.AlertDialog.Builder noFileDialog = new android.app.AlertDialog.Builder(this);
                    noFileDialog.setMessage("There was a problem saving the image file to storage. ");
                    noFileDialog.setCancelable(true);
                    noFileDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            onBackPressed();
                        }
                    });
                    noFileDialog.create().show();
                    FileHelper.deleteTheFile();
                } else if (resultCode == RESULT_CANCELED) {
                    FileHelper.deleteTheFile();
                } else {
                    Log.v(TAG, "ERROR: Some other resultCode: " + resultCode);
                    FileHelper.deleteTheFile();
                }
                break;
            case POST_HELP_RATE_BEER_INTENT:
                Intent rateBeerIntent = new Intent(BeerSlideActivity.this, RateBeerActivity.class);
                rateBeerIntent.putExtra(EXTRA_POSITION, mPosition);
                startActivity(rateBeerIntent);
                break;
        } // switch
    }

    private void runPermissionsForPictureIntent() {
        String imagePermissionByApi = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)?Manifest.permission.READ_MEDIA_IMAGES:Manifest.permission.READ_EXTERNAL_STORAGE;
        final String[] permissionStringArray = new String[] {imagePermissionByApi, Manifest.permission.CAMERA};
        boolean permissionsBoolean = false;
        int storageCheck = ContextCompat.checkSelfPermission(this, imagePermissionByApi);
        int cameraCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        if (storageCheck == PackageManager.PERMISSION_GRANTED && cameraCheck == PackageManager.PERMISSION_GRANTED) {
            Log.v(TAG, "Both permissions were granted");
            permissionsBoolean = true;
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,  permissionStringArray[0]) || ActivityCompat.shouldShowRequestPermissionRationale(this,  permissionStringArray[1])) {
            Log.v(TAG, "At least one permission should show rationale");
            showSnackBar(this, R.string.external_storage_permission_rationale, permissionStringArray);
            permissionsBoolean = false;
        }  else {
            Log.v(TAG, "storageCheck was " + storageCheck + " and cameraCheck was " + cameraCheck + ".  Now requesting permissions.");
            ActivityCompat.requestPermissions(this, permissionStringArray,MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            permissionsBoolean = false;
        }

        if (permissionsBoolean) {
            Log.v(TAG, ">>>>>>>>> Both permissions are ok, starting from runPermissionsForPictureIntent <<<<<<<<<<<");
            reallyDoPictureIntent();
        }
    }

    public static void showSnackBar(final Activity activity, int messageId, final String[] permissionStringArray) {
        View rootView = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        Snackbar snackbar = Snackbar.make(rootView, messageId, Snackbar.LENGTH_INDEFINITE).setAction(android.R.string.ok, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ActivityCompat.requestPermissions(activity, permissionStringArray,MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            }
        });
        View snackbarView = snackbar.getView();
        //TextView snackTextView = (TextView) snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
        //snackTextView.setMaxLines(9);
        snackbar.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, ">>>>>>>>> External permissions granted.  Running from onRequestPermissionsResult <<<<<<<<<<<");
                    reallyDoPictureIntent();
                } else {
                    Log.v(TAG, "External permissions denied");
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    SharedPreferences.Editor prefsEdit = prefs.edit();
                    prefsEdit.putBoolean(SAVE_PICTURE_EXTERNAL, false);
                    prefsEdit.apply();
                    Toast.makeText(this, "The ability to take pictures will be removed.", Toast.LENGTH_LONG).show();
                }
                break;
            default:
                Log.v(TAG, "DEFAULT RUNNING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    private void reallyDoPictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                File tempPhoto = FileHelper.createTempPhotoFile();
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", tempPhoto));
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mCurrentFileName = (FileHelper.getFileNameStringForTempPhotoFile(false));
                Log.v(TAG, "mCurrentFileName = " + mCurrentFileName);
                KnurderApplication.setContext(this);
            } else if (takePictureIntent.resolveActivity(getPackageManager()) != null){
                //takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(FileHelper.createTempPhotoFile())); // DRS 20181026 - Quit working when I targeted API 26
                File tempPhoto = FileHelper.createTempPhotoFile();
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", tempPhoto));
                takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                mCurrentFileName = (FileHelper.getFileNameStringForTempPhotoFile(false));
                Log.v(TAG, "mCurrentFileName = " + mCurrentFileName);
                KnurderApplication.setContext(this);
            } else {
                Log.v(TAG, "no camera available");
                KnurderApplication.setContext(this);
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.v(TAG, "Tragedy struck. " + e.getMessage());
            mCurrentFileName = null;
        }
        Log.v(TAG, "starting takePictureInent");
        startActivityForResult(takePictureIntent, POST_PICTURE_INTENT);
    }


    private static class BeerSlidePageAdapter extends FragmentStatePagerAdapter {

        int count;

        public BeerSlidePageAdapter(FragmentManager fm, Context applicationContext){
            super(fm);
            // DRS 20161130 - Commented 1, Added 1
            if (KnurderApplication.getCursor(applicationContext) != null) this.count = KnurderApplication.getCursor(applicationContext).getCount();
            else Log.v(TAG, "Cursor was null in BeerSlidePageAdapter.");
        }

        @Override
        public int getCount() {
            return this.count;
        }


        @Override
        public Fragment getItem(int position) {
            //Log.v("sengsational", "BeerSlideActivity.getItem with passed in position " + position);
            //return BeerSlideFragment.create(position);
            // DRS 20161130 - Commented 1, Added 1
            //return BeerSlideFragment.newInstance(position, firstVisiblePosition);
            return BeerSlideFragment.newInstance(position, R.layout.activity_beer);
        }

    }
}
