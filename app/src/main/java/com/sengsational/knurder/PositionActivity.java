package com.sengsational.knurder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.sengsational.almeros.MoveGestureDetector;
import com.sengsational.almeros.RotateGestureDetector;
import com.sengsational.almeros.ShoveGestureDetector;
import com.sengsational.chiralcode.ColorPickerDialog;
import com.sengsational.sephiroth.UILongPressGestureRecognizer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import static com.sengsational.knurder.BeerSlideActivity.EXTRA_FILENAME;
import static com.sengsational.knurder.BeerSlideActivity.EXTRA_GLASS_TYPE;
import static com.sengsational.knurder.BeerSlideActivity.EXTRA_POSITION;
import static com.sengsational.knurder.BeerSlideActivity.EXTRA_TUTORIAL_TYPE;
import static com.sengsational.knurder.BeerSlideActivity.PINT_GLASS;
import static com.sengsational.knurder.BeerSlideActivity.SNIFTER_GLASS;
import static com.sengsational.knurder.PopTutorial.EXTRA_TEXT_RESOURCE;
import static com.sengsational.knurder.PopTutorial.EXTRA_TITLE_RESOURCE;

public class PositionActivity extends AppCompatActivity implements OnTouchListener {
	private static final String TAG = PositionActivity.class.getSimpleName();
    private static final String OVERLAY_COLOR = "overlayColor";
    public static final int RESULT_FAILED = -42;
    private static SharedPreferences prefs;
    public static final String PREF_POSITION_TUTORIAL = "positionTutorial";

	private Matrix mMatrix = new Matrix();
    private float mScaleFactor = .8f;
    private float mRotationDegrees = 0.f;
    private float mFocusX = 0.f;
    private float mFocusY = 0.f;  
    private int mAlpha = 255;
    private int mImageHeight, mImageWidth;

    private ScaleGestureDetector mScaleDetector;
    private RotateGestureDetector mRotateDetector;
    private MoveGestureDetector mMoveDetector;
    private ShoveGestureDetector mShoveDetector;
    //private GestureDetector mLongPressDetector;
    private UILongPressGestureRecognizer mLongPressDetector; // DRS 20170408

    private ImageView mImageViewToMove;

	private RelativeLayout mBeerLayout;
    private Bitmap mBeerLayoutTextMap;
    private float mCurveFactor = 0.151515f;
    float mTopBottomRatio = 0.5f;
    int mTransformStepCount = 48; // TODO: Set back to 48
    String mFileName = "";
    int mPosition = 0;
    private Context mLocalContext;
    private PositionActivity positionActivity;
    private int mGlassType;
    private float mSquashFactor = 1.0f;
    float[] mSnifterRelativeSizes = {.766f,  0.836f,     0.906f,     0.953f,     1f,         0.953f,     0.859f,     0.687f};

    public PositionActivity() {
        positionActivity = this;
    }

    @SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
        Log.v(TAG, "PositionActivity.onCreate running.");
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_position_beer);
        mLocalContext = getApplicationContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        try { mGlassType = (int) getIntent().getExtras().get(EXTRA_GLASS_TYPE); } catch (Throwable t) {Log.v(TAG, "Could not find glass type on intent.");}
        switch (mGlassType) {
            case SNIFTER_GLASS:
                mCurveFactor = 0.4f;
                break;
            case PINT_GLASS:
                mCurveFactor = 0.15f;
                break;
        }

        String errorMessage = "";
            /* Get the size of the display */
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int targetW = size.x;
        int targetH = size.y;


        try {
            errorMessage = "Oops!!  The picture was unavailable!!";
            if (savedInstanceState != null) {
                mFileName = savedInstanceState.getString("mFileName");
                Log.v(TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>> gettting savedInstance state had mFileName: " + mFileName);
            }
            if (mFileName == null || "".equals(mFileName)) {
                mFileName = (String) getIntent().getExtras().get("fileName");
                Log.v(TAG, "the extras had fileName: " + mFileName);
            }
            if (mFileName == null) {
                Log.v(TAG, "mFileName was STILL null!");
                mFileName = FileHelper.getFileNameStringForTempPhotoFile(false);
            } else if (mFileName.length() == 0) {
                errorMessage = "Oops!! Can't access the picture on external storage!!";
                throw new Exception (errorMessage);
            } else {
                errorMessage = "Filename was fine, but something else bad happened.";
            }
            Log.v(TAG, "mFileName was " + mFileName);
            View backgroundLayout = findViewById(R.id.background_layout);

            Log.v(TAG, "**DRS** The display was " + targetW + " by " + targetH + " ratio: " + ((float)targetW)/((float)targetH));

		    /* Get the size of the image */
            BitmapFactory.Options bmOptions =new BitmapFactory.Options();
            bmOptions.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(mFileName, bmOptions);

            float imageWidth = (float)bmOptions.outWidth;
            float imageHeight = (float)bmOptions.outHeight;

            Log.v(TAG, "**DRS** The image was " + imageWidth + " by " + imageHeight + " ratio: " + imageWidth/imageHeight);

            if (((targetW < targetH) && (imageWidth > imageHeight)) || ((targetW > targetH) && (imageWidth < imageHeight))) {
                // need to swap image sizes because the camera reported the swapped width vs height
                float savedWidth = imageWidth;
                imageWidth = imageHeight;
                imageHeight = savedWidth;
                Log.v(TAG, "**DRS** SWAPPED IMAGE " + imageWidth + " by " + imageHeight + " ratio: " + imageWidth/imageHeight);
            }

            /* Match the image size with the screen */
            float widthRatio = ((float)targetW) / imageWidth;  // maybe: 1080 / 3024 = 0.3751
            float heightRatio = ((float)targetH) / imageHeight; // maybe 1776 / 4032 = 0.4405
            Log.v(TAG, "widthRatio: "  + widthRatio + " heightRatio: " + heightRatio);
            if (Math.abs(heightRatio - widthRatio) < 0.01) {
                // do nothing if it's equal or close to it
            } else if (heightRatio > widthRatio) {
                // reduce height
                Log.v(TAG, "old TargetH: " + targetH);
                targetH = (int)(((float)targetW) * (imageHeight) / (imageWidth)); // 1080 * 4032 / 3024 =  1440
                Log.v(TAG, "new TargetH: " + targetH);
            } else {
                // reduce width
                Log.v(TAG, "old TargetW: " + targetW);
                targetW = (int)(((float)targetH) * (imageWidth) / (imageHeight));
                Log.v(TAG, "new TargetW: " + targetW);
            }

            Log.v(TAG, "**DRS** The size to use was " + targetW + " by " + targetH + " ratio: " + ((float)targetW)/((float)targetH));

            Log.v(TAG, "**DRS** The image was " + imageWidth + " by " + imageHeight + " ratio: " + (imageWidth)/(imageHeight));

            /* Set the height and width to match the photograph */
            Object o = backgroundLayout.getLayoutParams();
            Log.v(TAG, "The type wsas " + o.getClass().getName());

            FrameLayout.LayoutParams backgroundLayoutLayoutParams = (FrameLayout.LayoutParams)backgroundLayout.getLayoutParams();
            backgroundLayoutLayoutParams.height = targetH;
            backgroundLayoutLayoutParams.width = targetW;

            /* Set bitmap options to scale the image decode target */
            bmOptions.inSampleSize = PhotoHelper.calculateInSampleSize(bmOptions, targetW, targetH);
            bmOptions.inPurgeable = true;
            bmOptions.inJustDecodeBounds = false;

            InputStream inputStream = mLocalContext.getContentResolver().openInputStream(Uri.fromFile(new File(mFileName)));
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, bmOptions);
            inputStream.close();
            bitmap = PhotoHelper.rotateImageIfRequired(bitmap, Uri.parse(mFileName));
            backgroundLayout.setBackgroundDrawable(new BitmapDrawable(bitmap));

            FloatingActionButton saveFab = (FloatingActionButton) findViewById(R.id.save_fab);
            saveFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    savePositionImage();
                }
            });

            FloatingActionButton ignoreFab = (FloatingActionButton) findViewById(R.id.ignore_fab);
            ignoreFab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    ignorePositionImage();
                }
            });
        } catch (Throwable t) {
            Toast.makeText(this, errorMessage + " " + t.getMessage(), Toast.LENGTH_LONG).show();
            Log.v(TAG, "Problem creating position activity: " + errorMessage + " " + t.getMessage());
            t.printStackTrace();
        }

		// Determine the center of the screen
		mFocusX = display.getWidth()/2f;
		mFocusY = display.getHeight()/2f;
		
		// Set this class as touchListener to the ImageView
		mImageViewToMove = (ImageView) findViewById(R.id.imageToMove);
		mImageViewToMove.setOnTouchListener(this);

        // Set the color of the KNURDER watermark
        ContextCompat.getDrawable(this, R.mipmap.knurder_label).setColorFilter(Color.DKGRAY, PorterDuff.Mode.SRC_ATOP);

        // Get the layout from the layout xml in order to make it available as a bitmap
        mBeerLayout = (RelativeLayout) findViewById(R.id.beerRelativeLayoutInvisible);
        boolean useSample = false;
        if (useSample) {
            // Use the sample data that's hard-coded in the layout file
        } else {
            // Use actual data from the database
            try { mPosition = (int) getIntent().getExtras().get(EXTRA_POSITION); } catch (Throwable t) {Log.v(TAG, "Could not find position on intent.");}
            Log.v(TAG, "positioning cursor " + mPosition);
            Cursor cursor = KnurderApplication.getCursor(getApplicationContext());
            cursor.moveToPosition(mPosition);
            final SaucerItem modelItem = new SaucerItem(KnurderApplication.getCursor(getApplicationContext()));
            Log.v(TAG, "brewer: " + modelItem.brewer);
            Log.v(TAG, "style: " + modelItem.style);
            //Log.v(TAG, "BeerSlideFragment.onCreateView() Cursor moving to ARG_POSITION position: " + mPosition + " and finding " + modelItem.getName());
            ((TextView)mBeerLayout.findViewById(R.id.database_key)).setText(modelItem.getIdString());
            ((TextView)mBeerLayout.findViewById(R.id.beername)).setText(modelItem.getName());
            ((TextView)mBeerLayout.findViewById(R.id.description)).setText(modelItem.getDescription());
            ((TextView)mBeerLayout.findViewById(R.id.abv)).setText(SaucerItem.getPctString(modelItem.getAbv()));
            ((TextView)mBeerLayout.findViewById(R.id.city)).setText(modelItem.getCity());
            ((TextView)mBeerLayout.findViewById(R.id.style)).setTypeface(null, Typeface.BOLD_ITALIC);
            ((TextView)mBeerLayout.findViewById(R.id.style)).setText(modelItem.getStyle());
            ((TextView)mBeerLayout.findViewById(R.id.brewery)).setTypeface(null, Typeface.BOLD_ITALIC);
            ((TextView)mBeerLayout.findViewById(R.id.brewery)).setText(modelItem.getCleanBrewer());
            String createdText = modelItem.getCreated();
            ((TextView)mBeerLayout.findViewById(R.id.created)).setText((createdText==null || "null".equals(createdText))?"":("Tasted " + createdText));

            ViewUpdateHelper.setNewArrivalStyleInView((TextView)mBeerLayout.findViewById(R.id.new_arrival), modelItem.getNewArrival(), mLocalContext);
            ViewUpdateHelper.setActiveStyleInView((TextView)mBeerLayout.findViewById(R.id.beername), modelItem.getActive(), mLocalContext);
            ViewUpdateHelper.setActiveStyleInView((TextView)mBeerLayout.findViewById(R.id.description), modelItem.getActive(), mLocalContext);

            // This will set bold, bold-italic, italic or normal font for the beer name in the beer detail page.
            String newArrivalState = modelItem.getNewArrival();
            String tastedState = modelItem.getTasted();
            String fontDeterminer = (newArrivalState!=null?newArrivalState:"F") + (tastedState!=null?tastedState:"F");
            ViewUpdateHelper.setNameTextStyle(fontDeterminer, (TextView)mBeerLayout.findViewById(R.id.beername));

            setOverlayColor(prefs.getInt(OVERLAY_COLOR, Color.YELLOW));

        }
        mBeerLayout.setDrawingCacheEnabled(true);
        mBeerLayout.buildDrawingCache();
        mBeerLayoutTextMap = null;
        final PositionActivity activity = this;
        final int fTargetW = targetW;
        final int fTargetH = targetH;

        @SuppressLint("WrongViewCast") final ViewTreeObserver viewTreeObserver = findViewById(R.id.beerRelativeLayoutInvisible).getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @SuppressLint("WrongViewCast")
            @Override
            public void onGlobalLayout() {
                //Log.v(TAG, "addOnGlobalLayoutListener running.");
                try {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        findViewById(R.id.beerRelativeLayoutInvisible).getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        findViewById(R.id.beerRelativeLayoutInvisible).getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }
                } catch (Throwable t) {
                    Log.v(TAG, "Build " + Build.VERSION.SDK_INT + " exception " + t.getMessage() + " Try to power through.");
                    Toast.makeText(activity, "If this isn't working, try keeping your device vertical the whole time.", Toast.LENGTH_LONG).show();
                }
                if (mBeerLayoutTextMap == null) {
                    mBeerLayout.setDrawingCacheEnabled(false);
                    mBeerLayout.setDrawingCacheEnabled(true);
                    mBeerLayout.buildDrawingCache();
                    mBeerLayoutTextMap = mBeerLayout.getDrawingCache();
                }

                Log.v(TAG, "before textMap width: " + mBeerLayoutTextMap.getWidth() + " height: " + mBeerLayoutTextMap.getHeight());

                if (Build.VERSION.SDK_INT > 18) {
                    Log.v(TAG, "setting target hight and width of text map");
                    mBeerLayoutTextMap.setHeight(fTargetH);
                    mBeerLayoutTextMap.setWidth(fTargetW);
                }
                Log.v(TAG, "after  textMap width: " + mBeerLayoutTextMap.getWidth() + " height: " + mBeerLayoutTextMap.getHeight());


                Log.v(TAG, ">>>>>>>>>>> mBeerLayoutTextMap: " + mBeerLayoutTextMap);
                Bitmap warpedBitmap = warpImage(mBeerLayoutTextMap, mGlassType);
                Drawable d  = new BitmapDrawable(getResources(), warpedBitmap);
                mImageViewToMove.setImageDrawable(d);
                mImageHeight = d.getIntrinsicHeight();
                mImageWidth = d.getIntrinsicWidth();
                //Log.v(TAG, "**DRS** mImageViewToMove: " + mImageWidth + " by " + mImageHeight);

                // View is scaled and translated by matrix, so scale and translate initially
                float scaledImageCenterX = (mImageWidth*mScaleFactor)/2;
                float scaledImageCenterY = (mImageHeight*mScaleFactor)/2;

                setGeometryFactors();
                mMatrix.postScale(mScaleFactor, mScaleFactor * mSquashFactor);
                mMatrix.postTranslate(mFocusX - scaledImageCenterX, mFocusY - scaledImageCenterY);
                mImageViewToMove.setImageMatrix(mMatrix);
                //Log.v(TAG, "**DRS** mImageViewToMove after initial scale: " + mImageViewToMove.getWidth() + " by " + mImageViewToMove.getHeight());

                // Setup Gesture Detectors
                mScaleDetector 	= new ScaleGestureDetector(getApplicationContext(), new ScaleListener()); //Android
                mRotateDetector = new RotateGestureDetector(getApplicationContext(), new RotateListener()); //almeros
                mMoveDetector 	= new MoveGestureDetector(getApplicationContext(), new MoveListener()); //almeros
                mShoveDetector 	= new ShoveGestureDetector(getApplicationContext(), new ShoveListener()); //almeros
                mLongPressDetector = new LongPressDetector(getApplicationContext());
            }
        });

        // Maybe show the help dialog
        boolean showTutorial =  PreferenceManager.getDefaultSharedPreferences(this).getBoolean(PREF_POSITION_TUTORIAL, true);
        if (savedInstanceState == null && showTutorial) {
            Intent popTutorialIntent = new Intent(PositionActivity.this, PopTutorial.class);
            popTutorialIntent.putExtra(EXTRA_TEXT_RESOURCE, R.string.position_instructions);
            popTutorialIntent.putExtra(EXTRA_TITLE_RESOURCE, R.string.position_title);
            popTutorialIntent.putExtra(EXTRA_TUTORIAL_TYPE, PREF_POSITION_TUTORIAL);
            startActivity(popTutorialIntent);
        }
	}

    @Override
    protected void onDestroy() {
        Log.v(TAG, ">>>>>>> onDestroy Running!");
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        Log.v(TAG, ">>>>>>> onPause Running!");
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Log.v(TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>   Saving file name " + mFileName);
        savedInstanceState.putString("mFileName", mFileName);
        super.onSaveInstanceState(savedInstanceState);
    }

    private void setOverlayColor(int color) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(OVERLAY_COLOR, color);
        editor.apply();

        Drawable knurderDrawable = ContextCompat.getDrawable(positionActivity, R.mipmap.knurder_label);
        knurderDrawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
        ((ImageView)mBeerLayout.findViewById(R.id.knurder_label)).setImageDrawable(knurderDrawable);
        ((TextView)mBeerLayout.findViewById(R.id.beername)).setTextColor(color);
        ((TextView)mBeerLayout.findViewById(R.id.description)).setTextColor(color);
        ((TextView)mBeerLayout.findViewById(R.id.abv)).setTextColor(color);
        ((TextView)mBeerLayout.findViewById(R.id.city)).setTextColor(color);
        ((TextView)mBeerLayout.findViewById(R.id.style)).setTextColor(color);
        ((TextView)mBeerLayout.findViewById(R.id.brewery)).setTextColor(color);
        ((TextView)mBeerLayout.findViewById(R.id.created)).setTextColor(color);
        ((TextView)mBeerLayout.findViewById(R.id.new_arrival)).setTextColor(color);

    }

    @Override
    public void onBackPressed() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Save Or Not");
        builder.setMessage("Do you want to save this? ");
        builder.setPositiveButton("Save / Share", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                savePositionImage();
                /*
                FrameLayout backgroundLayout = (FrameLayout) findViewById(R.id.background_layout);
                backgroundLayout.buildDrawingCache();
                Bitmap backgroundBitmap = backgroundLayout.getDrawingCache();
                Log.v("20170328", "backgroundBitmap size: " + backgroundBitmap.getWidth() + " by " + backgroundBitmap.getHeight());
                String fileName = saveResult(backgroundBitmap);
                if ("".equals(fileName)) {
                    setResult(RESULT_FAILED);
                } else {
                    Intent returnFileIntent = new Intent();
                    returnFileIntent.putExtra(EXTRA_FILENAME, fileName);
                    setResult(RESULT_OK, returnFileIntent);
                }
                PositionActivity.super.onBackPressed();
                */
            }
        });
        builder.setNegativeButton("Discard", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                ignorePositionImage();
                /*
                Log.v(TAG, "No save");
                setResult(RESULT_CANCELED);
                PositionActivity.super.onBackPressed();
                */
            }
        });
        builder.show();
    }

    private void savePositionImage() {
        FrameLayout backgroundLayout = (FrameLayout) findViewById(R.id.background_layout);
        backgroundLayout.findViewById(R.id.save_fab).setVisibility(View.INVISIBLE);
        backgroundLayout.findViewById(R.id.ignore_fab).setVisibility(View.INVISIBLE);
        backgroundLayout.buildDrawingCache();
        Bitmap backgroundBitmap = backgroundLayout.getDrawingCache();
        Log.v("20170328", "backgroundBitmap size: " + backgroundBitmap.getWidth() + " by " + backgroundBitmap.getHeight());
        String fileName = saveResult(backgroundBitmap);
        if ("".equals(fileName)) {
            setResult(RESULT_FAILED);
        } else {
            Intent returnFileIntent = new Intent();
            returnFileIntent.putExtra(EXTRA_FILENAME, fileName);
            setResult(RESULT_OK, returnFileIntent);
        }
        backgroundLayout.findViewById(R.id.save_fab).setVisibility(View.VISIBLE);
        backgroundLayout.findViewById(R.id.ignore_fab).setVisibility(View.VISIBLE);
        PositionActivity.super.onBackPressed();
    }

    private void ignorePositionImage() {
        Log.v(TAG, "No save");
        setResult(RESULT_CANCELED);
        PositionActivity.super.onBackPressed();
    }

    private String saveResult(Bitmap bitmap) {
        //Log.v(TAG, "**DRS** bitmap to file system: " + bitmap.getWidth() + " by " + bitmap.getHeight());
        String fileName = "undefined";
        try {
            /*
            fileName = new FileHelper().getFileNameStringForTempPhotoFile();
            File aFile = new File(fileName);
            if (aFile.exists()) {
                Log.v(TAG, "File " + fileName + "  existed. Deleting it.");
                try {
                    aFile.delete();
                } catch (Throwable t) {
                    Log.v(TAG, "The file was not deleted: " + t.getMessage());
                }
            } else {
                Log.v(TAG, "File " + fileName + " did not exist.");
            }
            */
            fileName = FileHelper.getFileNameStringForTempPhotoFile(false);
            FileOutputStream out = new FileOutputStream(fileName);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
            Log.v(TAG, "Saved " + fileName);
        } catch (Exception e) {
            Log.v(TAG, "Failed in file output stream " + e.getMessage() + " " + fileName);
            return "";
        }

		/* Associate the Bitmap to the ImageView */
        //mImageView.setImageBitmap(combinedBitmap); // DRS was "bitmap"
        //mImageView.setVisibility(View.VISIBLE);

        /* Add as a gallery picture */
        Log.v(TAG, "galleryAddPic");
        Intent mediaScanIntent = new Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE");
        Uri contentUri = Uri.fromFile(new File(fileName));

        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
        return fileName;

    }

    float mTouchX = 0F;
    float mTouchY = 0F;

    // 20170329 - Added Handler and Runnable
    /*
    final Handler handler = new Handler();
    Runnable mLongPressed = new Runnable() {
        public void run() {
            Log.v(TAG, "onLongPress runnable triggered.  Now doing color picker");
            ColorPickerDialog colorPickerDialog = new ColorPickerDialog(positionActivity, prefs.getInt(OVERLAY_COLOR, Color.YELLOW), new ColorPickerDialog.OnColorSelectedListener() {

                @Override
                public void onColorSelected(int color) {
                    setOverlayColor(color);

                    mBeerLayout.setDrawingCacheEnabled(true);
                    mBeerLayout.buildDrawingCache();
                    mBeerLayoutTextMap = mBeerLayout.getDrawingCache();
                    mImageViewToMove.setImageMatrix(mMatrix);
                    mImageViewToMove.setImageDrawable(new BitmapDrawable(getResources(), warpImage(mBeerLayoutTextMap, mGlassType)));
                }

            });
            colorPickerDialog.show();
        }
    };
    */


    @SuppressWarnings("deprecation")
	public boolean onTouch(View v, MotionEvent event) {
        //Log.v(TAG, "View id " + v.getId() + " Motion event : " + event);
        mScaleDetector.onTouchEvent(event);
        mRotateDetector.onTouchEvent(event);
        mMoveDetector.onTouchEvent(event);
        mShoveDetector.onTouchEvent(event);
        // 20170327 - removed 1, added 'if' block
        //mLongPressDetector.onTouchEvent(event);
        //if(event.getAction() == MotionEvent.ACTION_DOWN) {
            // Start a timer on each ACTION_DOWN
        //    handler.postDelayed(mLongPressed, 1000);
        //}

        mLongPressDetector.onTouchEvent(event);

        float scaledImageCenterX = (mImageWidth*mScaleFactor)/2;
        float scaledImageCenterY = (mImageHeight*mScaleFactor)/2;
        
        mMatrix.reset();
        setGeometryFactors();

        mMatrix.postScale(mScaleFactor, mScaleFactor * mSquashFactor);
        mMatrix.postRotate(mRotationDegrees,  scaledImageCenterX, scaledImageCenterY);
        mMatrix.postTranslate(mFocusX - scaledImageCenterX, mFocusY - scaledImageCenterY);
        
		ImageView view = (ImageView) v;
		view.setImageMatrix(mMatrix);
		view.setAlpha(mAlpha);
        //Log.v(TAG, "ImageView updated by matrix " + view.getId() + " "  + view.getClass().getName());

		return true; // indicate event was handled
	}

    private void setGeometryFactors() {
        if (mGlassType == SNIFTER_GLASS) {
            mSquashFactor = 0.5f;
            if (mCurveFactor == 0.151515f) mCurveFactor = 0.4f; // if default, set it, otherwise don't
        } else { // PINT_GLASS
            mSquashFactor = 1.0f;
            if (mCurveFactor == 0.151515f) mCurveFactor = 0.15f; // if default, set it, otherwise don't
        }
    }

    private class LongPressDetector extends UILongPressGestureRecognizer {

        public LongPressDetector(Context context){
            super(context);
        }

        @Override
        public void handleLongPress() {

            removeMessages(MESSAGE_FAILED);

            if (getState() == State.Possible && mStarted) {
                Log.v(TAG, "onLongPress runnable triggered.  Now doing color picker");
                ColorPickerDialog colorPickerDialog = new ColorPickerDialog(positionActivity, prefs.getInt(OVERLAY_COLOR, Color.YELLOW), new ColorPickerDialog.OnColorSelectedListener() {

                    @Override
                    public void onColorSelected(int color) {
                        setOverlayColor(color);

                        mBeerLayout.setDrawingCacheEnabled(true);
                        mBeerLayout.buildDrawingCache();
                        mBeerLayoutTextMap = mBeerLayout.getDrawingCache();
                        mImageViewToMove.setImageMatrix(mMatrix);
                        mImageViewToMove.setImageDrawable(new BitmapDrawable(getResources(), warpImage(mBeerLayoutTextMap, mGlassType)));
                    }

                });
                colorPickerDialog.show();

            }
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(ScaleGestureDetector detector) {
            //Log.v(TAG, "onScale <<<<<<<<<<<<<<<<< ");
			mScaleFactor *= detector.getScaleFactor(); // scale change since previous event
            //handler.removeCallbacks(mLongPressed);
            //Log.v(TAG, "onScale removed");

            // Don't let the object get too small or too large.
			mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 10.0f)); 

			return true;
		}
	}
	
	private class RotateListener extends RotateGestureDetector.SimpleOnRotateGestureListener {
		@Override
		public boolean onRotate(RotateGestureDetector detector) {
            //Log.v(TAG, "onRotate <<<<<<<<<<<<<<<<< ");
			mRotationDegrees -= detector.getRotationDegreesDelta();
            //handler.removeCallbacks(mLongPressed);
            //Log.v(TAG, "onRotate removed");

            return true;
		}
	}	
	
	private class MoveListener extends MoveGestureDetector.SimpleOnMoveGestureListener {
		@Override
		public boolean onMove(MoveGestureDetector detector) {
            //Log.v(TAG, "onMove <<<<<<<<<<<<<<<<< ");
			PointF d = detector.getFocusDelta();
			mFocusX += d.x;
			mFocusY += d.y;
            /*
            if ((Math.abs(d.x) + Math.abs(d.y)) > 8f) {
                handler.removeCallbacks(mLongPressed);
                //Log.v(TAG, "onMove removed " + d.x + ", " + d.y);
            }
            */
            // mFocusX = detector.getFocusX();
			// mFocusY = detector.getFocusY();
			return true;
		}
	}		
	
	private class ShoveListener extends ShoveGestureDetector.SimpleOnShoveGestureListener {
		@Override
		public boolean onShove(ShoveGestureDetector detector) {
            //Log.v(TAG, "onShove <<<<<<<<<<<<<<<<< ");
            float delta  = detector.getShovePixelsDelta();
            //handler.removeCallbacks(mLongPressed);
            Log.v(TAG, "onShove removed");
            Log.v(TAG, "delta: " + delta);
            if (delta > 0) {
                mCurveFactor -= 0.025f;
            } else {
                mCurveFactor += 0.025f;
            }

            //Log.v(TAG, "mCurveFactor " + mCurveFactor);
            if (mCurveFactor > 2.0f) {
                mCurveFactor = 2.0f;
            } else if (mCurveFactor < 0.0f) {
                mCurveFactor = 0.0f;
            }

            Log.v(TAG, "curve:" + mCurveFactor);
            mImageViewToMove.setImageDrawable(new BitmapDrawable(getResources(), warpImage(mBeerLayoutTextMap, mGlassType)));

            /*Log.v(TAG, "onShove <<<<<<<<<<<<<<<<< ");
			mAlpha += detector.getShovePixelsDelta();
			if (mAlpha > 255)
				mAlpha = 255;
			else if (mAlpha < 0)
				mAlpha = 0;
			*/
			return true;
		}
	}

    private Bitmap warpImage(Bitmap beerLayoutTextMap, int glassType) {
        int columns = 0;
        int rows = 0;
        float[] scaledVertices = null;

        if (beerLayoutTextMap == null) {
            Toast.makeText(this, "Please try keeping your device vertical the whole time.", Toast.LENGTH_LONG).show();
            Log.v(TAG, "beerLayoutTextMap was null in warpImage!!@!!");
            return null;
        } else {

            if (PINT_GLASS == glassType) {
                //Log.v(TAG, "CREATING scaledVerticies for shaker pint glass");
                Log.v(TAG, "beerLayoutTextMap: " + beerLayoutTextMap);
                Log.v(TAG, "stuff: " + mTransformStepCount + mCurveFactor + mTopBottomRatio);
                scaledVertices = computeVerticesFromEllipsePintGlass(beerLayoutTextMap.getWidth(), beerLayoutTextMap.getHeight(), mTransformStepCount, mCurveFactor, mTopBottomRatio);
                columns = (scaledVertices.length / (2 * 2)) - 1; // divide by 2 since for two points, divide by 2 again for two fence posts
                rows = 1;
            } else if (SNIFTER_GLASS == glassType) {
                //Log.v(TAG, "CREATING scaledVerticies for snifter glass");
                scaledVertices = computeVerticesFromEllipseSnifterGlass(beerLayoutTextMap.getWidth(), beerLayoutTextMap.getHeight(), mTransformStepCount, mCurveFactor, mTopBottomRatio, mSnifterRelativeSizes);
                columns = (scaledVertices.length / (2 * mSnifterRelativeSizes.length)) - 1; // divide by 2 since for two points, divide by 9 since we have nine fence posts
                rows = mSnifterRelativeSizes.length - 1; // fence sections
            }
            //DRS 20181123 - Height times curve factor keeps cutoff point from moving up as curve increases.  But still the large descriptions are cut off, regardless of the curve.
            Bitmap descriptionBitmap = Bitmap.createBitmap(beerLayoutTextMap.getWidth(), (int)(beerLayoutTextMap.getHeight() * (1 + mCurveFactor)), Bitmap.Config.ARGB_8888);
            Canvas descriptionCanvas = new Canvas(descriptionBitmap);
            descriptionCanvas.drawBitmapMesh(beerLayoutTextMap, columns, rows, scaledVertices, 0, null, 0, null);
            return descriptionBitmap;
        }
    }

    private float[] computeVerticesFromEllipseSnifterGlass(int width, int height, int steps, float curveFactor, float topBottomRatio, float[] ellipseRelativeSizes) {
        // Defined by the shape of the snifter.  It is the relative size of the slice at even intervals.  Arbitrarily select 8 slices.
        int slices = ellipseRelativeSizes.length;

        // this array holds the points that make up the geometry
        // each step is a column but we don't use the first or last column due to extreme geometry there
        // We may later add topBottomRatio to increase the curve of lower ellipses (simulating a top-down camera view): ellipsePosition[1] * topBottomRatio;
        int pointsPerSlice = steps - 1; // number of columns we use is steps - 2 (fence post vs fence rails)
        float[] vertices = new float[slices * pointsPerSlice * 2]; // 2: one for x, one for y
        Log.v(TAG, "We have room for " + vertices.length + " entries.");

        double[] ellipsePosition = new double[slices];
        double[] altWidth = new double[slices];
        double[] pArray = new double[slices];
        double[] aArray = new double[slices];
        double[] bArray = new double[slices];
        double[] qArray = new double[slices];
        double[] incrementArray = new double[slices];
        double[] shiftArray = new double[slices];
        double[] shiftDownArray = new double[slices];
        for (int size = 0; size < slices; size++) {
            ellipsePosition[size] = size/(double)(slices - 1);                                      // build array that starts at 0 and ends at 1, evenly spaced.
            pArray[size] = width / 2d * ellipseRelativeSizes[size];                                 // ellipse "p" variable
            aArray[size] = width / 2d;                                                              // ellipse "a" variable.  This might change if we start using top/bottom ratio
            bArray[size] = curveFactor * aArray[size];                                              // ellipse "b" variable
            altWidth[size] = width;                                                                 // this might change if we start using top/bottom ratio
            qArray[size] = (width - altWidth[size]) / 2d;                                           // ellipse "q" variable
            incrementArray[size] = altWidth[size] * ellipseRelativeSizes[size] / (double)steps;     // used to step through each ellipse point left to right. smaller ellipses increment less
            shiftArray[size] = altWidth[size] * (1d - ellipseRelativeSizes[size]) / 2d;             // used to center each ellipse under one and other (adjust horizontally)
            shiftDownArray[size] = height * ellipsePosition[size];                                  // used to position each ellipse vertically
        }

        int j = 0; // Global counter for float array
        Log.v(TAG, "Defined width, " + width + ", height, " + height + ", curveFactor, " + curveFactor);

        for (int size = 0; size < slices; size++) {
            //Log.v(TAG, "E" + size + " Parameters " + pArray[size] + "," +  qArray[size] + "," + aArray[size] + "," + bArray[size] + "," + incrementArray[size] + "," + shiftArray[size]);
            for (int i = 1; i < steps; i++, j=j+2) {
                vertices[j] = (float)(incrementArray[size] * (double)i) + (float)shiftArray[size];
                vertices[j+1] =(float) -(-Math.sqrt((1-Math.pow(((incrementArray[size] * (double)i)-pArray[size]), 2)/Math.pow(aArray[size],2)) * Math.pow(bArray[size],2)) + qArray[size])+ (float)shiftDownArray[size] ;
                //Log.v(TAG, "E" + size + " Point, " + vertices[j] + ", " + vertices[j+1] + ", " + j + ", " +(j+1));
            }
        }
        return vertices;
    }

    private static float[] computeVerticesFromEllipsePintGlass(int width, int height, int steps, float curveFactor, float topBottomRatio) {
        // this array holds the points that make up the geometry
        // each step is a column but we don't use the first or last column
        // the '4' is because each column has 2 points, and we have 2 ellipses
        float[] vertices = new float[(steps-1) * 4];

        // parameters for the top ellipse
        double p = width / 2d;
        double q = 0d;
        double a = width / 2d;
        double b = curveFactor * a;

        int j = 0;

        double increment = width / (double)steps;

        // Load the array with points for the top ellipse
        for (int i = 1; i < steps; i++, j=j+2) {
            vertices[j] = (float)(increment * (double)i);
            vertices[j+1] =-(float) (-Math.sqrt((1-Math.pow(((increment * (double)i)-p), 2)/Math.pow(a,2)) * Math.pow(b,2)) + q);
            //Log.v(TAG, "E0 Point, " + vertices[j] + ", " + vertices[j+1] + ", " + j + ", " +(j+1));
        }

        // parameters for the bottom ellipse
        double width2 = topBottomRatio * width;
        p = width2 / 2d;
        q = (width - width2) / 2d;
        a = width2 / 2d;
        b = curveFactor * a / topBottomRatio / topBottomRatio; // divided by this ratio twice just looked better (more curve at the bottom)
        increment = width2 / (double)steps;

        // The shift is so that this ellipse will be centered with the top ellipse
        double shift = width * (1d - topBottomRatio) / 2d;

        for (int i = 1; i < steps; i++, j=j+2) {
            vertices[j] = (float)(increment * (double)i) + (float)shift;
            vertices[j+1] =(float) -(-Math.sqrt((1-Math.pow(((increment * (double)i)-p), 2)/Math.pow(a,2)) * Math.pow(b,2)) + q)+ height; // note the height is what puts this ellipse lower
            //Log.v(TAG, "E1 Point, " + vertices[j] + ", " + vertices[j+1] + ", " + j + ", " +(j+1));
        }

        return vertices;
    }


}