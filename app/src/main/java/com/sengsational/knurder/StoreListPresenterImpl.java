package com.sengsational.knurder;

import android.content.Context;
import android.util.Log;

/**
 * Created by Dale Seng on 5/30/2016.
 */
public class StoreListPresenterImpl implements StoreListPresenter {
    private static final String TAG = "StoreListPresenterImpl";

    private DataView dataView;
    private WebResultListener webResultListener;

    public StoreListPresenterImpl(DataView dataView) {
        this.dataView = dataView;
        this.webResultListener = new WebResultListenerImpl(dataView);
    }

    // Implementations for StoreListPresenter
    @Override
    public void getStoreList(String storeNumber, boolean resetPresentation, Context context) {
        boolean fieldsAreValid = true; // TODO: implement this
        if (fieldsAreValid) {
            // Run Async Task (does not block)
            new StoreListInteractorImpl().getStoreListFromWeb(storeNumber, webResultListener, resetPresentation, context);

            // Call show progress in the view object
            if (dataView != null) dataView.showProgress(true);
            else Log.v(TAG, "TastedListPresenterImpl.getTastedList() tastedListView was null");
        } else {
            if (dataView != null) dataView.showProgress(false);
        }
    }

    @Override
    public void onDestroy() {
        dataView = null;
    }

}

interface StoreListPresenter {
    void getStoreList(String storeNumber, boolean resetPresentation, Context context);
    void onDestroy();
}

