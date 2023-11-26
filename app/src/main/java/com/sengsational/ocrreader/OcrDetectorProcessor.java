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

import android.content.Context;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;
import android.view.ViewParent;

import com.sengsational.ocrreader.camera.GraphicOverlay;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.text.TextBlock;

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
        Log.v(TAG, "CLASS NO LONGER USED.");
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
        Log.v(TAG, "CLASS NO LONGER USED.");
        mGraphicOverlay.clear();
        SparseArray<TextBlock> items = detections.getDetectedItems();
        for (int i = 0; i < items.size(); ++i){
            TextBlock item = items.valueAt(i);
            if (item != null && item.getValue() != null) {
                if ("newArrivals".equals(mFlavor)) {
                    //OcrScanHelper.getInstance().scanNewArrivals(item);
                } else {
                    //OcrScanHelper.getInstance().scanTapMenu(item);
                }
            }
            OcrGraphic graphic = new OcrGraphic(mGraphicOverlay, item);
            mGraphicOverlay.add(graphic);
        }
    }
}
