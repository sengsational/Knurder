package com.sengsational.knurder;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class TouchlessActivity extends AppCompatActivity {

    TextView ocrResult;
    WebResultListener nListener;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_touchless);
        ocrResult = (TextView)findViewById(R.id.scanResultText);
        //getTextFromImage(ocrResult);
        IntentIntegrator integrator = new IntentIntegrator(this); // `this` is the current Activity
        integrator.setPrompt("Scan the Touchless Menu QR code");
        integrator.setTimeout(8000);
        integrator.setOrientationLocked(false);
        integrator.initiateScan();

        WebResultListener nListener = new WebResultListenerImpl((DataView)this);
    }
    // Get the results:
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if(result != null) {
            if(result.getContents() == null) {
                Toast.makeText(this, "TouchlessActivity.onActivity Result Cancelled", Toast.LENGTH_LONG).show();
            } else {
                // Getting here means we have a URL
                String urlToTouchlessMenu = result.getContents();
                Toast.makeText(this, "TouchlessActivity.onActivity Result Scanned: " + urlToTouchlessMenu, Toast.LENGTH_LONG).show();

                // Rather than run the Async Task from here, we call another activity
                // new MenusPageInteractorImpl().getMenuDataFromWeb(urlToTouchlessMenu, nListener, getApplicationContext()); // Does not block

                // Call show progress in the view object <<< Does not work yet.... progress view does not show
                //DataView dataView = (DataView)this;
                //if (dataView != null) dataView.showProgress(true);
                //else Log.v("sengsational", "TouchlessActivity.onActivityResult data view was null");

            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }

    }

    // CREATED FOR TESTING ONLY. NOT ACTIVE.
    public void getTextFromImage(View view) {
        Bitmap bitmap = BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.scan_new);
        TextRecognizer textRecognizer = new TextRecognizer.Builder(getApplicationContext()).build();

        if (!textRecognizer.isOperational()) {
            Toast.makeText(getApplicationContext(), "Could not get text from the image.", Toast.LENGTH_SHORT).show();

        } else {
            Frame frame = new Frame.Builder().setBitmap(bitmap).build();
            SparseArray<TextBlock> items = textRecognizer.detect(frame);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                TextBlock myItem = items.valueAt(i);
                builder.append(myItem.getValue());
                builder.append("\n");
            }
            ocrResult.setText(builder.toString());
        }
    }
}