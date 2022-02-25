package com.sengsational.knurder;

import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import static com.sengsational.knurder.BeerSlideActivity.EXTRA_POSITION;
import static com.sengsational.knurder.KnurderApplication.getContext;

public class RateBeerActivity extends AppCompatActivity {
    private static final String TAG = RateBeerActivity.class.getSimpleName();

    RatingBar mRatingBar;
    Button mSaveButton;
    Button mCancelButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rate_beer);
        int position = 0;
        try { position = (Integer) getIntent().getExtras().get(EXTRA_POSITION); } catch (Throwable t) {Log.e(TAG, "SERIOUS ERROR: unable to get " + EXTRA_POSITION);}
        if (KnurderApplication.getCursor(getApplicationContext()) != null) {
            Cursor cursor = KnurderApplication.getCursor(getApplicationContext());
            cursor.moveToPosition(position);
            final SaucerItem modelItem = new SaucerItem(cursor);
            // Set the non-dynamic values in the associated layout
            ((TextView)findViewById(R.id.database_key)).setText(modelItem.getIdString());
            ((TextView)findViewById(R.id.beername)).setText(modelItem.getName());
            ((TextView)findViewById(R.id.description)).setText(modelItem.getDescription());

            // Ratings Text
            final TextView ratingTextView = findViewById(R.id.ratingEditText);
            String existingText = modelItem.getUserReview();
            if (existingText != null && !"null".equals(existingText)) {
                ratingTextView.setText(existingText);
                if ("W".equals(modelItem.getReviewFlag())) {
                    ratingTextView.setTextColor(Color.GRAY);
                }
            }

            // Ratings Bar for stars
            mRatingBar = findViewById(R.id.ratingBar);
            String starCountString = modelItem.getUserStars();
            if (starCountString != null) {
                int starCount = -1;
                Log.v(TAG, "starCountString " + starCountString);
                try { starCount = Integer.parseInt(starCountString); } catch (Throwable t) {/*ignore*/ }
                if (starCount > 0) {
                    mRatingBar.setRating(starCount);
                }
            }
            mRatingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
                @Override
                public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
                    Log.v(TAG, "float " + v);
                    mRatingBar.setRating(v);
                    Log.v(TAG, "rating changed " + ratingBar.getRating() + " v:" + v + " b:"  + b);

                }
            });
            ratingTextView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if ("W".equals(modelItem.getReviewFlag())) {
                        Toast.makeText(getContext(), "This review is already on the Saucer web site, so it can't be changed.", Toast.LENGTH_LONG).show();
                    }
                }
            });

            // Save Button
            mSaveButton = findViewById(R.id.save_rating_button);
            mSaveButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    float ratingFloat = mRatingBar.getRating();
                    String ratingText = ratingTextView.getText().toString();
                    Log.v(TAG, "save button clicked with " + ratingFloat + " and [" + ratingText + "]");
                    // FOR TESTING... Allow me to clear out something downloaded earlier
                    if (ratingText.contains("CLEARCLEAR")) {
                        modelItem.setUserReview(""); // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< TEMP CLEAR OLD DOWNLOAD
                        modelItem.setUserStars(""); // <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< TEMP CLEAR OLD DOWNLOAD
                        modelItem.setReviewFlag("");
                    } else {
                        modelItem.setUserReview(ratingText);
                        modelItem.setUserStars("" + (int)ratingFloat);
                        modelItem.setReviewFlag("L");
                    }
                    UfoDatabaseAdapter.update(modelItem, getContext());
                    KnurderApplication.reQuery(getContext());
                    onBackPressed();
                }
            });

            // Cancel Button
            mCancelButton = findViewById(R.id.cancel_rating_button);
            mCancelButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    onBackPressed();
                }
            });

            String reviewFlag = modelItem.getReviewFlag();
            if ("W".equals(reviewFlag)) {
                // No editing allowed
                ratingTextView.setFocusable(false);
                mSaveButton.setVisibility(View.GONE);
                mRatingBar.setIsIndicator(true);
                findViewById(R.id.description).setVisibility(View.GONE);
                getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
            } else {
                ratingTextView.setFocusable(true);
                mSaveButton.setVisibility(View.VISIBLE);
                mRatingBar.setIsIndicator(false);
                findViewById(R.id.description).setVisibility(View.VISIBLE);
            }



        }
    }
}
