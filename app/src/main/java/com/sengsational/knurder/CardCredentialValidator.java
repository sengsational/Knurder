package com.sengsational.knurder;

import android.util.Log;

/**
 * Created by Dale Seng on 8/27/2021.
 */
public class CardCredentialValidator {

    private DataView dataView;
    private WebResultListener webResultListener;

    public CardCredentialValidator(DataView dataView) {
        this.dataView = dataView;
        this.webResultListener = new WebResultListenerImpl((DataView) dataView);
    }

    public void validateCredentials(String cardNumber, String cardPin, String mou, String savePin, String storeNumber, String brewIds) {
        boolean fieldsAreValid = true; // TODO: implement this
        if (fieldsAreValid) {
            // Run Async Task (does not block)
            new CardnumberCredentialInteractor().validateCardnumberCredentialsFromWeb(cardNumber, cardPin, mou, savePin, storeNumber, brewIds, webResultListener);

            // Call show progress in the view object
            if (dataView != null) dataView.showProgress(true);
            else Log.v("sengsational", "TastedListPresenterImpl.getTastedList() dataView was null");
        } else {
            dataView.showProgress(false);
        }

    }

    public void onDestroy() {
        dataView = null;
    }

    public void onUsernameError() {
        if (dataView != null) {
            dataView.setUsernameError("user name error");
            dataView.showProgress(false);
        }
    }

    public void onPasswordError() {
        if (dataView != null) {
            dataView.setPasswordError("This password is too short");
            dataView.showProgress(false);
        }
    }

    public void onSuccess() {
        if (dataView != null) {
            Log.v("sengsational", "TLPI.onSuccess()");
            dataView.showProgress(false);
            dataView.navigateToHome();
        }
    }

}

