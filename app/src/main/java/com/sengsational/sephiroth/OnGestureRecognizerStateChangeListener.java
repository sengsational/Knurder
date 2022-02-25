package com.sengsational.sephiroth;

/**
 * Created by Owner on 4/8/2017.
 */


import android.support.annotation.NonNull;

/**
 * Created by alessandro crugnola on 11/20/16.
 */
public interface OnGestureRecognizerStateChangeListener {
    void onStateChanged(@NonNull final UIGestureRecognizer recognizer);
}