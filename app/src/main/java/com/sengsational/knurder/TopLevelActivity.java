package com.sengsational.knurder;
//Check source control 10/25/2021
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.tasks.OnCompleteListener;
import com.google.android.play.core.tasks.OnFailureListener;
import com.google.android.play.core.tasks.Task;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class TopLevelActivity extends AppCompatActivity implements DataView {
    private static final String TAG = TopLevelActivity.class.getSimpleName();

    // Preferences keys definitions
    public static final String AUTHENTICATION_NAME = "cardNumberPref"; // NOTE: This preference represents authentication name, not card number.
    public static final String SAVE_PASSWORD = "savePasswordPref";
    public static final String PASSWORD = "passwordPref";
    public static final String MOU = "mouPref";
    public static final String STORE_NUMBER = "storeNumberPref";
    public static final String STORE_NUMBER_LOGON = "storeNumberLogonPref";
    public static final String USER_NAME = "userNamePref";
    public static final String TASTED_COUNT = "tastedCountPref";
    public static final String STORE_NAME = "storeNamePref";
    public static final String LAST_LIST_DATE = "listDatePref";
    public static final String LAST_TASTED_DATE = "tastedDatePref";
    public static final String UPGRADE_MESSAGE_PRESENTED = "updateMessagePref";
    public static final String LAST_QUIZ_MS = "lastQuizMsPref";
    public static final String EMAIL_ADDRESS = "emailPref";
    public static final String FIRST_NAME = "firstNamePref";
    public static final String LAST_NAME = "lastNamePref";
    public static final String SAVE_PICTURE_EXTERNAL = "allow_external_picture_storage_switch";
    public static final String UBER_EATS_LINK = "uberEatsLinkPref";
    public static final String LOGIN_ALERT_MESSAGE = "loginAlertMessagePref";
    public static final String NEW_FEATURE_ALERT_MESSAGE = "newFeatureAlertMessagePref";
    public static final String NEW_FEATURE_ALERT_DATE = "20220801"; // TODO: Change this to trigger the new feature alert
    // DRS 20210827 - Added 3 - Card Number Authentication
    public static final String CARD_NUMBER = "cardNumberActualPref";
    public static final String SAVE_CARD_PIN = "saveCardPinPref";
    public static final String CARD_PIN = "cardPinPref";

    // Presentation mode is controlled by PRESENTATION_MODE preference
    public static final String PRESENTATION_MODE = "presentationModePref";
    public static final String NO_STORE_PRESENTATION = "noStorePresenetation";
    public static final String STORE_PRESENTATION = "storePresenetation";
    public static final String USER_PRESENTATION = "userPresenetation";

    public static final String DEFAULT_STORE_NAME = "The Flying Saucer";
    public static final int SET_STORE_CALLBACK = 1959;
    public static final int SET_PRESENTATION_MODE = 1957;
    public static final int SETTINGS_ACTIVITY = 1955;
    public static final int UPDATE_GLASS_CALLBACK = 1953;
    public static final long ONE_DAY_IN_MS = 86400000L; //86400000L;

    // DRS 20161208 - Query by icon feature
    public static final String QUERY_CONTAINER = "queryContainerPref";
    public static final String QUERY_TASTED = "queryTastedPref";
    public static final String QUERY_GEOGRAPHY = "queryGeographyPref";
    public static int colorIconNonActive;
    public static int colorIconActive;
    public static boolean mWeHavePaused = false;

    // control items populated in onCreate
    private StoreListPresenter storeListPresenter;
    private TastedListPresenter tastedListPresenter;

    public static SharedPreferences prefs;

    ReviewInfo reviewInfo;
    ReviewManager manager;

    Context context;
    View topLevelView;
    View progressView;

    TextView locationTextView;
    TextView userNameView;
    TextView tastedCountView;
    TextView saucerNameView;
    Button localBeersNotTastedButton;
    Button localTapsNotTastedButton;
    Button allTapsNotTastedButton;
    Button tastedListButton;
    Button allBeersButton;
    Button allTapsButton;
    Button localBeersButton;
    Button flaggedBeersButton;
    Button localTapsButton;
    Button newArrivalsButton;
    Button uberEatsButton;

    // DRS 20161208 - Query by icon feature
    View tapIcon;
    View bottleIcon;
    View spacerIcon;
    View tastedIcon;
    View untastedIcon;
    View localIcon;
    View worldIcon;
    Button listBeersButton;


    private static HashMap<String, MenuItem> menuMap = new HashMap<>(); //Loaded when settings gets created
    private int mProgressStacker = 0;

    @Override
    protected void onPause() {
        Log.v("sengsational", "onPause() running");
        mWeHavePaused = true;
        super.onPause();
    }

    @Override
    protected void onResume() {
        Log.v("sengsational", "onResume() running");
        // Only run this if we're resuming after a pause, because a resume happens early, and we don't want a dialog then.
        if (mWeHavePaused) {
            Log.v("sengsational", "onResume() passed test 0");
            String newFeatureAlertPreference = prefs.getString(NEW_FEATURE_ALERT_MESSAGE,""); // newFeatureAlertPreference will be blank, or the DATE of the last new feature alert
            boolean newFeatureAlertPreferenceIsDoneAlready = NEW_FEATURE_ALERT_DATE.equals(newFeatureAlertPreference);
            boolean useAlternateAlertText = UntappdHelper.getInstance().getUntappdUrlForCurrentStore("").equals(""); // This variable and the 'if' block that uses it can be removed later.
            if (!newFeatureAlertPreferenceIsDoneAlready) {
                Log.v("sengsational", "onResume() passed test 1");
                android.app.AlertDialog.Builder logonDialog = new android.app.AlertDialog.Builder(this);
                //logonDialog.setMessage("DONNIE DARK MODE\n\nI'm a few years late on this feature, but if you find yourself grappling for your blue light filter glasses every time you open Knurder, you're in for a treat.\n\nFrom the starting page, go to the 'three dot' menu, select settings, and you'll find a \"Dark Theme\" switch.\n\nWe'll now return you to your regular drinking.");
                logonDialog.setMessage(Html.fromHtml("<p>" + getResources().getString(R.string.new_feature_alert_title) + "</p><p>" + getResources().getString(R.string.new_feature_alert_message) + "</p>"));
                // override message if they have never scanned the QR code and haven't populated the Untappd URL.
                if (useAlternateAlertText) {
                    logonDialog.setMessage(Html.fromHtml("<p>" + getResources().getString(R.string.new_feature_alert_title_alt) + "</p><p>" + getResources().getString(R.string.new_feature_alert_message_alt) + "</p>"));
                }
                logonDialog.setCancelable(true);
                logonDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString(NEW_FEATURE_ALERT_MESSAGE, NEW_FEATURE_ALERT_DATE);
                        editor.apply();
                        dialog.dismiss();
                    }
                });
                Log.v("sengsational", "onResume() showing dialog");
                android.app.AlertDialog newFeatureAlert = logonDialog.create();
                newFeatureAlert.show();
                // The following allows links to be clickable
                TextView aView = (TextView)newFeatureAlert.findViewById(android.R.id.message);
                aView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }

        // This is to get dark mode to show after the user makes a change to the setting
        Log.v("sengsational", AppCompatDelegate.getDefaultNightMode() + " < night mode on resume.");
        if (prefs.getBoolean("dark_mode_has_changed", false)) {
            Log.v("sengsational", "<<<<<<<<dark mode change<<<<<<<<<<<");
            // Clear
            SharedPreferences.Editor prefsEdit = prefs.edit();
            prefsEdit.putBoolean("dark_mode_has_changed", false);
            prefsEdit.apply();

            //Handle color setting
            setQueryIconColors();

        }
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_top_level_activity);

        storeListPresenter = new StoreListPresenterImpl(this);  // Creates a WebResultListener
        tastedListPresenter = new TastedListPresenterImpl(this); // Creates a WebResultListener

        context = getApplicationContext(); KnurderApplication.setContext(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(TopLevelActivity.this);
        manager = ReviewManagerFactory.create(this);

        if (savedInstanceState == null) { // When starting app for the first time with or without preferences set
            dumpPrefs(); // <<<<<<<<<<<<<For debugging only.  Remove when removing Log.v("sengsational", items
            boolean wipeDatabaseForTesting = false;
            if (wipeDatabaseForTesting){
                Log.e("sengsational", "ERASING EVERYTHING IN DATABASE NAME FOR DEBUG");
                UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(context) ;
                SQLiteDatabase db = ufoDatabaseAdapter.openDb(this);                                     //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
                db.execSQL("drop table if exists UFO");
                db.execSQL("drop table if exists UFOLOCAL");
                db.execSQL("drop table if exists LOCATIONS");
                try {db.close();} catch (Throwable t){};                                //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
            }
            boolean forceNewUserStateForTesting = false;
            if (!forceNewUserStateForTesting) { // Normally, during non-test runs
                // we need to set the correct presentation mode below
            } else {
                Log.e("sengsational", "ERASING EVERYTHING IN PREFS NAME FOR DEBUG");
                Map<String, ?> allPrefs = prefs.getAll();
                SharedPreferences.Editor editor = prefs.edit();
                Iterator<String> apIter = allPrefs.keySet().iterator();
                while (apIter.hasNext()){
                    editor.remove(apIter.next());
                }
                editor.apply();
            }
            boolean setListOldForTesting = false;
            if (setListOldForTesting) {
                SharedPreferences.Editor editor = prefs.edit();
                editor.putLong(TopLevelActivity.LAST_TASTED_DATE,  (new Date().getTime() - (5 *  86400000L)));
                editor.putLong(TopLevelActivity.LAST_LIST_DATE,  (new Date().getTime() - (5 *  86400000L)));
                editor.apply();
            }

            // Set to true for TESTING ONLY!
            boolean wipePreferenceForXXX = false; //TODO: Set to false
            if (wipePreferenceForXXX) {
                SharedPreferences.Editor editor = prefs.edit();
                //editor.remove(TopLevelActivity.UBER_EATS_LINK);
                //editor.remove("dark_theme_switch");
                //editor.remove(NEW_FEATURE_ALERT_MESSAGE);
                editor.remove(CARD_NUMBER);
                editor.remove(CARD_PIN);
                editor.apply();
            }
        }

        topLevelView = findViewById(R.id.content_top_level_activity_v2);
        progressView = findViewById(R.id.top_progress);

        // DRS 20161006 - Add 2 - Allow Change Location while logged-in
        locationTextView = ((TextView) findViewById(R.id.location_text));
        locationTextView.setText(prefs.getString(STORE_NAME, DEFAULT_STORE_NAME));

        userNameView = ((TextView) findViewById(R.id.userName));
        userNameView.setText(prefs.getString(USER_NAME, ""));
        tastedCountView = ((TextView) findViewById(R.id.tastedCount));
        tastedCountView.setText("Tasted " + prefs.getString(TASTED_COUNT, "?") + " so far.");
        saucerNameView = ((TextView) findViewById(R.id.saucerName));
        saucerNameView.setText(prefs.getString(STORE_NAME, DEFAULT_STORE_NAME));
        localBeersNotTastedButton = ((Button) findViewById(R.id.local_beers_nt_button));
        localTapsNotTastedButton = ((Button) findViewById(R.id.local_taps_nt_button));
        allTapsNotTastedButton = ((Button) findViewById(R.id.all_taps_nt_button));
        tastedListButton = ((Button) findViewById(R.id.tasted_list_button));
        allBeersButton = ((Button) findViewById(R.id.all_beers_button));
        allTapsButton = ((Button) findViewById(R.id.all_taps_button));
        localBeersButton = ((Button) findViewById(R.id.local_beers_button));
        flaggedBeersButton = ((Button) findViewById(R.id.flagged_beers_button));
        localTapsButton = ((Button) findViewById(R.id.local_taps_button));
        newArrivalsButton = ((Button) findViewById(R.id.new_arrival_button));
        listBeersButton = ((Button) findViewById(R.id.list_beers_button));
        uberEatsButton = ((Button) findViewById(R.id.uber_eats_button));

        // DRS 20161208 - Query by icon feature
        tapIcon = findViewById(R.id.tap_icon);
        bottleIcon = findViewById(R.id.bottle_icon);
        spacerIcon = findViewById(R.id.spacer_icon1);
        tastedIcon = findViewById(R.id.tasted_icon);
        untastedIcon = findViewById(R.id.untasted_icon);
        localIcon = findViewById(R.id.local_icon);
        worldIcon = findViewById(R.id.world_icon);
        listBeersButton = (Button) findViewById(R.id.list_beers_button);
        setQueryIconColors();

        // DRS 20161208 - Query by icon feature
        // Set the text on the list beers button based on saved preferences
        String iconStatusString = prefs.getString(QUERY_CONTAINER, "B") + prefs.getString(QUERY_TASTED, "B") + prefs.getString(QUERY_GEOGRAPHY, "B");
        int aResource = getResources().getIdentifier(iconStatusString, "string", "com.sengsational.knurder");
        if (aResource != 0) {
            listBeersButton.setText(aResource);
            Log.v(TAG, "listBeersButton text [" + listBeersButton.getText() + "]");
        }

        setLegacyButtonVisibility();
        setUbereatsButtonVisibility();

        switch (prefs.getString(PRESENTATION_MODE,NO_STORE_PRESENTATION)) {
            case NO_STORE_PRESENTATION:
                setToNoStorePresentation();
                break;
            case STORE_PRESENTATION:
                boolean clearUserData = false;
                setToStorePresentation(clearUserData);
                break;
            case USER_PRESENTATION:
                setToUserPresentation();
                break;
        }

        //Cause store selection pop-up if there is no store defined
        if(DEFAULT_STORE_NAME.equals(prefs.getString(STORE_NAME,DEFAULT_STORE_NAME))){
            Intent intent = new Intent(TopLevelActivity.this, SelectStoreActivity.class);
            startActivityForResult(intent, SET_STORE_CALLBACK);
        }

        //Cause store selection pop-up if there is no store defined and they click on the name
        final TextView saucerNameView = ((TextView) findViewById(R.id.saucerName));
        saucerNameView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ("The Flying Saucer".equals(saucerNameView.getText())){
                    Intent intent = new Intent(TopLevelActivity.this, SelectStoreActivity.class);
                    startActivityForResult(intent, SET_STORE_CALLBACK);
                }
            }
        });

        /////////////////////////////////////////Listeners created inside onCreate Top Level/////////////////////////

        // DRS 20200220 - Added listener to supply card number on long press of user name
        userNameView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Toast.makeText(TopLevelActivity.this,"Your authentication name is " + prefs.getString(TopLevelActivity.AUTHENTICATION_NAME, "") , Toast.LENGTH_LONG).show();
                return true;
            }
        });

        // START VARIOUS THINGS WHEN SETTINGS ITEMS CLICKED
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (toolbar != null) {
            toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    Log.v(TAG, "Toolbar Item Clicked: " + item.getItemId());
                    switch (item.getItemId()) {
                        case R.id.action_settings:
                            Intent settingsIntent = new Intent(TopLevelActivity.this, SettingsActivitySimple.class);
                            startActivityForResult(settingsIntent, SETTINGS_ACTIVITY);
                            break;
                        case R.id.action_about:
                            String versionNumber = "(unavailable)";
                            try {versionNumber = getPackageManager().getPackageInfo(getPackageName(), 0).versionName.toString();} catch (PackageManager.NameNotFoundException e) {}
                            new AlertDialog.Builder(TopLevelActivity.this)
                                    .setTitle("Knurder Software Version")
                                    .setMessage(Html.fromHtml(getResources().getString(R.string.menu_top_level_action_about, versionNumber)))
                                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // do nothing
                                        }
                                    })
                                    .setNegativeButton("Email Log", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            Log.v("sengsational", "Email log requested");
                                            extractLogAndEmail();
                                        }
                                    })
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();
                            break;
                        case R.id.action_rate_app:
                            boolean useNewWay = false;
                            if (useNewWay) {
                                ReviewInfo reviewInfo;
                                manager.requestReviewFlow().addOnCompleteListener(new OnCompleteListener<ReviewInfo>() {
                                    @Override
                                    public void onComplete(@NonNull Task<ReviewInfo> task) {
                                        ReviewInfo reviewInfo;
                                        if(task.isSuccessful()){
                                            reviewInfo = task.getResult();
                                            manager.launchReviewFlow(TopLevelActivity.this, reviewInfo).addOnFailureListener(new OnFailureListener() {
                                                @Override
                                                public void onFailure(Exception e) {
                                                    Toast.makeText(TopLevelActivity.this, "Sorry, Review Process Has Failed", Toast.LENGTH_SHORT).show();
                                                }
                                            }).addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task) {
                                                    Toast.makeText(TopLevelActivity.this, "Review Process Completed, Thank You!", Toast.LENGTH_SHORT).show();
                                                }
                                            });
                                        }

                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(Exception e) {
                                        Toast.makeText(TopLevelActivity.this, "In-App Request Failed", Toast.LENGTH_SHORT).show();
                                    }
                                });
                            } else if (useNewWay) {
                                final ReviewManager manager = ReviewManagerFactory.create(TopLevelActivity.this);
                                Task<ReviewInfo> request = manager.requestReviewFlow();
                                request.addOnCompleteListener(new OnCompleteListener<ReviewInfo>() {
                                    @Override
                                    public void onComplete(@NonNull Task<ReviewInfo> task) {
                                        Log.v("sengsational", "onComplete() running");
                                        if (task.isSuccessful()) {
                                            Log.v("sengsational", "task.isSuccessful()");
                                            // We can get the ReviewInfo object
                                            ReviewInfo reviewInfo = task.getResult();
                                            Task<Void> flow = manager.launchReviewFlow(TopLevelActivity.this, reviewInfo);
                                            flow.addOnCompleteListener(new OnCompleteListener<Void>() {
                                                @Override
                                                public void onComplete(@NonNull Task<Void> task2) {
                                                    Log.v("sengsational", "onComplete() running");
                                                    // The flow has finished. The API does not indicate whether the user
                                                    // reviewed or not, or even whether the review dialog was shown. Thus, no
                                                    // matter the result, we continue our app flow.
                                                }
                                            });
                                        } else {
                                            Log.v("sengsational", "task.isSuccessful() FALSE");
                                            // There was some problem, continue regardless of the result.
                                        }
                                    }
                                });
                            } else {
                                final Uri uri = Uri.parse("market://details?id=" + getApplicationContext().getPackageName());
                                final Intent rateAppIntent = new Intent(Intent.ACTION_VIEW, uri);

                                if (getPackageManager().queryIntentActivities(rateAppIntent, 0).size() > 0) {
                                    startActivity(rateAppIntent);
                                } else {
                                    Toast.makeText(getApplicationContext(), "Please go to the PlayStore app on your phone to do the rating", Toast.LENGTH_LONG).show();
                                }
                            }
                            break;
                        case R.id.change_location:
                            Intent intent = new Intent(TopLevelActivity.this, SelectStoreActivity.class);
                            startActivityForResult(intent, SET_STORE_CALLBACK);
                            break;
                        case R.id.scan_glass_size:
                            long lastListDate = prefs.getLong(TopLevelActivity.LAST_LIST_DATE, 0L);
                            //lastListDate = lastListDate - 99900000L; ///////////////////////////////////////TESTING<<<<<<<<<<<<<<
                            long msSinceRefreshList = new Date().getTime() - lastListDate;
                            Log.v("sengsational","msSinceRefreshList " + msSinceRefreshList);
                            if (msSinceRefreshList > ONE_DAY_IN_MS) {
                                askAboutUpdating("storeList", lastListDate, (msSinceRefreshList / ONE_DAY_IN_MS), " UPDATE IS STRONGLY SUGGESTED FOR MATCHING TAPS");
                            } else {
                                Intent intentOcrBase = new Intent(TopLevelActivity.this, OcrBase.class);
                                startActivityForResult(intentOcrBase, UPDATE_GLASS_CALLBACK);
                            }
                            break;
                        case R.id.load_active:
                            boolean resetPresentation = false;
                            getStoreList(resetPresentation, true);
                            break;
                        case R.id.load_tasted:
                            getTastedList();
                            break;
                        case R.id.logon:
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                                Toast.makeText(getApplicationContext(), "The UFO web site no longer accepts connections from older Android devices.  Sorry.  Nothing I can do about it.\n\nPlease try on a device with Jelly Bean or higher.", Toast.LENGTH_LONG).show();
                            } else {
                                Intent loginIntent = new Intent(TopLevelActivity.this, LoginActivity.class);
                                //startActivity(loginIntent);
                                startActivityForResult(loginIntent, SET_PRESENTATION_MODE);
                            }
                            break;
                        case R.id.action_edit_query:
                            //Intent queryIntent = new Intent(TopLevelActivity.this, QueryCheckboxActivity.class);
                            Intent queryIntent = new Intent(TopLevelActivity.this, AndroidDatabaseManager.class);
                            queryIntent.putExtra("LastQuery", "FixMe"); // TODO: pass in the last query
                            startActivityForResult(queryIntent, SET_STORE_CALLBACK);
                            break;
                        case R.id.logout:
                            // Clear database of tasted since user is no longer associated with the session
                            removeTastedFlagsFromDatabase();
                            // Change to store presentation and remove user credentials from preferences
                            boolean removeUserCredentialsFromPrefereneces = true;
                            setToStorePresentation(removeUserCredentialsFromPrefereneces);
                            break;
                        case R.id.action_check_quiz:
                            new QuizInteractor(TopLevelActivity.this).getQuizPageFromWeb(TopLevelActivity.this);
                            break;
                    }
                    return false;
                }
            });

        }
        Log.v("sengsational", "TLA.onCreate() is complete. 1722");

        // DRS 20160815 = added 'if' - old list reminder
        if (KnurderApplication.oldListCheck) {

            String loginAlertMessagePreference = prefs.getString(LOGIN_ALERT_MESSAGE,"");
            boolean logonAlertPreferenceFound = !"".equals(loginAlertMessagePreference);
            boolean logonAlertPreferenceIsDoneAlready = "20200601".equals(loginAlertMessagePreference);
            logonAlertPreferenceIsDoneAlready = true; // DRS 20220805 - This changeover happened a long time ago and it's no longer a worthwhile alert.  But I might want to use it in the future.
            if (!logonAlertPreferenceFound && !logonAlertPreferenceIsDoneAlready) {
                String presentationMode = prefs.getString(TopLevelActivity.PRESENTATION_MODE,"");
                if(USER_PRESENTATION.equals(presentationMode)) {
                    android.app.AlertDialog.Builder logonDialog = new android.app.AlertDialog.Builder(this);
                    logonDialog.setMessage("IMPORTANT\nUFO LOGIN\nINFORMATION:\n\nIf you haven't done so, please update your login to use your email address or username.\n\nCard number is no longer going to work.\n\nClick settings > Log Out, then log on with your email or username.");
                    logonDialog.setCancelable(true);
                    logonDialog.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putString(LOGIN_ALERT_MESSAGE, "20200601");
                            editor.apply();
                            dialog.dismiss();
                        }
                    });
                    logonDialog.create().show();
                }
            } else{
                String presentationMode = prefs.getString(TopLevelActivity.PRESENTATION_MODE,"");
                if(USER_PRESENTATION.equals(presentationMode)) {
                    long lastTastedDate = prefs.getLong(TopLevelActivity.LAST_TASTED_DATE, 0L);
                    //lastTastedDate = lastTastedDate - 99900000L; ///////////////////////////////////////TESTING<<<<<<<<<<<<<<
                    long msSinceRefreshTasted = new Date().getTime() - lastTastedDate;
                    if (msSinceRefreshTasted > ONE_DAY_IN_MS) {

                        askAboutUpdating("tastedList", lastTastedDate, (msSinceRefreshTasted / ONE_DAY_IN_MS), null);
                    }
                }


                if (STORE_PRESENTATION.equals(presentationMode) || USER_PRESENTATION.equals(presentationMode)) {
                    long lastListDate = prefs.getLong(TopLevelActivity.LAST_LIST_DATE, 0L);
                    Log.v(TAG, "last list date " + new Date(lastListDate));
                    //lastListDate = lastListDate - 99900000L; ///////////////////////////////////////TESTING<<<<<<<<<<<<<<
                    long msSinceRefreshList = new Date().getTime() - lastListDate;
                    Log.v("sengsational","msSinceRefreshList " + msSinceRefreshList);
                    if (msSinceRefreshList > ONE_DAY_IN_MS) {
                        askAboutUpdating("storeList", lastListDate, (msSinceRefreshList / ONE_DAY_IN_MS), null);
                    }
                }

                KnurderApplication.oldListCheck = false;
            }
        }
    }

    private void setLegacyButtonVisibility() {
        boolean hideLegacyButtons = prefs.getBoolean("legacy_button_switch", true);
        if (!hideLegacyButtons) {
            localBeersNotTastedButton.setVisibility(View.VISIBLE);
            localTapsNotTastedButton.setVisibility(View.VISIBLE);
            allTapsNotTastedButton.setVisibility(View.VISIBLE);
            tastedListButton.setVisibility(View.VISIBLE);
            allBeersButton.setVisibility(View.VISIBLE);
            allTapsButton.setVisibility(View.VISIBLE);
            localBeersButton.setVisibility(View.VISIBLE);
            localTapsButton.setVisibility(View.VISIBLE);
        } else {
            localBeersNotTastedButton.setVisibility(View.GONE);
            localTapsNotTastedButton.setVisibility(View.GONE);
            allTapsNotTastedButton.setVisibility(View.GONE);
            tastedListButton.setVisibility(View.GONE);
            allBeersButton.setVisibility(View.GONE);
            allTapsButton.setVisibility(View.GONE);
            localBeersButton.setVisibility(View.GONE);
            localTapsButton.setVisibility(View.GONE);
        }
    }

    private void setUbereatsButtonVisibility() {
        boolean hideUbereatsButton = prefs.getBoolean("hide_ubereats_switch", false);
        boolean noUbereatsLinkFound = "".equals(prefs.getString(UBER_EATS_LINK,""));
        if (!hideUbereatsButton && !noUbereatsLinkFound) {
            uberEatsButton.setVisibility(View.VISIBLE);
        } else {
            uberEatsButton.setVisibility(View.GONE);
        }
    }

            // DRS 20160815 - added 'method' - old list reminder
    private void askAboutUpdating(final String listType, long lastListDate, long daysSinceRefresh, final String extraMessage) {
        Log.v("sengsational", "askAboutUpdating");
        //ContextThemeWrapper wrapper = new ContextThemeWrapper(this, R.style.myDialogTheme);
        //android.app.AlertDialog.Builder oldListDialog = new android.app.AlertDialog.Builder(wrapper);
        //android.app.AlertDialog.Builder oldListDialog = new android.app.AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog);
        //AlertDialog.Builder oldListDialog = new AlertDialog.Builder(this, R.style.Theme_AppCompat_Dialog);
        android.app.AlertDialog.Builder oldListDialog = new android.app.AlertDialog.Builder(this);
        //AlertDialog.Builder oldListDialog = new AlertDialog.Builder(this);
        StringBuilder message = new StringBuilder();
        if (listType.equals("storeList")) {
            message.append("Your beer list ");
        } else {
            message.append("Your tasted list ");
        }

        if (daysSinceRefresh == 1) {
            message.append("is 1 day old.");
        } else if (lastListDate < 1) { // uninitailized..first run with this function
            //message.append("might need refreshing.");
            Log.v(TAG, "This is the first time since install or logon");
            //return; // The list gets refreshed on first use without this dialog
            message.append("needs work. ");
        } else if (daysSinceRefresh > 10) {
            message.append("is really old.");
        } else {
            message.append("is " + daysSinceRefresh + " days old.");
        }
        if (extraMessage != null) {
            message.append(extraMessage);
        }
        oldListDialog.setMessage(message.toString() + " Refresh now?");
        oldListDialog.setCancelable(true);
        oldListDialog.setPositiveButton("Refresh", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                boolean resetPresentation = false;
                if (listType.equals("storeList")) {
                    boolean checkForQuiz = true;
                    boolean updateRequestFromMenuScan = extraMessage != null && extraMessage.contains("STRONGLY");
                    if (updateRequestFromMenuScan) checkForQuiz = false;
                    getStoreList(resetPresentation, checkForQuiz);
                } else if (listType.equals("tastedList")) {
                    getTastedList();
                }
                if (extraMessage != null) {
                    Intent intentOcrBase = new Intent(TopLevelActivity.this, OcrBase.class);
                    startActivityForResult(intentOcrBase, UPDATE_GLASS_CALLBACK);
                }
            }
        });
        oldListDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                if (extraMessage != null) {
                    Intent intentOcrBase = new Intent(TopLevelActivity.this, OcrBase.class);
                    startActivityForResult(intentOcrBase, UPDATE_GLASS_CALLBACK);
                }
            }
        });
        Log.v("sengsational", "about to show.");
        oldListDialog.create().show();
        return;
    }

    // DRS 20161208 - Query by icon feature
    public void onClickShowListFromIcons(View unused) {
        String queryContainerKey = prefs.getString(QUERY_CONTAINER, "B");
        String queryTastedKey = prefs.getString(QUERY_TASTED, "B");
        String queryGeographyKey = prefs.getString(QUERY_GEOGRAPHY, "B");

        // Copied this block
        String[] pullFields = new String[]{"_id", "NAME", "DESCRIPTION", "CITY", "ABV", "STYLE", "CREATED", "HIGHLIGHTED","NEW_ARRIVAL", "ACTIVE", "IS_IMPORT", "TASTED", "BREWER", "GLASS_SIZE", "GLASS_PRICE", "USER_REVIEW", "USER_STARS", "REVIEW_FLAG", "BREW_ID", "UNTAPPD_BEER", "UNTAPPD_BREWERY", "STORE_ID"};
        String selectionFields = null;
        String selectionArgs[] = null;
        boolean respectHideMixAndFlightFlag = true;
        String orderBy = null;
        boolean showDateInList = false;
        // End copied this block

        StringBuilder selectionFieldsBuilder = new StringBuilder();
        ArrayList<String> selectionArgsArray = new ArrayList<>();

        if ("L".equals(queryTastedKey)) { // Show only tasted, including non-active, of course
            selectionFieldsBuilder.append("TASTED=? ");
            selectionArgsArray.add("T");
            orderBy = "CREATED_DATE DESC";
            showDateInList = true;
            respectHideMixAndFlightFlag = false;

            if ("L".equals(queryContainerKey)){ // Taps only
                selectionFieldsBuilder.append("AND CONTAINER=? ");
                selectionArgsArray.add("draught");
            } else if ("R".equals(queryContainerKey)){ // Bottles only
                selectionFieldsBuilder.append("AND CONTAINER<>? ");
                selectionArgsArray.add("draught");
            }

            if ("L".equals(queryGeographyKey)) { // Local only
                selectionFieldsBuilder.append("AND IS_LOCAL=? ");
                selectionArgsArray.add("T");
            } else if ("R".equals(queryGeographyKey)) { // Import only
                selectionFieldsBuilder.append("AND IS_IMPORT=? ");
                selectionArgsArray.add("T");
            }
        } else {
            selectionFieldsBuilder.append("ACTIVE=? ");
            selectionArgsArray.add("T");

            if ("R".equals(queryTastedKey)) {
                selectionFieldsBuilder.append("AND TASTED<>? ");
                selectionArgsArray.add("T");
            }

            if ("L".equals(queryContainerKey)){ // Taps only
                selectionFieldsBuilder.append("AND CONTAINER=? ");
                selectionArgsArray.add("draught");
            } else if ("R".equals(queryContainerKey)){ // Bottles only
                selectionFieldsBuilder.append("AND CONTAINER<>? ");
                selectionArgsArray.add("draught");
            }

            if ("L".equals(queryGeographyKey)) { // Local only
                selectionFieldsBuilder.append("AND IS_LOCAL=? ");
                selectionArgsArray.add("T");
            } else if ("R".equals(queryGeographyKey)) { // Import only
                selectionFieldsBuilder.append("AND IS_IMPORT=? ");
                selectionArgsArray.add("T");
            }

        }

        selectionFields = selectionFieldsBuilder.toString();
        selectionArgs =  selectionArgsArray.toArray(new String[0]);

        // Copied this block
        Intent beerList = null;
        Log.v("sengsational", "TRYING RECYCLER VIEW"); //<<<<<<<<<<<<<<<<<<<<<<<<<<<SWAP-OUT TECHNIQUE
        beerList = new Intent(TopLevelActivity.this, RecyclerSqlbListActivity.class);
        beerList.putExtra("pullFields", pullFields);
        //beerList.putExtra("selectedItems", selectedItems);
        beerList.putExtra("selectionFields", selectionFields);
        beerList.putExtra("selectionArgs", selectionArgs);
        beerList.putExtra("respectHideMixAndFlightFlag", respectHideMixAndFlightFlag);
        beerList.putExtra("orderBy", orderBy);
        beerList.putExtra("showDateInList", showDateInList);
        beerList.putExtra("refreshRequired",selectionFields.contains("HIGHLIGHTED")?true:false); // Tell the list activity that we must refresh the list when backing out
        beerList.putExtra("queryButtonText", listBeersButton.getText());
        Log.v("sengsational", "going to RecyclerSqlbListActivity");
        startActivity(beerList);
        // END Copied this block
    }

    // Alter Icon highlighting and set preferences when icons clicked (this method is associated with buttons inside the layout)
    public void onClickUpdateIcons(View view) {
        if (NO_STORE_PRESENTATION.equals(prefs.getString(PRESENTATION_MODE, NO_STORE_PRESENTATION))) return; // Don't show icon changes if they're in this 'nothing' mode

        SharedPreferences.Editor editor = prefs.edit();
        String queryContainerKey = prefs.getString(QUERY_CONTAINER, "B");
        String queryTastedKey = prefs.getString(QUERY_TASTED, "B");
        String queryGeographyKey = prefs.getString(QUERY_GEOGRAPHY, "B");
        boolean isSelected = false;
        String nextKey = null;

        switch (view.getId()) {
            case R.id.tap_icon:
                nextKey = getNextKeyLeft(queryContainerKey);
                editor.putString(QUERY_CONTAINER, nextKey).apply();
                setQueryIconColors(nextKey, ContextCompat.getDrawable(this, R.drawable.tap) ,ContextCompat.getDrawable(this, R.drawable.bottle));
                tapIcon.invalidate();
                bottleIcon.invalidate();
                break;
            case R.id.bottle_icon:
                nextKey = getNextKeyRight(queryContainerKey);
                editor.putString(QUERY_CONTAINER, nextKey).apply();
                setQueryIconColors(nextKey, ContextCompat.getDrawable(this, R.drawable.tap) ,ContextCompat.getDrawable(this, R.drawable.bottle));
                tapIcon.invalidate();
                bottleIcon.invalidate();
                break;
            case R.id.tasted_icon:
                nextKey = getNextKeyLeft(queryTastedKey);
                editor.putString(QUERY_TASTED, nextKey).apply();
                setQueryIconColors(nextKey, ContextCompat.getDrawable(this, R.drawable.tasted) ,ContextCompat.getDrawable(this, R.drawable.untasted));
                tastedIcon.invalidate();
                untastedIcon.invalidate();
                break;
            case R.id.untasted_icon:
                nextKey = getNextKeyRight(queryTastedKey);
                editor.putString(QUERY_TASTED, nextKey).apply();
                setQueryIconColors(nextKey, ContextCompat.getDrawable(this, R.drawable.tasted) ,ContextCompat.getDrawable(this, R.drawable.untasted));
                tastedIcon.invalidate();
                untastedIcon.invalidate();
                break;
            case R.id.local_icon:
                nextKey = getNextKeyLeft(queryGeographyKey);
                editor.putString(QUERY_GEOGRAPHY, nextKey).apply();
                setQueryIconColors(nextKey, ContextCompat.getDrawable(this, R.drawable.local) ,ContextCompat.getDrawable(this, R.drawable.world));
                localIcon.invalidate();
                worldIcon.invalidate();
                break;
            case R.id.world_icon:
                nextKey = getNextKeyRight(queryGeographyKey);
                editor.putString(QUERY_GEOGRAPHY, nextKey).apply();
                setQueryIconColors(nextKey, ContextCompat.getDrawable(this, R.drawable.local) ,ContextCompat.getDrawable(this, R.drawable.world));
                localIcon.invalidate();
                worldIcon.invalidate();
                break;
            default:
                Log.v(TAG, "Invalidate all.");
                tapIcon.invalidate();
                bottleIcon.invalidate();
                tastedIcon.invalidate();
                untastedIcon.invalidate();
                localIcon.invalidate();
                worldIcon.invalidate();
        }
        String iconStatusString = prefs.getString(QUERY_CONTAINER, "B") + prefs.getString(QUERY_TASTED, "B") + prefs.getString(QUERY_GEOGRAPHY, "B");
        Log.v(TAG, "iconStatusString: " + iconStatusString);
        listBeersButton.setEnabled(false);
        if (iconStatusString.substring(0,1).equals("X")) {
            listBeersButton.setText(R.string.container_prompt);
        } else if (iconStatusString.substring(1,2).equals("X")) {
            listBeersButton.setText(R.string.tasted_prompt);
        } else if (iconStatusString.substring(2,3).equals("X")) {
            listBeersButton.setText(R.string.geography_prompt);
        } else {
            int aResource = getResources().getIdentifier(iconStatusString, "string", "com.sengsational.knurder");
            if (aResource != 0) listBeersButton.setText(aResource);
            listBeersButton.setEnabled(true);
        }
    }

    public void onClickLaunchUberEatsBrowser(View view) {
        Log.v("sengsational", "<<<<<<<<<<<<clicked to launch browser");
        Intent browserIntent = new Intent(Intent.ACTION_VIEW);
        browserIntent.setData(Uri.parse(prefs.getString(UBER_EATS_LINK,"https://www.beerknurd.com")));
        startActivity(browserIntent);
    }

    private String getNextKeyLeft(String pairStateLetter) {
        if ("B".equals(pairStateLetter)) return "R";
        else if ("L".equals(pairStateLetter)) return "X";
        else if ("R".equals(pairStateLetter)) return "B";
        return "L";
    }

    private String getNextKeyRight(String pairStateLetter) {
        if ("B".equals(pairStateLetter)) return "L";
        else if ("R".equals(pairStateLetter)) return "X";
        else if ("L".equals(pairStateLetter)) return "B";
        return "R";
    }

    private void setQueryIconColors() {
        if (prefs.getBoolean("dark_mode_switch", false)) {
            colorIconActive = ContextCompat.getColor(context, R.color.colorIconActiveDark);
            colorIconNonActive = ContextCompat.getColor(context, R.color.colorIconNonActiveDark);
        } else {
            colorIconActive = ContextCompat.getColor(context, R.color.colorIconActiveLight);
            colorIconNonActive = ContextCompat.getColor(context, R.color.colorIconNonActiveLight);
        }
        ContextCompat.getDrawable(this, R.drawable.spacer).setColorFilter(colorIconNonActive, PorterDuff.Mode.SRC_ATOP);
        String queryContainerKey = prefs.getString(QUERY_CONTAINER, "B");
        String queryTastedKey = prefs.getString(QUERY_TASTED, "B");
        String queryGeographyKey = prefs.getString(QUERY_GEOGRAPHY, "B");
        setQueryIconColors(queryContainerKey, ContextCompat.getDrawable(this, R.drawable.tap) ,ContextCompat.getDrawable(this, R.drawable.bottle));
        setQueryIconColors(queryTastedKey, ContextCompat.getDrawable(this, R.drawable.tasted) ,ContextCompat.getDrawable(this, R.drawable.untasted));
        setQueryIconColors(queryGeographyKey, ContextCompat.getDrawable(this, R.drawable.local) ,ContextCompat.getDrawable(this, R.drawable.world));

    }

    private void setQueryIconColors(String key, Drawable leftIcon, Drawable rightIcon) {
        if ("B".equals(key)) { // Both selected
            leftIcon.setColorFilter(colorIconActive, PorterDuff.Mode.SRC_ATOP);
            rightIcon.setColorFilter(colorIconActive, PorterDuff.Mode.SRC_ATOP);
        } else if ("R".equals(key)) { // Right selected
            leftIcon.setColorFilter(colorIconNonActive, PorterDuff.Mode.SRC_ATOP);
            rightIcon.setColorFilter(colorIconActive, PorterDuff.Mode.SRC_ATOP);
        } else if ("L".equals(key)){  // Left selected
            leftIcon.setColorFilter(colorIconActive, PorterDuff.Mode.SRC_ATOP);
            rightIcon.setColorFilter(colorIconNonActive, PorterDuff.Mode.SRC_ATOP);
        } else { // Only for no-store presentation
            leftIcon.setColorFilter(colorIconNonActive, PorterDuff.Mode.SRC_ATOP);
            rightIcon.setColorFilter(colorIconNonActive, PorterDuff.Mode.SRC_ATOP);
        }
    }


    // START BEER LIST VIEW WHEN BUTTONS CLICKED (this method is associated with buttons inside the layout)
    public void onClickShowList(View v) {
        String[] pullFields = new String[]{"_id", "NAME", "DESCRIPTION", "CITY", "ABV", "STYLE", "CREATED", "HIGHLIGHTED","NEW_ARRIVAL", "ACTIVE", "IS_IMPORT", "TASTED", "BREWER", "GLASS_SIZE", "GLASS_PRICE", "USER_REVIEW", "USER_STARS", "REVIEW_FLAG", "BREW_ID", "UNTAPPD_BEER", "UNTAPPD_BREWERY", "STORE_ID"};

        //final String[] selectedItems = {"LocalNotTasted", "Local", "TapsNotTasted", "Taps", "Tasted", "Database"};
        String selectionFields = null;
        String selectionArgs[] = null;
        boolean respectHideMixAndFlightFlag = true;
        String orderBy = null;
        boolean showDateInList = false;
        String iconSetting = "BBB";

        switch (v.getId()){
            case R.id.flagged_beers_button:
                selectionFields =               "HIGHLIGHTED=?"; // DRS 20161202 - Originally had ACTIVE=T
                selectionArgs = new String[] {  "T"};
                respectHideMixAndFlightFlag = false;
                break;
            case R.id.new_arrival_button:
                selectionFields =               "NEW_ARRIVAL=?";
                selectionArgs = new String[] {  "T"};
                respectHideMixAndFlightFlag = false;
                break;
            case R.id.database_access_button:
                pullFields = null;
                Intent dbmanager = new Intent(TopLevelActivity.this,AndroidDatabaseManager.class);
                Log.v("sengsational", "going to AndroidDatabaseManager");
                startActivity(dbmanager);
                break;
            case R.id.all_beers_button:
                iconSetting = "BBB";
                pullFields = null;
                break;
            case R.id.all_taps_button:
                iconSetting = "LBB";
                pullFields = null;
                break;
            case R.id.local_beers_button:
                iconSetting = "BBL";
                pullFields = null;
                break;
            case R.id.local_beers_nt_button:
                iconSetting = "BRL";
                pullFields = null;
                break;
            case R.id.local_taps_button:
                iconSetting = "LBL";
                pullFields = null;
                break;
            case R.id.local_taps_nt_button:
                iconSetting = "LRL";
                pullFields = null;
                break;
            case R.id.tasted_list_button:
                iconSetting = "BLB";
                pullFields = null;
                break;
            case R.id.all_taps_nt_button:
                iconSetting = "LRB";
                pullFields = null;
                break;
        } // end switch
        if (pullFields != null) {
            Intent beerList = null;
            Log.v("sengsational", "TRYING RECYCLER VIEW"); //<<<<<<<<<<<<<<<<<<<<<<<<<<<SWAP-OUT TECHNIQUE
            beerList = new Intent(TopLevelActivity.this, RecyclerSqlbListActivity.class);
            beerList.putExtra("pullFields", pullFields);
            //beerList.putExtra("selectedItems", selectedItems);
            beerList.putExtra("selectionFields", selectionFields);
            beerList.putExtra("selectionArgs", selectionArgs);
            beerList.putExtra("respectHideMixAndFlightFlag", respectHideMixAndFlightFlag);
            beerList.putExtra("orderBy", orderBy);
            beerList.putExtra("showDateInList", showDateInList);
            beerList.putExtra("refreshRequired",selectionFields.contains("HIGHLIGHTED")?true:false); // Tell the list activity that we must refresh the list when backing out
            Log.v("sengsational", "going to BeerListActivity");
            startActivity(beerList);
        } else {
            // This needs to go to set the icons and then run the query the new way
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(QUERY_CONTAINER, iconSetting.substring(0,1));
            editor.putString(QUERY_TASTED, iconSetting.substring(1,2));
            editor.putString(QUERY_GEOGRAPHY, iconSetting.substring(2,3));
            editor.apply();
            onClickUpdateIcons(v);
            alignIconAndButtonTextWithPrefs(prefs);
            // Delay the action momentarily
            //onClickShowListFromIcons(v);
            v.removeCallbacks(delayShowListFromIcons);
            v.postDelayed(delayShowListFromIcons, 400);
        }
    }

    Runnable delayShowListFromIcons = new Runnable() {
        @Override
        public void run() {
            onClickShowListFromIcons(null);
        }
    };

    // START NEXT ACTIVITY AFTER STORE SELECTED
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.v("sengsational", "onActivityResult running with " + resultCode);
        if (requestCode == SET_STORE_CALLBACK) {
            if (resultCode == RESULT_OK){
                // need to load that saucer's beers and remove tasted if that needs to be done <<<< That was the FORMER IDEA, now we will not remove tasted
                boolean resetPresentation = true;
                // DRS 20161006 - Add 1 + if block - Allow Change Location while logged-in
                if (USER_PRESENTATION.equals(prefs.getString(PRESENTATION_MODE, ""))){
                    resetPresentation = false;
                }
                // DRS 20181028 - Don't check for quiz on location change
                boolean checkQuiz = true;
                try {checkQuiz = !"N".equals(data.getStringExtra("check_quiz"));} catch (Throwable t) {}
                Log.v(TAG, "checkQuiz was " + checkQuiz);
                this.getStoreList(resetPresentation, checkQuiz);
            } else {
                Log.v("sengsational","TLA.onActivityResult code: " + resultCode);
            }
        } else if (requestCode == SET_PRESENTATION_MODE) {
            String presentationMode = KnurderApplication.getPresentationMode();
            if ("store-false".equals(presentationMode)) {
                setToStorePresentation(false);
            } else if ("user".equals(presentationMode)) {
                setToUserPresentation();
            }
        } else if (requestCode  == SETTINGS_ACTIVITY) {
            setLegacyButtonVisibility();
            setUbereatsButtonVisibility();
        } else if (requestCode  == UPDATE_GLASS_CALLBACK) {
            // Nothing to do here?
        }

    }//onActivityResult

    // CONTROL VISIBILITY DEPENDING ON MODE

    public void setToUserPresentation() {
        Log.v("sengsational", "TLA.setToUserPresentation");
        userNameView.setText(prefs.getString(USER_NAME, ""));

        // DRS 20161008 - Added 'if' - For when existing user upgrades to allow logged in store to differ from display store
        if("undefined".equals(prefs.getString(STORE_NUMBER_LOGON,"undefined"))){
            // this installation has never logged-on with updated version.  Make the STORE_NUMBER and STORE_NUMBER_LOGON match
            Log.v("sengsational","STORE_NUMBER_LOGON not defined.  Now setting it to the STORE_NUMBER");
            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
            editor.putString(STORE_NUMBER_LOGON, prefs.getString(STORE_NUMBER,DEFAULT_STORE_NAME));
            editor.apply();
        }

        // DRS 20161006 - Added 'if' - Allow Change Location while logged-in
        // If the loginStoreName is different than the STORE_NAME, display the store name so the user knows they are displaying a different list
        if(!prefs.getString(STORE_NUMBER_LOGON,DEFAULT_STORE_NAME).equals(prefs.getString(STORE_NUMBER,DEFAULT_STORE_NAME))) {
            locationTextView.setVisibility(View.VISIBLE);
        } else {
            locationTextView.setVisibility(View.INVISIBLE);
        }

        userNameView.setVisibility(View.VISIBLE);
        saucerNameView.setVisibility(View.INVISIBLE);

        tastedCountView.setText("Tasted " + prefs.getString(TASTED_COUNT, "?") + " so far.");
        tastedCountView.setVisibility(View.VISIBLE);

        localBeersNotTastedButton.setEnabled(true);
        localTapsNotTastedButton.setEnabled(true);
        newArrivalsButton.setEnabled(true);
        allTapsNotTastedButton.setEnabled(true);
        tastedListButton.setEnabled(true);
        flaggedBeersButton.setEnabled(true);

        tastedIcon.setVisibility(View.VISIBLE);
        untastedIcon.setVisibility(View.VISIBLE);
        spacerIcon.setVisibility(View.VISIBLE);

        // DRS 20161006 - Comment 1, Add 1 - Allow Change Location while logged-in
        //disableMenuItems(new String[] {"Log On", "Change Location"});
        disableMenuItems(new String[] {"Log On"});

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PRESENTATION_MODE, USER_PRESENTATION);
        editor.apply();

        alignIconAndButtonTextWithPrefs(prefs);


    } // Visibility

    private void alignIconAndButtonTextWithPrefs(SharedPreferences prefs) {
        setQueryIconColors(prefs.getString(QUERY_CONTAINER, "B"), ContextCompat.getDrawable(this, R.drawable.tap) ,ContextCompat.getDrawable(this, R.drawable.bottle));
        setQueryIconColors(prefs.getString(QUERY_TASTED, "B"), ContextCompat.getDrawable(this, R.drawable.tasted) ,ContextCompat.getDrawable(this, R.drawable.untasted));
        setQueryIconColors(prefs.getString(QUERY_GEOGRAPHY, "B"), ContextCompat.getDrawable(this, R.drawable.local) ,ContextCompat.getDrawable(this, R.drawable.world));
        String iconStatusString = prefs.getString(QUERY_CONTAINER, "B") + prefs.getString(QUERY_TASTED, "B") + prefs.getString(QUERY_GEOGRAPHY, "B");
        int aResource = getResources().getIdentifier(iconStatusString, "string", "com.sengsational.knurder");
        if (aResource != 0) listBeersButton.setText(aResource);
    }

    private static void disableMenuItems(String[] menuTitles) {
        Iterator<String> menuIter = menuMap.keySet().iterator();
        while (menuIter.hasNext()){menuMap.get(menuIter.next()).setEnabled(true);}
        for (String title : menuTitles) {
            MenuItem item = menuMap.get(title);
            Log.v("sengsational", "disable " + title);
            if (item != null) item.setEnabled(false);
        }
    }

    public void setToStorePresentation(boolean clearUserData) {
        Log.v("sengsational", "TLA.setToStorePresentation()");

        // DRS 20161006 - Add 1 - Allow Change Location while logged-in
        locationTextView.setVisibility(View.INVISIBLE);

        userNameView.setText("");
        saucerNameView.setText(prefs.getString(STORE_NAME, DEFAULT_STORE_NAME));
        saucerNameView.setVisibility(View.VISIBLE);
        tastedCountView.setVisibility(View.INVISIBLE);

        allBeersButton.setEnabled(true); //all_beers_button));
        allTapsButton.setEnabled(true); //all_taps_button));
        localBeersButton.setEnabled(true); //local_beers_button));
        flaggedBeersButton.setEnabled(true); //
        localTapsButton.setEnabled(true); //local_taps_button));
        newArrivalsButton.setEnabled(true);
        listBeersButton.setEnabled(true);

        tastedIcon.setVisibility(View.GONE);
        untastedIcon.setVisibility(View.GONE);
        spacerIcon.setVisibility(View.GONE);

        localBeersNotTastedButton.setEnabled(false);
        localTapsNotTastedButton.setEnabled(false);
        allTapsNotTastedButton.setEnabled(false);
        tastedListButton.setEnabled(false);

        disableMenuItems(new String[]{"Refresh Tasted", "Log Out"});

        if (clearUserData) clearUserData(null);

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PRESENTATION_MODE, STORE_PRESENTATION);
        editor.putString(QUERY_TASTED, "B").apply(); // Since tasted will be hidden, make sure the prefs are for "B" both so queries work
        editor.apply();

        alignIconAndButtonTextWithPrefs(prefs);


    } // Visibility

    private void clearUserData(String currentStoreNumber) {
        if (currentStoreNumber != null) {
            removeOldStoreData(currentStoreNumber);
        } else {
            removeTastedFlagsFromDatabase();
        }
        Log.v("sengsational", "Clearing preferences.");
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(USER_NAME);
        editor.remove(TASTED_COUNT);
        //editor.remove(CARD_NUMBER); // DRS 20200220 - Leave the card number so next login is easier
        editor.remove(PASSWORD);
        //editor.remove(LAST_LIST_DATE); //This should not be removed...it's not user specific.
        editor.remove(LAST_TASTED_DATE);
        editor.remove(QUERY_CONTAINER);
        editor.remove(QUERY_GEOGRAPHY);
        editor.remove(QUERY_TASTED);
        editor.putString(PRESENTATION_MODE, STORE_PRESENTATION);
        editor.apply();
    }

    private void setToNoStorePresentation() {
        Log.v("sengsational", "TLA.setToNoStorePresentation");

        // DRS 20161006 - Add 1 - Allow Change Location while logged-in
        locationTextView.setVisibility(View.INVISIBLE);

        userNameView.setVisibility(View.INVISIBLE);
        saucerNameView.setVisibility(View.VISIBLE);
        localBeersNotTastedButton.setEnabled(false);
        localTapsNotTastedButton.setEnabled(false);
        allTapsNotTastedButton.setEnabled(false);
        tastedListButton.setEnabled(false);
        tastedCountView.setVisibility(View.INVISIBLE);
        allBeersButton.setEnabled(false); //all_beers_button));
        allTapsButton.setEnabled(false); //all_taps_button));
        localBeersButton.setEnabled(false); //local_beers_button));
        flaggedBeersButton.setEnabled(false); //
        localTapsButton.setEnabled(false); //local_taps_button));
        newArrivalsButton.setEnabled(false);
        listBeersButton.setEnabled(false);

        tastedIcon.setVisibility(View.GONE);
        untastedIcon.setVisibility(View.GONE);
        spacerIcon.setVisibility(View.GONE);

        // Gray them all
        setQueryIconColors("X", ContextCompat.getDrawable(this, R.drawable.tap) ,ContextCompat.getDrawable(this, R.drawable.bottle));
        setQueryIconColors("X", ContextCompat.getDrawable(this, R.drawable.tasted) ,ContextCompat.getDrawable(this, R.drawable.untasted));
        setQueryIconColors("X", ContextCompat.getDrawable(this, R.drawable.local) ,ContextCompat.getDrawable(this, R.drawable.world));

        disableMenuItems(new String[]{"Refresh Active", "Refresh Tasted", "Log Out", "Custom Query"});

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PRESENTATION_MODE, NO_STORE_PRESENTATION);
        editor.putString(QUERY_TASTED, "B");
        editor.putString(QUERY_GEOGRAPHY, "B");
        editor.putString(QUERY_TASTED, "B");
        editor.apply();

        alignIconAndButtonTextWithPrefs(prefs);
    }  // Visibility

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    private void dumpPrefs() {
        Map<String,?> keys = prefs.getAll();

        for(Map.Entry<String,?> entry : keys.entrySet()){
            Log.d("map values", entry.getKey() + ": " + entry.getValue());
        }
    } // Private Helper Method

    private void removeOldStoreData(String currentStoreNumber) {
        try {
            UfoDatabaseAdapter ufoHelper = new UfoDatabaseAdapter(context);
            SQLiteDatabase db = ufoHelper.openDb(context);               //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            db.execSQL("delete from UFO where store_id = '" + currentStoreNumber + "'");
            //db.execSQL("delete from STYLES");
            db.close();                                                        //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        } catch (Throwable t) {
            Log.e("sengsational", "Database Failure: " + t.getMessage());
        }
    } // Private Helper Method



    private void removeTastedFlagsFromDatabase() {
        try {
            UfoDatabaseAdapter ufoHelper = new UfoDatabaseAdapter(context);
            SQLiteDatabase db = ufoHelper.openDb(this);               //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            db.execSQL("update UFO set TASTED='F'");
            db.execSQL("update UFO set CREATED = null"); // DRS 20181204
            db.close();                                                        //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        } catch (Throwable t) {
            Log.e("sengsational", "Database Failure: " + t.getMessage());
        }
    } // Private Helper Method

    private void removeHighlightedFlagsFromDatabase(){
        try {
            UfoDatabaseAdapter ufoHelper = new UfoDatabaseAdapter(context);
            SQLiteDatabase db = ufoHelper.openDb(this);               //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
            db.execSQL("update UFO set HIGHLIGHTED='F'");
            db.close();                                                        //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<CLOSING DATABASE
        } catch (Throwable t) {
            Log.e("sengsational", "Database Failure: " + t.getMessage());
        }
    } // Private Helper Method

    private void extractLogAndEmail() {

        int pid = android.os.Process.myPid();
        try {
            String command = String.format("logcat -d -v threadtime *:V");
            Process process = Runtime.getRuntime().exec(command);

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder result = new StringBuilder();
            String currentLine = null;

            int lineCount = 0;
            while ((currentLine = reader.readLine()) != null) {
                if (currentLine != null && currentLine.contains(String.valueOf(pid))) {
                    result.append(currentLine);
                    result.append("\n");
                    lineCount++;
                }
            }
            Log.v("sengsational","log has " + lineCount + " lines.");

            File tempDir = getContext().getExternalCacheDir();
            String timeStamp = new SimpleDateFormat("yyyy-MM-dd-mmss").format(new Date());
            File tempFile = File.createTempFile("debug-" + timeStamp, ".txt", tempDir);
            FileWriter out = new FileWriter(tempFile);
            out.write(result.toString());
            out.close();

            int BUFFER_SIZE = 1024;
            String tempZipName = "debug-" + timeStamp + "_";
            File tempZip = File.createTempFile(tempZipName, ".txt", tempDir);
            BufferedInputStream origin = null;
            ZipOutputStream outz = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZip)));
            byte data[] = new byte[BUFFER_SIZE];

            FileInputStream fi = new FileInputStream(tempFile);
            origin = new BufferedInputStream(fi, BUFFER_SIZE);
            try {
                ZipEntry entry = new ZipEntry(tempZipName);
                outz.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                    outz.write(data, 0, count);
                }
            }
            finally {
                outz.close();
                origin.close();
            }

            String versionNumber = "(unavailable)";
            try {versionNumber = getPackageManager().getPackageInfo(getPackageName(), 0).versionName.toString();} catch (PackageManager.NameNotFoundException e) {}

            //Uri contentUri = Uri.fromFile(tempZip); // DRS 20181026 - Quit working when I targeted API 26
            Uri contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".provider", tempZip);

            Intent mailIntent = new Intent(Intent.ACTION_SEND);
            mailIntent.setType("message/rfc822");
            mailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"Knurder.frog4food@recursor.net"});

            mailIntent.putExtra(Intent.EXTRA_SUBJECT, "Debug " + timeStamp);
            mailIntent.putExtra(Intent.EXTRA_TEXT, "The attachment contains logging from the Knurder application(" + versionNumber + ").  CC yourself on this email if you want me to reply via email.  You can add a description about what you were doing at the time of the problem here:");
            mailIntent.putExtra(android.content.Intent.EXTRA_STREAM, contentUri);

            try {
                startActivity(Intent.createChooser(mailIntent, "Send email.."));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, "NO EMAIL SERVICE AVAILABLE", Toast.LENGTH_SHORT).show();
            }

            try {
                //tempFile.delete();
                //tempZip.delete();
            } catch (Exception e) {
                Log.v("sengsational", "Could not delete temp file(s): " + e.getMessage());
            }

            //Runtime.getRuntime().exec("logcat -d -v time -f "+file.getAbsolutePath());
        } catch (IOException e) {
            Log.v("sengsational","Failed to manage log file send. " + e.getMessage());
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }


        //clear the log
        try {
            Runtime.getRuntime().exec("logcat -c");
        } catch (IOException e) {
            Toast.makeText(getApplicationContext(), e.toString(), Toast.LENGTH_SHORT).show();
        }



    } // Private Helper Method



    public Context getContext() {
        return context;
    } // Public Helper Method

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_top_level_activty, menu);
        for (int i = 0; i < menu.size(); i++) {
            Log.v("sengsational", "menu item " + menu.getItem(i).getTitle().toString());
            menuMap.put(menu.getItem(i).getTitle().toString(), menu.getItem(i));
        }
        String presentationMode = prefs.getString(PRESENTATION_MODE, STORE_PRESENTATION);
        switch (presentationMode) {
            case NO_STORE_PRESENTATION:
                disableMenuItems(new String[]{"Refresh Active", "Refresh Tasted", "Log Out", "Custom Query"});
                break;
            case STORE_PRESENTATION:
                disableMenuItems(new String[]{"Refresh Tasted", "Log Out"});
                break;
            case USER_PRESENTATION:
                // DRS 20161006 - Comment 1, Add 1 - Allow Change Location while logged-in
                //disableMenuItems(new String[] {"Log On", "Change Location"});
                disableMenuItems(new String[] {"Log On"});
                break;
        }
        return true;
    } // Options Menu Support

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //Log.v("TAG", "onPrepareOptionsMenu running!!");
        MenuItem itemGlass = menu.findItem(R.id.scan_glass_size);
        if (itemGlass != null) {
            if (!prefs.getBoolean("allow_external_picture_storage_switch", true)) {
                itemGlass.setVisible(false);
            } else {
                itemGlass.setVisible(true);
            }
        } else {
            Log.e(TAG, "The Menu item glass size was null");
        }

        return true;
    }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    } // Options Menu Support

    //////////////////////////////////////////////Methods I call on user action /////////////////////////////////////
    @Override public void getStoreList(boolean resetPresentation, boolean checkForQuiz) {
        storeListPresenter.getStoreList(prefs.getString(STORE_NUMBER, "13888"), resetPresentation);

        // If this person is logged in (in user presentation mode), hit the quiz interactor to see if there's a quiz
        if (prefs.getString(PRESENTATION_MODE, "").equals(USER_PRESENTATION) && checkForQuiz) {
            new QuizInteractor(TopLevelActivity.this).getQuizPageFromWeb(this);
        }
    } // Data activity

    @Override public void getTastedList() {
        String savePasswordFromSwitch = prefs.getBoolean("password_switch", true)?"T":"F";
        tastedListPresenter.getTastedList(prefs.getString(AUTHENTICATION_NAME, ""), prefs.getString(PASSWORD, ""), prefs.getString(MOU,"0"), savePasswordFromSwitch, prefs.getString(STORE_NUMBER_LOGON, "13888"));
    } // Data activity

    //////////////////////////////////////////Methods called by the listener////////////////
    @Override public void saveValidCredentials(String authenticationName, String password, String savePassword, String mou, String storeNumber, String userName, String tastedCount) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(AUTHENTICATION_NAME, authenticationName);
        if ("T".equals(prefs.getString(SAVE_PASSWORD,"T"))) editor.putString(PASSWORD, password);
        editor.putString(STORE_NUMBER_LOGON, storeNumber);
        editor.putString(TASTED_COUNT, tastedCount);
        editor.putString(USER_NAME, userName);
        editor.putString(MOU, mou);
        editor.apply();
        //setToUserPresentation();  << Don't do this here...wait for a good tasted list

    } // Called on successful login

    @Override public void saveValidStore(String storeNumber) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();

        // THIS CODE IS BOGUS AND NEEDS TO BE REMOVED AFTER SOME MORE DIGGING - I think that clearUserData is NEVER done.
        boolean currentStoreNumberIsAlwaysTheSameAsStoreNumber = true;
        if (currentStoreNumberIsAlwaysTheSameAsStoreNumber) {
            String currentStoreNumber = prefs.getString(STORE_NUMBER,storeNumber);

            // if the store number is NOT the same, wipe user data
            // this (should be) the only place where old store / new store are compared and action taken
            Log.v("sengsational", "Store number change from " + currentStoreNumber + " to " + storeNumber);
            if (!currentStoreNumber.equals(storeNumber) && !"".equals(currentStoreNumber)){
                Log.v("sengsational", "Store number change from " + currentStoreNumber + " to " + storeNumber + " User data getting wiped.");
                clearUserData(currentStoreNumber);
            }
        }

        editor.putString(STORE_NUMBER, storeNumber);//kK3yy7hs
        Log.v("sengsational", "TLA.saveValidStore " + StoreNameHelper.getInstance().getStoreNameFromNumber(storeNumber, null));
        editor.putString(STORE_NAME, StoreNameHelper.getInstance().getStoreNameFromNumber(storeNumber, null));
        editor.apply();

        // DRS 20161006 - Add 1 + 'if/else' - Allow Change Location while logged-in
        locationTextView.setText(StoreNameHelper.getInstance().getStoreNameFromNumber(storeNumber, null));
        // If the loginStoreName is different than the STORE_NAME, display the store name so the user knows they are displaying a different list
        // DRS 20161006 - Add 'if' - Allow Change Location while logged-in
        // If the loginStoreName is different than the STORE_NAME, display the store name so the user knows they are displaying a different list
        if(!prefs.getString(STORE_NUMBER_LOGON,DEFAULT_STORE_NAME).equals(prefs.getString(STORE_NUMBER,DEFAULT_STORE_NAME))) {
            locationTextView.setVisibility(View.VISIBLE);
        } else {
            locationTextView.setVisibility(View.INVISIBLE);
        }
        //setToStorePresentation(false);
        setUbereatsButtonVisibility(); // The store page may or may not have an uber eats link.  If it's populated and the user has button visible, show it now.
    } // Called on successful store list

    @Override public void onDestroy() {
        //presenter.onDestroy();
        super.onDestroy();
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public void showProgress(final boolean show) {
        // If more than one update is in process, mProgressStacker will increment higher.
        // As processes finish, and call showProgress(false), it will decrement.
        // If not all processes have called showProgress(false), it will return (have no effect on progress screen).
        // Once all processes call showProgress(false), it will continue and remove the progress screen.
        if (show) mProgressStacker++;
        else mProgressStacker--;
        Log.v("sengsational", "mProgressStacker: " + mProgressStacker + " " + show);
        if (mProgressStacker < 0) {
            Log.e(TAG, "Progress Stacker less than zero!");
            mProgressStacker = 0;
        }
        if (!show && mProgressStacker > 0) return;

        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            topLevelView.setVisibility(show ? View.GONE : View.VISIBLE);
            topLevelView.animate().setDuration(shortAnimTime).alpha(show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    topLevelView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            Log.v("sengsational", "setting visibility of progressView: show=" + show);
            progressView.setVisibility(show ? View.VISIBLE : View.GONE);
            progressView.animate().setDuration(shortAnimTime).alpha(show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    Log.v("sengsational", "setting visibility of 1progressView: show=" + show);
                    progressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            Log.v("sengsational", "setting visibility of 2progressView: show=" + show);

            progressView.setVisibility(show ? View.VISIBLE : View.GONE);
            topLevelView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showMessage(String message, int toastLength) {
        Toast.makeText(this, message, toastLength).show();
    }

    @Override public void showDialog(String message, long daysSinceQuiz) {
        Log.v("sengsational", "showDialog about quiz");
        android.app.AlertDialog.Builder quizDialog = new android.app.AlertDialog.Builder(this);

        quizDialog.setMessage(message + " Go to quiz now?");
        quizDialog.setCancelable(true);
        quizDialog.setPositiveButton("Open Browser", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
                Intent browserIntent = new Intent(Intent.ACTION_VIEW);
                browserIntent.setData(Uri.parse("http://www.saucerknurd.com/glassnite/beerknurd-glassnite.php?" +
                                "homestore=" + prefs.getString(STORE_NAME, "Charlotte Flying Saucer") +
                                "&email=" + prefs.getString(EMAIL_ADDRESS, "unknown@unknown.com") +
                                "&UFO=" + prefs.getString(AUTHENTICATION_NAME, "106011") +
                                "&FirstName=" + prefs.getString(FIRST_NAME, "Joe") +
                                "&LastName=" + prefs.getString(LAST_NAME, ""))); // NOTE THAT THE LAST NAME HAS NOT BEEN PULLED FROM THE LOGIN PAGE!!!
                startActivity(browserIntent);
            }
        });
        quizDialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                dialog.dismiss();
            }
        });
        Log.v("sengsational", "about to show dialog.");
        quizDialog.create().show();
    }

    @Override public void setStoreView(boolean resetPresentation) {
        boolean clearUserData = false;
        if (resetPresentation) {
            Log.v("sengsational","TLA.setStoreView() with resetPresentation " + resetPresentation + " and clearUserData " + clearUserData);
            setToStorePresentation(clearUserData);
        } else {
            Log.v("sengsational","TLA.setStoreView() Not doing anything (resetPresentation " + resetPresentation + ")");
        }
        setUbereatsButtonVisibility();
    }

    @Override public void setUserView() {
        setToUserPresentation();
    }

    @Override
    public void setUsernameError(String message) {

    }

    @Override
    public void setPasswordError(String message) {
// TODO: Implement
    }

    @Override
    public void navigateToHome() {
// TODO: Implement
    }

}


