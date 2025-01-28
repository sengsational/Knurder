package com.sengsational.knurder;

import android.app.LoaderManager;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;

import java.util.Date;

import static com.sengsational.knurder.KnurderApplication.getContext;

/**
 * Created by Dale Seng on 6/1/2016.
 */
public class WebResultListenerImpl implements WebResultListener, LoaderManager.LoaderCallbacks<Cursor> {
    private static final String TAG = WebResultListenerImpl.class.getSimpleName();

    private final DataView aView;
    private AppCompatActivity aActivity;

    public WebResultListenerImpl (DataView aView) {
        this.aView = aView;
        if (aView instanceof AppCompatActivity) {
            aActivity = (AppCompatActivity)aView;
        }
    }

    @Override
    public void saveValidCredentials(final String authenticationName, final String password, final String savePassword, final String mou, final String storeNumber, final String userName, final String tastedCount) {
        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                aView.saveValidCredentials(authenticationName, password, savePassword, mou, storeNumber, userName, tastedCount);
                //aView.setUserView(); <<< Don't do this here because the must have a good tasted list first
            }
        });
        if (aView == null) return;
    }

    @Override
    public void saveValidCardCredentials(final String cardNumber, final String cardPin, final String savePin, final String mou, final String storeNumber, final String userName, final String tastedCount) {
        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                aView.saveValidCardCredentials(cardNumber, cardPin, savePin, mou, storeNumber, userName, tastedCount);
            }
        });
        if (aView == null) return;
    }

    @Override public void saveValidStore(final String storeNumber){
        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.v(TAG, "WebResultListenerImpl.saveValidStore");
                aView.saveValidStore(storeNumber);
            }
        });

    }

    @Override public void onStoreListSuccess(final boolean resetPresentation, final boolean menuDataAdded) {

        // DRS 20160815 - Added 3 - old list reminder
        SharedPreferences.Editor editor = TopLevelActivity.prefs.edit();
        editor.putLong(TopLevelActivity.LAST_LIST_DATE, new Date().getTime());
        editor.apply();

        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.v(TAG, "WebResultListenerImpl.onStoreListSuccess");
                aView.setStoreView(resetPresentation);
                if (menuDataAdded) {
                    aView.showMessage("You've got the current beer list plus details!");
                } else {
                    aView.showMessage("You've got the current beer list!");
                }
            }
        });

    }

    @Override public void onTastedListSuccess() {

        // DRS 20160815 - Added 3 - old list reminder
        SharedPreferences.Editor editor = TopLevelActivity.prefs.edit();
        editor.putLong(TopLevelActivity.LAST_TASTED_DATE, new Date().getTime());
        editor.apply();

        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                aView.showMessage("You've got your tasted list!");
                aView.setUserView();
            }
        });
    }
    @Override public void onMemberDataSuccess() {
        SharedPreferences.Editor editor = TopLevelActivity.prefs.edit();
        editor.putLong(TopLevelActivity.LAST_TASTED_DATE, new Date().getTime());
        editor.apply();

        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                aView.showMessage("You've got your tasted list!");
                aView.setUserView();
            }
        });
    }

    @Override public void onSuccess(String mSavePin, String cardPin, String mMou, String cardNumber, String storeNumberLogon) {
        // This place and saveValidCredentials() are the places where changes to save cardPin and mou preferences happen
        SharedPreferences.Editor editor = TopLevelActivity.prefs.edit();

        // Save Successfully used credentials
        editor.putString(TopLevelActivity.SAVE_CARD_PIN, mSavePin);  // mSavePin is "Y" or "N"
        if ("Y".equals(mSavePin)) editor.putString(TopLevelActivity.CARD_PIN, cardPin);
        else editor.remove(TopLevelActivity.CARD_PIN);
        editor.putString(TopLevelActivity.MOU, mMou);
        editor.putString(TopLevelActivity.CARD_NUMBER, cardNumber);
        editor.putString(TopLevelActivity.STORE_NUMBER_LOGON, storeNumberLogon);
        editor.apply();

        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                //aView.showMessage("Flagged beers placed into 'Brews on Queue'");
                aView.setUserView();
            }
        });
    }


    public void onQuizPageSuccess(boolean mAlreadyPassed, long mQuizDateMs) {
        long nowMs = new Date().getTime();
        long msSinceQuiz = nowMs - mQuizDateMs;
        final long daysSinceQuiz = msSinceQuiz / 1000 / 60 / 60 / 24;
        boolean testing = false;

        long lastQuizMsRecorded = TopLevelActivity.prefs.getLong(TopLevelActivity.LAST_QUIZ_MS, 0L);

        Log.v(TAG, "mAlreadyPassed: " + mAlreadyPassed + " daysSinceQuiz: " + daysSinceQuiz  + " lastQuizMsRecorded: " + lastQuizMsRecorded + " mQuizDateMs: " + mQuizDateMs);
        // Put this quiz date ms into the user settings or do nothing if there is no new news
        if (lastQuizMsRecorded == mQuizDateMs && !testing) {
            return;
        } else {
            SharedPreferences.Editor editor = TopLevelActivity.prefs.edit();
            editor.putLong(TopLevelActivity.LAST_QUIZ_MS, mQuizDateMs);
            editor.apply();
        }

        // Do nothing if they've already passed the quiz
        if (mAlreadyPassed && !testing){
            return;
        }

        // Do nothing if the quiz is really old
        if (daysSinceQuiz > 13 && !testing) return;

        // The quiz is relatively new, they haven't passed it, and this is the first time we've seen this quiz, let's alert them!!
        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                String releaseDateLanguage = "The date on it is " + daysSinceQuiz + " days ago.";
                if (daysSinceQuiz <= 0 ) releaseDateLanguage = "It is a new quiz.";
                aView.showDialog("There appears to be a Captain Keith's Quiz available!!\n\n" + releaseDateLanguage + "\n\n", daysSinceQuiz);
            }
        });
    }

    public void onOcrScanSuccess(final Intent data) {
        if (aActivity == null || aView == null) {
            Log.v(TAG, "WebResultListener.onOcrScanSuccess with activity " + aActivity +  " and view " + aView + " not doing anything.");
            return;
        }
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.v(TAG, "WebResultListener.onOcrScanSuccess.");
                OcrBase ocrBase = (OcrBase)aActivity;
                Log.v(TAG, "WebResultListener.onOcrScanSuccess..");
                ocrBase.onActivityResult(OcrBase.OCR_REQUEST, OcrBase.RESULT_OK, data);
            }
        });

    }
    public void onOcrScanProgress(final int completePercent) {
        try {
            aActivity.runOnUiThread(new Runnable() {
                public void run() {
                    Log.v(TAG, "WebResultListener.onOcrScanProgress.");
                    OcrBase ocrBase = (OcrBase)aActivity;
                    ocrBase.onActivityProgress(completePercent);
                }
            });
        } catch (Throwable t) {
            Log.v(TAG, "WebResultListener.onOcrScanProgress FAILED: " + t.getLocalizedMessage());
        }
    }

    @Override public void setToUserPresentation() {
        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                aView.setUserView();
            }
        });
    }


    @Override
    public void onFinished() {
        if (aActivity == null || aView == null) {
            Log.v(TAG, "WebResultListener.onFinished with activity " + aActivity +  " and view " + aView + " not doing anything.");
            return;
        }
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                Log.v(TAG, "WebResultListener.onFinished.");
                aView.showProgress(false);
                aView.navigateToHome();
            }
        });
    }

    // All errors now just go through this one method.  These call methods in the main activity and manage details with the UI elements
    private void handleError(final String errorMessage) {
        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                aView.showProgress(false);
                aView.showMessage(errorMessage);
                aView.setPasswordError(errorMessage);
            }
        });
    }

    //////////////////////These are the errors that can be thrown by the Interactors (StoreListInteractor and TastedListInteractor)
    @Override public void onError(String message) {
        handleError(message);
    }

    @Override public void sendStatusToast(String message, int toastLength) {
        if (aActivity == null || aView == null) return;
        aActivity.runOnUiThread(new Runnable() {
            public void run() {
                aView.showMessage(message, toastLength);
            }
        });
    }

    @Override public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(getContext(),
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI, ContactsContract.Contacts.Data.CONTENT_DIRECTORY), null,

                // Select only cardnoes.
                ContactsContract.Contacts.Data.MIMETYPE + " = ?", new String[]{ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE},

                // Show primary cardnoes first. Note that there won't be
                // a primary cardno if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");

        // **********   Caution: I replaced "ProfileQuery.PROJECTION" above with null. This was after getting rid of the contacts/autocomplete stuff.
    }

    @Override public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
    }

    @Override public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }

}

interface WebResultListener {
    void onError(String message);
    void sendStatusToast(String message, int toastLength);
    void saveValidCredentials(String authenticationName, String password, String savePassword, String mou, String storeNumber, String userName, String tastedCount);
    void saveValidCardCredentials(String cardNumber, String cardPin, String savePin, String mou, String storeNumber, String userName, String tastedCount);
    void saveValidStore(String storeNumber);
    void onStoreListSuccess(boolean nResetPresentation, boolean menuDataAdded);
    void onTastedListSuccess();
    void onMemberDataSuccess();
    void onSuccess(String mSavePin, String cardPin, String mMou, String cardNumber, String storeNumberLogon);
    void setToUserPresentation();
    void onOcrScanSuccess(final Intent data);
    void onOcrScanProgress(final int completePercent);
    void onFinished();


    void onQuizPageSuccess(boolean mAlreadyPassed, long mQuizDateMs);

}
