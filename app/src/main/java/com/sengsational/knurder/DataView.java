package com.sengsational.knurder;

/**
 * Created by Dale Seng on 6/2/2016.
 */
interface DataView {
    // To Process
    void getTastedList();
    void getStoreList(boolean resetPresentation, boolean checkForQuiz);

    // From Process
    void showProgress(final boolean show);
    void onDestroy();
    void showMessage(String message);
    void showMessage(String message, int toastLength);
    void setUsernameError(String message);
    void setPasswordError(String message);
    void saveValidCredentials(String authenticationName, String password, String savePassword, String mou, String storeNumber, String userName, String tastedCount);
    void saveValidStore(String storeNumber);
    void navigateToHome();
    void setStoreView(boolean resetPresentation);
    void setUserView();
    void showDialog(String message, long daysSinceQuiz);
}