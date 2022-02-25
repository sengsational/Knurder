package com.sengsational.knurder;

import android.Manifest;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDelegate;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import static com.sengsational.knurder.BeerSlideActivity.MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE;
import static com.sengsational.knurder.BeerSlideActivity.PREF_RATE_BEER_TUTORIAL;
import static com.sengsational.knurder.BeerSlideActivity.PREF_TAKE_PICTURE_TUTORIAL;
import static com.sengsational.knurder.BeerSlideActivity.showSnackBar;
import static com.sengsational.knurder.KnurderApplication.reviewUploadWarning;
import static com.sengsational.knurder.OcrBase.PREF_OCR_BASE_TUTORIAL;
import static com.sengsational.knurder.OcrBase.PREF_OCR_MENU_TUTORIAL;
import static com.sengsational.knurder.OcrBase.PREF_OCR_NEW_TUTORIAL;
import static com.sengsational.knurder.OcrBase.PREF_TOUCHLESS_TUTORIAL;
import static com.sengsational.knurder.PositionActivity.PREF_POSITION_TUTORIAL;
import static com.sengsational.knurder.RecyclerSqlbListActivity.PREF_SHAKER_TUTORIAL;
import static com.sengsational.knurder.TopLevelActivity.NEW_FEATURE_ALERT_MESSAGE;
import static com.sengsational.knurder.TopLevelActivity.SAVE_PICTURE_EXTERNAL;

/**
 * Created by Dale Seng on 6/4/2016.
 */
public class SettingsActivitySimple extends AppCompatPreferenceActivity {
    private static final String TAG = SettingsActivitySimple.class.getSimpleName();

    private final SettingsActivitySimple settingsActivitySimple = this;
    private SwitchPreference mAllowPicturesSwitchPreference;
    private boolean mOriginalDarkModeSetting;

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.pref_general);
        mOriginalDarkModeSetting = PreferenceManager.getDefaultSharedPreferences(settingsActivitySimple).getBoolean("dark_mode_switch", false);

    }

    @Override
    protected void onStop() {
        if (PreferenceManager.getDefaultSharedPreferences(settingsActivitySimple).getBoolean("dark_mode_switch", false) != mOriginalDarkModeSetting) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(settingsActivitySimple);
            SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.putBoolean("dark_mode_has_changed", true);
            prefsEdit.apply();
        }
        super.onStop();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        if ("current_beers_switch".equals(preference.getKey())) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Refresh Required");
            builder.setMessage("Changing this setting takes effect the next time you refresh your tasted list.  You can do this from the app's three dot menu.");
            builder.setCancelable(true);
            builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
        } else if ("automatic_untappd_switch".equals(preference.getKey())) {

            if (((SwitchPreference)preference).isChecked()) { // They are turning it on!!!
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(settingsActivitySimple);
                if ("".equals(prefs.getString("untappd_url",""))) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Scan QR Code First");
                    builder.setMessage("Knurder doesn't know where to get the menu data for your Saucer until you scan the Touchless QR code.\n\nScan the Touchless Menu QR code (should be on the table at your Saucer).\n\nGo to the menu (the three dots) then 'Scan the Menu' > 'Scan the Touchless Menu'");
                    builder.setCancelable(true);
                    builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                        }
                    });
                    builder.create().show();
                    SharedPreferences.Editor prefsEdit = prefs.edit();
                    prefsEdit.putBoolean("automatic_untappd_switch", false);
                    prefsEdit.apply();
                }
            }
        } else if ("upload_reviews_switch".equals(preference.getKey()) && reviewUploadWarning) {
            reviewUploadWarning = false; // just show once per session
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Reviews Considerations");
            builder.setMessage("Saving your reviews to the Saucer site means you CAN NO LONGER EDIT THEM!  But if you don't save your reviews to the Saucer and you happen to change phones or reinstall Knurder, all your reviews will be gone-gone!  It's up to you.");
            builder.setCancelable(true);
            builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    dialog.dismiss();
                }
            });
            builder.create().show();
        }else if ("dark_mode_switch".equals(preference.getKey())) {
            if (((SwitchPreference)preference).isChecked()) { //They are turning it on
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else{ //They are turning it off
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
            Log.v("sengsational", "dark mode switch pressed.");
            boolean newSetting = PreferenceManager.getDefaultSharedPreferences(settingsActivitySimple).getBoolean(preference.getKey(), false);
        } else if ("allow_external_picture_storage_switch".equals(preference.getKey())) {
            mAllowPicturesSwitchPreference = (SwitchPreference)preference;
            //Log.v(TAG, "The preference thing was: " + mAllowPicturesSwitchPreference.isChecked() + "... " + preference.getClass().getName() + " ... " + preference.toString());
            if (mAllowPicturesSwitchPreference.isChecked()) { // They are turning it on!!!
                final String[] permissionStringArray = new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
                boolean permissionsBoolean = false;
                int storageCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                int cameraCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

                if (storageCheck == PackageManager.PERMISSION_GRANTED && cameraCheck == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, "Settings Activity - Both permissions were granted");
                    permissionsBoolean = true;
                } else if (ActivityCompat.shouldShowRequestPermissionRationale(this,  permissionStringArray[0]) || ActivityCompat.shouldShowRequestPermissionRationale(this,  permissionStringArray[1])) {
                    Log.v(TAG, "Settings Activity - At least one permission should show rationale");
                    showSnackBar(this, R.string.external_storage_permission_rationale, permissionStringArray);
                }  else {
                    Log.v(TAG, "Settings Activity - storageCheck was " + storageCheck + " and cameraCheck was " + cameraCheck + ".  Now requesting permissions.");
                    ActivityCompat.requestPermissions(this, permissionStringArray,MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
                }

                storageCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                cameraCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

                if (permissionsBoolean || (storageCheck == PackageManager.PERMISSION_GRANTED && cameraCheck == PackageManager.PERMISSION_GRANTED)) {
                    Log.v(TAG, ">>>>>>>>> Both permissions are ok. <<<<<<<<<<<");
                } else {
                    Log.v(TAG, ">>>>>>>>> Both permissions are NOT ok. <<<<<<<<<<<");
                }
            } else {
                // They are turning it off.  Do nothing.
            }
        }
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    Log.v(TAG, ">>>>>>>>> External permissions granted.  Leave switch as-is. <<<<<<<<<<<");
                } else {
                    if (mAllowPicturesSwitchPreference != null) {
                        Log.v(TAG, "External permissions denied.  Flip switch back.");
                        mAllowPicturesSwitchPreference.setChecked(false);
                    } else { // Couldn't do it
                        Log.v(TAG, "External permissions denied.  Couldn't flip switch back directly, so doing it using editor.");
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        SharedPreferences.Editor prefsEdit = prefs.edit();
                        prefsEdit.putBoolean(SAVE_PICTURE_EXTERNAL, false);
                        prefsEdit.apply();
                    }
                    Toast.makeText(this, "If you want pictures in Knurder, I'm afraid you'll need to allow those permissions.", Toast.LENGTH_LONG).show();
                }
                break;
            default:
                Log.v(TAG, "DEFAULT RUNNING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        }
    }

    public void resetHelp(View view) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(settingsActivitySimple);
        SharedPreferences.Editor prefsEdit = prefs.edit();
        prefsEdit.putBoolean(PREF_POSITION_TUTORIAL, true);
        prefsEdit.putBoolean(PREF_TAKE_PICTURE_TUTORIAL, true);
        prefsEdit.putBoolean(PREF_SHAKER_TUTORIAL, true);
        prefsEdit.putBoolean(PREF_OCR_BASE_TUTORIAL, true);
        prefsEdit.putBoolean(PREF_OCR_MENU_TUTORIAL, true);
        prefsEdit.putBoolean(PREF_OCR_NEW_TUTORIAL, true);
        prefsEdit.putBoolean(PREF_TOUCHLESS_TUTORIAL, true);
        prefsEdit.putBoolean(PREF_RATE_BEER_TUTORIAL, true);
        prefsEdit.remove(NEW_FEATURE_ALERT_MESSAGE);
        prefsEdit.apply();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "onDestroy()");
    }
}
