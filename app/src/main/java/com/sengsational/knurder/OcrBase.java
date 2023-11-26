package com.sengsational.knurder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PorterDuff;
import android.os.Build;
import android.preference.PreferenceManager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.sengsational.ocrreader.OcrCaptureActivity;
import com.sengsational.ocrreader.OcrScanHelper;

import static com.sengsational.knurder.BeerSlideActivity.EXTRA_TUTORIAL_TYPE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TEXT_RESOURCE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TITLE_RESOURCE;
import static com.sengsational.knurder.R.color.colorActivePrimary;
import static com.sengsational.knurder.TopLevelActivity.STORE_NAME_LIST;

public class OcrBase extends AppCompatActivity implements DataView {
    private static final String TAG = OcrBase.class.getSimpleName();
    public static final String PREF_OCR_BASE_TUTORIAL = "prefOcrBaseTutorial";
    public static final String PREF_OCR_MENU_TUTORIAL = "prefOcrMenuTutorial";
    public static final String PREF_OCR_NEW_TUTORIAL = "prefOcrNewTutorial";
    public static final String PREF_TOUCHLESS_TUTORIAL = "prefTouchlessTutorial";
    private static final int POST_HELP_OCR_MENU_INTENT = 1999;
    private static final int POST_HELP_OCR_NEW_INTENT = 1998;
    private static final int POST_HELP_TOUCHLESS_INTENT = 1997;

    public static final int OCR_REQUEST = 42;
    private static final int GLASS_SIZE_REQUEST = 142;
    private static final int TOUCHLESS_REQUEST = 242;
    private static final int QR_SCAN_REQUEST = 342;
    public static SharedPreferences prefs;
    public static final String USE_LIGHT_FOR_SCAN = "useLightForScanPref";
    public static final String SCANNED_COUNT = "scannedCountPref";
    public static final String SCAN_COVERAGE = "scanCoveragePref";

    private ImageView lightbulbSwitchView;
    private TextView locationTextView;
    private TextView menuItemCount;
    private TextView addedMenuItemCount;
    private int mNewItemCount;

    // UI references
    private View mProgressView;
    private View mOcrBaseView;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ocr_base);
        prefs = PreferenceManager.getDefaultSharedPreferences(OcrBase.this);

        lightbulbSwitchView = (ImageView)findViewById(R.id.lightbulb_switch);
        lightbulbSwitchView.setImageResource(prefs.getBoolean(USE_LIGHT_FOR_SCAN, true)?R.mipmap.ic_lightbulb_on:R.drawable.ic_lightbulb_outline_black_24dp);
        lightbulbSwitchView.getDrawable().setColorFilter(getResources().getColor(colorActivePrimary), PorterDuff.Mode.SRC_ATOP);

        locationTextView = (TextView)findViewById(R.id.location_text);
        locationTextView.setText(getResources().getString(R.string.store_location_name, prefs.getString(STORE_NAME_LIST, "")));

        menuItemCount = (TextView)findViewById(R.id.menu_item_count);
        menuItemCount.setText(getResources().getString(R.string.scanned_menu_item_text, prefs.getString(SCANNED_COUNT, "0")));

        addedMenuItemCount = (TextView)findViewById(R.id.added_menu_item_count);

        boolean showTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_OCR_BASE_TUTORIAL, true);
        if (savedInstanceState == null && showTutorial) {
            Intent popTutorialIntent = new Intent(OcrBase.this, PopTutorial.class);
            popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.ocr_base_instructions);
            popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.ocr_base_title);
            popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_OCR_BASE_TUTORIAL);
            startActivity(popTutorialIntent);
        }

        mProgressView = findViewById(R.id.touchless_progress);
        mOcrBaseView = findViewById(R.id.ocr_base);

        OcrScanHelper.getInstance(); // putting the context into the mostly static class is not allowed in Android (memory leak)
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("mNewItemCount", mNewItemCount);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mNewItemCount = savedInstanceState.getInt("mNewItemCount");
        addedMenuItemCount.setText(getResources().getString(R.string.added_menu_item_text, "" + mNewItemCount));
    }

    /*
    public void onClickStartOcrMenu(View aView) {
        boolean ocrMenuTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_OCR_MENU_TUTORIAL, true);
        Log.v(TAG, "looked up " + PREF_OCR_MENU_TUTORIAL + " and got back " + ocrMenuTutorial);
        if (ocrMenuTutorial) {
            Intent popTutorialIntent = new Intent(OcrBase.this, PopTutorial.class);
            popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.ocr_menu_instructions);
            popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.ocr_menu_title);
            popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_OCR_MENU_TUTORIAL);
            startActivityForResult(popTutorialIntent, POST_HELP_OCR_MENU_INTENT);
        } else {
            onActivityResult(POST_HELP_OCR_MENU_INTENT, RESULT_OK, null);
        }
    }
     */

    /*
    public void onClickStartOcrNew(View aView) {
        boolean ocrMenuTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_OCR_NEW_TUTORIAL, true);
        Log.v(TAG, "looked up " + PREF_OCR_NEW_TUTORIAL + " and got back " + ocrMenuTutorial);
        if (ocrMenuTutorial) {
            Intent popTutorialIntent = new Intent(OcrBase.this, PopTutorial.class);
            popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.ocr_new_instructions);
            popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.ocr_new_title);
            popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_OCR_NEW_TUTORIAL);
            startActivityForResult(popTutorialIntent, POST_HELP_OCR_NEW_INTENT);
        } else {
            onActivityResult(POST_HELP_OCR_NEW_INTENT, RESULT_OK, null);
        }
    }
    */

    public void onClickScanTouchless(View aView) {
        boolean touchlessMenuTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_TOUCHLESS_TUTORIAL, true);
        Log.v(TAG, "looked up " + PREF_TOUCHLESS_TUTORIAL + " and got back " + touchlessMenuTutorial);
        if (touchlessMenuTutorial) {
            Intent popTutorialIntent = new Intent(OcrBase.this, PopTutorial.class);
            popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.touchless_instructions);
            popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.touchless_title);
            popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_TOUCHLESS_TUTORIAL);
            startActivityForResult(popTutorialIntent, POST_HELP_TOUCHLESS_INTENT);
        } else {
            onActivityResult(POST_HELP_TOUCHLESS_INTENT, RESULT_OK, null);
        }

    }

    public void onClickListGlassSizes(View aView) {
        Intent glassSizeListIntent = new Intent(this, RecyclerOcrListActivity.class);
        startActivityForResult(glassSizeListIntent, GLASS_SIZE_REQUEST);
    }

    public void onActivityProgress(int completePercent) {
        addedMenuItemCount.setText(completePercent + "% scanned...");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.v(TAG, "--------- OcrBase.onActivityResult with requestCode " + requestCode + " and resultCode " + resultCode);
        Context listenerContext = getApplicationContext();
        if (requestCode == OCR_REQUEST) {
            Log.v(TAG, "Activity Result code " + resultCode + " being handled.  intent: " + intent);

            if (resultCode == RESULT_OK) {
                if (intent != null) {

                    // Update total menu item count in UI
                    int totalItemCount = intent.getIntExtra("totalItemCount", 0);

                    // Update preference on whether to show sort by glass and price
                    int totalTapCount = intent.getIntExtra("totalTapCount", 0);
                    float scanCoverage = (float)totalItemCount / (float)totalTapCount;
                    prefs = PreferenceManager.getDefaultSharedPreferences(OcrBase.this);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putFloat(SCAN_COVERAGE,  scanCoverage);
                    editor.apply();
                    Log.v(TAG, "SCAN_COVERAGE was "  + scanCoverage);

                    setTotalItemCountPreference(totalItemCount);
                    menuItemCount.setText(getResources().getString(R.string.scanned_menu_item_text, prefs.getString(SCANNED_COUNT, "0")));

                    // Update new items found count in UI
                    mNewItemCount = intent.getIntExtra("newItemCount", 0);
                    addedMenuItemCount.setText(getResources().getString(R.string.added_menu_item_text, "" + mNewItemCount));

                    // Optionally save the untappd data URL if it's new or different

                    // From the recent intent (MenusPageInteractorImpl)
                    final String untappdDataUrlString = intent.getStringExtra("untappdUrlExtra");
                    if (untappdDataUrlString != null && !"".equals(untappdDataUrlString)) {
                        String preferencesUntappdDataUrlString = UntappdHelper.getInstance().getUntappdUrlForCurrentStore("", this);

                        //DRS 20220802 - Add nag for them to send QR code.  This preference is to only ask once.
                        String oneTimeNagForQr = prefs.getString("oneTimeNagForQr","");
                        if (oneTimeNagForQr.equals("") && "".equals(preferencesUntappdDataUrlString)) {
                            editor.putString("oneTimeNagForQr", "DONE_ONCE");
                            editor.apply();
                            new AlertDialog.Builder(OcrBase.this)
                                    .setTitle("Send Untappd Url")
                                    .setMessage(Html.fromHtml(getResources().getString(R.string.menu_top_level_action_untappd, StoreNameHelper.getCurrentStoreName())))
                                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            // do nothing
                                        }
                                    })
                                    .setPositiveButton("Email the Untappd URL", new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int which) {
                                            UntappdHelper.getInstance().saveUntappdUrlForCurrentStore(untappdDataUrlString, listenerContext);
                                            Log.v("sengsational", "Email URL requested");
                                            Intent mailIntent = new Intent(Intent.ACTION_SEND);
                                            mailIntent.setType("message/rfc822");
                                            mailIntent.putExtra(Intent.EXTRA_EMAIL, new String[] {"Knurder.frog4food@recursor.net"});
                                            mailIntent.putExtra(Intent.EXTRA_SUBJECT, "Untappd URL for " + StoreNameHelper.getCurrentStoreName());
                                            mailIntent.putExtra(Intent.EXTRA_TEXT, "Here is the URL:  [ " + untappdDataUrlString + "].");
                                            try {
                                                startActivity(Intent.createChooser(mailIntent, "Send email.."));
                                            } catch (android.content.ActivityNotFoundException ex) {
                                                Toast.makeText(OcrBase.this, "NO EMAIL SERVICE AVAILABLE", Toast.LENGTH_SHORT).show();
                                            }
                                        }
                                    })
                                    .setIcon(android.R.drawable.ic_dialog_alert)
                                    .show();
                        } else if (!untappdDataUrlString.equals(preferencesUntappdDataUrlString)) {  // The previous value can be blank or different
                            // We have a legit untapped data URL that's new or different from what's in preferences
                            AlertDialog.Builder builder = new AlertDialog.Builder(this);
                            final SharedPreferences.Editor prefsEdit = prefs.edit();
                            builder.setTitle("New or Changed Menu Data Source");
                            builder.setMessage("Would you like to save this data source, using it for " + StoreNameHelper.getCurrentStoreName() + " from now on?");
                            builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    UntappdHelper.getInstance().saveUntappdUrlForCurrentStore(untappdDataUrlString, listenerContext);
                                }
                            });
                            builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                }
                            });
                            builder.show();
                        } else { // Same untappd data URL... no alert
                            Log.v(TAG, "The data URL was unchanged (typical situation).");
                        }
                    } else { // URL from web interaction was no good
                        Log.v(TAG, "The data URL was null or blank.");
                    }
                } else { // Intent was null
                    Log.v(TAG, "The intent data was null, so we have nothing to use to update the UI.")   ;
                }
            } else { // Result was not OK
                Log.v(TAG, "Activity Result code " + resultCode + " not `RESULT_OK`.");
                menuItemCount.setText(getResources().getString(R.string.scanned_menu_item_text, prefs.getString(SCANNED_COUNT, "0")));
                addedMenuItemCount.setText(getResources().getString(R.string.added_menu_item_text, "?"));
            }
        } else if (requestCode == GLASS_SIZE_REQUEST) {
            if (resultCode == RESULT_OK) {
                Log.v(TAG, "Activity Result code " + resultCode + " being handled-.");
                //Bundle captureBundle = data.getBundleExtra(somethingheremaybe);
            } else {
                Log.v(TAG, "Activity Result code " + resultCode + " not `RESULT_OK`-.");
            }
        } else if (requestCode == POST_HELP_OCR_MENU_INTENT) {
            if (resultCode == RESULT_OK) {
                Log.v(TAG, "Post help intent on activity result. GO!! Need to implement: " + resultCode);
                Intent ocrIntent = new Intent(this, OcrCaptureActivity.class);
                ocrIntent.putExtra("flavor", "tapMenu");
                ocrIntent.putExtra("lightbulb", prefs.getBoolean(USE_LIGHT_FOR_SCAN, true));
                startActivityForResult(ocrIntent, OCR_REQUEST);
            } else {
                Log.v(TAG, "Post help intent on activity result. NO GO. resultCode: " + resultCode);
            }
        } else if (requestCode == POST_HELP_OCR_NEW_INTENT) {
            if (resultCode == RESULT_OK) {
                Log.v(TAG, "Post help intent on activity result. GO!! Need to implement: " + resultCode);
                Intent ocrIntent = new Intent(this, OcrCaptureActivity.class);
                ocrIntent.putExtra("flavor", "newArrivals");
                ocrIntent.putExtra("lightbulb", prefs.getBoolean(USE_LIGHT_FOR_SCAN, true));
                startActivityForResult(ocrIntent, OCR_REQUEST);
            } else {
                Log.v(TAG, "Post help intent on activity result. NO GO. resultCode: " + resultCode);
            }
        } else if (requestCode == POST_HELP_TOUCHLESS_INTENT) {
            if (resultCode == RESULT_OK) {
                Log.v(TAG, "Post help intent on activity result. GO!!  " + resultCode);
                IntentIntegrator integrator = new IntentIntegrator(this); // `this` is the current Activity
                integrator.addExtra("TORCH_ENABLED", prefs.getBoolean(USE_LIGHT_FOR_SCAN, true));
                integrator.setPrompt("Scan the Touchless Menu QR code");
                integrator.setTimeout(8000);
                integrator.setOrientationLocked(true);
                //integrator.setTorchEnabled(true);
                integrator.initiateScan();
                // Note this returns to this method (onActivityResult) with IntentIntegrator.REQUEST_CODE
            } else {
                Log.v(TAG, "Post help intent on activity result. NO GO. resultCode: " + resultCode);
            }
        } else if (requestCode == IntentIntegrator.REQUEST_CODE)  {
            // This is after the user scanned the Touchless menu and we now need to USE the URL that was scanned
            IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
            if(result != null) {
                if(result.getContents() == null) {
                    Toast.makeText(this, "OcrBase onActivityResult Cancelled", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "OcrBase onActivityResult Scanned: " + result.getContents(), Toast.LENGTH_LONG).show();
                    // WE FOUND A  URL.  Now we need to go over the web to get the menu.  This is an AsynchTask.  The next line does not block.
                    new MenusPageInteractorImpl().getMenuDataFromWeb(result.getContents(), new WebResultListenerImpl(this), getApplicationContext());
                    // TODO: Need to show the spinner and/or show the scan results as they come in.
                }
            } else {
                super.onActivityResult(requestCode, resultCode, intent);
            }
        } else {
            Log.v(TAG, "Activity Result code " + resultCode + " not handled.");
        }
    }

    private void scanWithTimeout() {
    }

    private void setTotalItemCountPreference(int totalItemCount) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SCANNED_COUNT, "" + totalItemCount);
        editor.apply();
    }

    public void onClickToggleLightbulb(View view) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(USE_LIGHT_FOR_SCAN, !prefs.getBoolean(USE_LIGHT_FOR_SCAN, true));
        editor.apply();
        lightbulbSwitchView.setImageResource(prefs.getBoolean(USE_LIGHT_FOR_SCAN, true)?R.mipmap.ic_lightbulb_on:R.drawable.ic_lightbulb_outline_black_24dp);
        lightbulbSwitchView.getDrawable().setColorFilter(getResources().getColor(colorActivePrimary), PorterDuff.Mode.SRC_ATOP);

    }

    @Override
    public void showProgress(final boolean show) {
        // Copy from LoginActivity
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mOcrBaseView.setVisibility(show ? View.GONE : View.VISIBLE);
            mOcrBaseView.animate().setDuration(shortAnimTime).alpha(show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mOcrBaseView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mOcrBaseView.setVisibility(show ? View.GONE : View.VISIBLE);
        }

    }

    @Override
    public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showMessage(String message, int toastLength) {
        Toast.makeText(this, message, toastLength).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override public void getTastedList() {/*no implementation needed*/}
    @Override public void getStoreList(boolean resetPresentation, boolean checkForQuiz) {/*no implementation needed*/}
    @Override public void setUsernameError(String message) {/*no implementation needed*/}
    @Override public void setPasswordError(String message) {/*no implementation needed*/}
    @Override public void saveValidCredentials(String authenticationName, String password, String savePassword, String mou, String storeNumber, String userName, String tastedCount) {/*no implementation needed*/}
    @Override public void saveValidStore(String storeNumber) {/*no implementation needed*/}
    @Override public void showDialog(String message, long daysSinceQuiz) {/*no implementation needed*/}
    @Override public void navigateToHome() {/*no implementation needed*/}
    @Override public void setStoreView(boolean resetPresentation) {/*no implementation needed*/}
    @Override public void setUserView() {/*no implementation needed*/}
}