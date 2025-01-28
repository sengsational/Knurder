package com.sengsational.knurder;

import android.content.Context;
import android.util.Log;

/**
 * Created by Dale Seng on 5/30/2016.
 */
public class MemberDataPresenterImpl implements MemberDataPresenter {
    private static final String TAG = "MemberDataPresenterImpl";

    private DataView dataView;
    private WebResultListener webResultListener;

    public MemberDataPresenterImpl(DataView dataView) {
        this.dataView = dataView;
        this.webResultListener = new WebResultListenerImpl((DataView) dataView);
    }

    // Implementations for MemberDataPresenter
    @Override
    public void getMemberData(String cardNumber, String cardPin, String mou, String savePin, String storeNumber, Context context) {
        boolean fieldsAreValid = true;
        if (fieldsAreValid) {
            // Run Async Task (does not block)
            // These three variables are used in the refresh of queued beers.  This is not a very good situation, but comes from having that functionality running before the old site shutdown.
            String brewIds = "";
            String brewNames = "";
            String storeNumberOfList = "";
            new MemberDataInteractorImpl().getMemberDataFromWeb(cardNumber, cardPin, mou, savePin, storeNumber, storeNumberOfList, brewIds, brewNames, webResultListener, dataView, context);
        } else {
            dataView.showProgress(false);
        }
    }
    // Implementations for MemberDataPresenter
    @Override
    public void getMemberData(String storeNumber, Context context) {
        boolean fieldsAreValid = true;
        if (fieldsAreValid) {
            // Run Async Task (does not block)
            new MemberDataInteractorImpl().getMemberDataFromWeb(storeNumber, webResultListener, dataView, context);
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
            Log.v(TAG, "TLPI.onSuccess()");
            dataView.showProgress(false);
            dataView.navigateToHome();
        }
    }

}

interface MemberDataPresenter {
    void getMemberData(String authenticationName, String password, String mou, String savePassword, String storeNumber, Context context);
    void getMemberData(String storeNumber, Context context);
    void onDestroy();
    void onUsernameError();
    void onPasswordError();
    void onSuccess();
}
