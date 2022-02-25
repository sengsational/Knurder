package com.sengsational.knurder;

import android.util.Log;

/**
 * Created by Dale Seng on 5/30/2016.
 */
public class TastedListPresenterImpl implements TastedListPresenter {

    private DataView dataView;
    private WebResultListener webResultListener;

    public TastedListPresenterImpl(DataView dataView) {
        this.dataView = dataView;
        this.webResultListener = new WebResultListenerImpl((DataView) dataView);
    }

    // Implementations for TastedListPresenter
    @Override
    public void getTastedList(String authenticationName, String password, String mou, String savePassword, String storeNumber) {
        boolean fieldsAreValid = true; // TODO: implement this
        if (fieldsAreValid) {
            // Run Async Task (does not block)
            new TastedListInteractorImpl().getTastedListFromWeb(authenticationName, password, mou, savePassword, storeNumber, webResultListener);

            // Call show progress in the view object
            if (dataView != null) dataView.showProgress(true);
            else Log.v("sengsational", "TastedListPresenterImpl.getTastedList() dataView was null");
        } else {
            dataView.showProgress(false);
        }

    }

    @Override
    public void onDestroy() {
        dataView = null;
    }

    @Override public void onUsernameError() {
        if (dataView != null) {
            dataView.setUsernameError("user name error");
            dataView.showProgress(false);
        }
    }

    @Override public void onPasswordError() {
        if (dataView != null) {
            dataView.setPasswordError("This password is too short");
            dataView.showProgress(false);
        }
    }

    @Override public void onSuccess() {
        if (dataView != null) {
            Log.v("sengsational", "TLPI.onSuccess()");
            dataView.showProgress(false);
            dataView.navigateToHome();
        }
    }

}

interface TastedListPresenter {
    void getTastedList(String authenticationName, String password, String mou, String savePassword, String storeNumber);
    void onDestroy();
    void onUsernameError();
    void onPasswordError();
    void onSuccess();
}
