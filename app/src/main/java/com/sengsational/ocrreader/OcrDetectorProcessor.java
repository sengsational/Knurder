/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.sengsational.ocrreader;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewParent;

import com.sengsational.knurder.ConcurrentHashSet;
import com.sengsational.knurder.KnurderApplication;
import com.sengsational.knurder.SaucerItem;
import com.sengsational.knurder.TopLevelActivity;
import com.sengsational.knurder.UfoDatabaseAdapter;
import com.sengsational.ocrreader.camera.GraphicOverlay;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.sengsational.ocrreader.camera.Levenshtein;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;

import static com.sengsational.knurder.SaucerItem.BREWERY_CLEANUP;
import static com.sengsational.knurder.TopLevelActivity.STORE_NUMBER;
import static com.sengsational.knurder.TopLevelActivity.prefs;

/**
 * A very simple Processor which gets detected TextBlocks and adds them to the overlay
 * as OcrGraphics.
 *
 */
public class OcrDetectorProcessor implements Detector.Processor<TextBlock>{
    private static final String TAG = OcrDetectorProcessor.class.getSimpleName();
    private final String mFlavor;

    private GraphicOverlay<OcrGraphic> mGraphicOverlay;

    public OcrDetectorProcessor(GraphicOverlay<OcrGraphic> ocrGraphicOverlay, String flavor, Context context) {
        if (prefs == null) prefs = PreferenceManager.getDefaultSharedPreferences(context);
        mGraphicOverlay = ocrGraphicOverlay;
        mFlavor = flavor;
    }

    @Override
    public void release() {
        mGraphicOverlay.clear();
        ViewParent aView = mGraphicOverlay.getParent();
        Log.v(TAG, "parent view:" + aView.getClass().getName());
    }

    @Override
    public void receiveDetections(Detector.Detections<TextBlock> detections) {
        mGraphicOverlay.clear();
        SparseArray<TextBlock> items = detections.getDetectedItems();
        for (int i = 0; i < items.size(); ++i){
            TextBlock item = items.valueAt(i);
            if (item != null && item.getValue() != null) {
                if ("newArrivals".equals(mFlavor)) {
                    OcrScanHelper.getInstance(KnurderApplication.getContext()).scanNewArrivals(item);
                } else {
                    OcrScanHelper.getInstance(KnurderApplication.getContext()).scanTapMenu(item);
                }
            }
            OcrGraphic graphic = new OcrGraphic(mGraphicOverlay, item);
            mGraphicOverlay.add(graphic);
        }
    }
}
