package com.sengsational.knurder;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.preference.PreferenceManager;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.Date;
import java.util.List;

/**
 * A login screen that offers login via username/password.
 */
public class LoginActivity extends AppCompatActivity implements DataView {
    private static final String TAG = "LoginActivity";
    private static SharedPreferences prefs;

    // UI references.
    private EditText mUsernameView;
    private Spinner mSpinnerView;
    private View mProgressView;
    private View mLoginFormView;
    private View mCheckboxView;
    private View mMouView;

    private TastedListPresenterImpl tastedListPresenter;

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    ActionBar mActionBar;
    EditText mPasswordView;


    // Flags
    private boolean mClearTasted;

    // From preferences
    private String mStoreNumber;
    private String mAuthenticationName;
    private String mPassword;
    private String mMou;
    private String mStoreName;
    private String mSavePassword;
    private boolean mSavePasswordSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        tastedListPresenter = new TastedListPresenterImpl(this);  // Creates a WebResultListener

        Intent intent = getIntent();

        // Get defaults and prepare store list from shared preferences
        prefs = PreferenceManager.getDefaultSharedPreferences(this);

        // DRS 20161006 - Add 1 - Allow Change Location while logged-in
        // The logon store number can be different than the active store number
        mStoreNumber = prefs.getString(TopLevelActivity.STORE_NUMBER_LOGON, prefs.getString(TopLevelActivity.STORE_NUMBER_LIST, "13888"));
        mAuthenticationName = prefs.getString(TopLevelActivity.AUTHENTICATION_NAME, "");
        mPassword = prefs.getString(TopLevelActivity.PASSWORD, "");
        mMou = prefs.getString(TopLevelActivity.MOU,"0");
        mStoreName = prefs.getString(TopLevelActivity.STORE_NAME_LIST, "");

        mSavePasswordSwitch = prefs.getBoolean("password_switch", true);

        mActionBar = getSupportActionBar();
        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);


        // Set up the login form.
        mUsernameView =  (EditText) findViewById(R.id.username);
        mUsernameView.setText(mAuthenticationName);

        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setText(mPassword);

        mMouView = (CheckBox) findViewById(R.id.mouCheckBox);
        mMouView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                //SharedPreferences.Editor editor = prefs.edit();
                if(((CheckBox)v).isChecked()){
                    //editor.putString(TopLevelActivity.MOU,"1");
                    mMou = "1";
                    Log.v(TAG,"The checkbox is set to MOU");
                }else{
                    //editor.putString(TopLevelActivity.MOU,"0");
                    mMou = "0";
                    Log.v(TAG,"The checkbox is set to not MOU");
                }
                //editor.apply();
            }
        });
        mMouView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                AlertDialog.Builder mouExplainationDialog = new AlertDialog.Builder(LoginActivity.this);
                mouExplainationDialog.setMessage("MOU stands for \"Master Of the Universe\".  You'd know it if you were an MOU.  Most people don't check this box.");
                mouExplainationDialog.setCancelable(true);
                mouExplainationDialog.setNeutralButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
                mouExplainationDialog.create().show();

                boolean longClickConsumed = true;
                return longClickConsumed;
            }
        });

        Log.v(TAG, "from state the login values: " + mAuthenticationName + " " + mPassword + " " + mStoreNumber + " " + mMou);

        mCheckboxView = (CheckBox) findViewById(R.id.showPasswordCheckBox);
        mCheckboxView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if(((CheckBox)v).isChecked()){
                    mPasswordView.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                }else{
                    mPasswordView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);                }
            }
        });

        List<String> storeNames = StoreNameHelper.getInstance().getSortedStoreNames(); // This is the list put into the ArrayAdapter for Spinner, should be sorted
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, storeNames); // <<<<<<<<<<List of storeNames put into ArrayAdapter
        final int lastStorePos = dataAdapter.getPosition(mStoreName);
        mSpinnerView = (Spinner) findViewById(R.id.store_list);
        mSpinnerView.setSelection(lastStorePos);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mSpinnerView.setAdapter(dataAdapter);
        mSpinnerView.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) position = lastStorePos;
                if (parent.getItemAtPosition(position) == null) return;
                Log.v(TAG, "item at position: " + parent.getItemAtPosition(position));
                mSpinnerView.setSelection(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });


        Button mUsernameSignInButton = (Button) findViewById(R.id.username_sign_in_button);
        Log.v(TAG,">>>>>>>>>>>>>>>>>>><<<<<<<<<<<<<<<<<<<<<<<<<<<<<");
        if ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE) != null) {
            mUsernameSignInButton.setText(R.string.action_sign_in);
            mUsernameSignInButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    hideSoftKeyBoard();
                    getTastedList(); // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<GET TASTED LIST
                }
            });
        } else {
            mUsernameSignInButton.setText(R.string.action_no_network);
            mUsernameSignInButton.setEnabled(false);
        }
    }

    private void hideSoftKeyBoard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);

        if(imm.isAcceptingText()) { // verify if the soft keyboard is open
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    ///////////////////////////////////Methods called by user action//////////////////
    // Called when user presses the login button.  TODO: Here we can see if the store changed
    @Override
    public void getTastedList() {
        showProgress(true);

        // Reset errors.
        mUsernameView.setError(null);
        mPasswordView.setError(null);

        // Pull user's inputs from the form
        String authenticationName = mUsernameView.getText().toString();
        String password = mPasswordView.getText().toString();
        Object selectedItem = mSpinnerView.getSelectedItem();
        String storeName = prefs.getString(TopLevelActivity.STORE_NAME_LIST, "(undefined)");
        if (selectedItem != null) storeName = selectedItem.toString();
        Log.v(TAG, "storeName in attemptLogin: " + storeName);
        String storeNumber = StoreNameHelper.getInstance().getStoreNumberFromName(storeName);
        //boolean storeNameChanged = !storeName.equals(prefs.getString(TopLevelActivity.STORE_NAME,TopLevelActivity.DEFAULT_STORE_NAME));
        //Log.v(TAG, "Changed: " + storeNameChanged + " The last store was : " + (prefs.getString(TopLevelActivity.STORE_NAME,TopLevelActivity.DEFAULT_STORE_NAME)) + " and now we have  " + storeName);
        Log.v(TAG, "storeNumber from Spinner: " + storeNumber);

        // This place and saveValidCredentials() are the places where changes to save password and mou preferences happen
        SharedPreferences.Editor editor = prefs.edit();
        // password
        mSavePassword = mSavePasswordSwitch?"T":"F";
        editor.putString(TopLevelActivity.SAVE_PASSWORD, mSavePassword);
        if (mSavePasswordSwitch) editor.putString(TopLevelActivity.PASSWORD, password);
        else editor.remove(TopLevelActivity.PASSWORD);
        // mou
        editor.putString(TopLevelActivity.MOU, mMou);
        editor.apply();

        tastedListPresenter.getTastedList(authenticationName, password, mMou, mSavePassword, storeNumber); // <<<<<<<<<<<<<<<< does not block
    }

    @Override
    public void getMemberData() {
        Log.e(TAG, "getMemberData called in LoginActivity");

    }

    @Override public void getStoreList(boolean resetPresentation, boolean checkForQuiz){
        Log.e(TAG, "getStoreList called in LoginActivity");
    }


    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    public void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
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
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }


    //////////////////////////////////////////Methods called by the listener////////////////
    @Override public void saveValidCredentials(String authenticationName, String password, String savePassword, String mou, String storeNumber, String userName, String tastedCount) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(TopLevelActivity.AUTHENTICATION_NAME, authenticationName);
        if ("T".equals(TopLevelActivity.SAVE_PASSWORD)) editor.putString(TopLevelActivity.PASSWORD, password);
        editor.putString(TopLevelActivity.MOU, mou);
        editor.putString(TopLevelActivity.STORE_NUMBER_LOGON, storeNumber);
        editor.putString(TopLevelActivity.TASTED_COUNT, tastedCount);
        editor.putString(TopLevelActivity.USER_NAME, userName);
        editor.apply();
    }

    @Override
    public void saveValidCardCredentials(String cardNumber, String cardPin, String savePin, String mou, String storeNumber, String userName, String tastedCount) {
        Log.e(TAG, "Unexpected call of old saveValidCardCredentials()");
    }

    // Only called from WebResultListner which is called from StoreListInteractor - The storeNumber is for the list, not the credentials.
    @Override public void saveValidStore(String storeNumber) {
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(TopLevelActivity.STORE_NUMBER_LIST, storeNumber); // DRS 20161006 - Leaving this as STORE_NUMBER_LIST, not changing to STORE_NUMBER_LOGON
        editor.putLong(TopLevelActivity.LAST_LIST_DATE, new Date().getTime()); // DRS 20160815 - Added 1 - old list reminder
        editor.apply();
    }

    @Override public void navigateToHome(){
        finish(); /// I think this sends us back to the main page.
    }

    @Override public void onDestroy() {
        tastedListPresenter.onDestroy();
        super.onDestroy();
    }

    @Override public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void showMessage(String message, int toastLength) {
        Toast.makeText(this, message, toastLength).show();
    }

    @Override public void showDialog(String message, long days) {
        // no implementation needed here
    }

    @Override public void setUsernameError(String message) {
        mUsernameView.setError(message);
        mPasswordView.requestFocus();
        showProgress(false);
    }

    @Override public void setPasswordError(String message) {
        mPasswordView.setError(message);
        mPasswordView.requestFocus();
        showProgress(false);
    }

    @Override public void setStoreView(boolean resetPresentation) {
        //TopLevelActivity.setToStorePresentation(false);
        KnurderApplication.setPresentationMode("store-false"); // variable picked-up in TopLevelActivity.onActivityResult
    }

    @Override public void setUserView() {
        //Thread.dumpStack();
        Log.v(TAG,"LA.setUserView()");
        //TopLevelActivity.setToUserPresentation();
        KnurderApplication.setPresentationMode("user"); // variable picked-up in TopLevelActivity.onActivityResult
    }
}

