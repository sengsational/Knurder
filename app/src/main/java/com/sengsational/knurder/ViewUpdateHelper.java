package com.sengsational.knurder;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import static com.sengsational.knurder.KnurderApplication.getContext;


/**
 * Created by Owner on 12/2/2016.
 */

public class ViewUpdateHelper {
    private static final String TAG =ViewUpdateHelper.class.getSimpleName();

    public static void setHighlightIconInView(ImageView viewToManage, String highlightState, Context mContext) {
        if(highlightState == null) highlightState = "F";
        switch (highlightState) {
            case "F":
                ViewUpdateHelper.setImage(viewToManage, R.drawable.ufo_logo1, mContext);
                viewToManage.setVisibility(View.GONE);
                break;
            case "T":
                ViewUpdateHelper.setImage(viewToManage, R.drawable.ufo_logo1, mContext);
                viewToManage.setVisibility(View.VISIBLE);
                break;
            case "X":
                ViewUpdateHelper.setImage(viewToManage, R.drawable.ic_not_interested, mContext);
                viewToManage.setVisibility(View.VISIBLE);
                break;
        }
    }

    public static void setGlassShapeIconInView(ImageView viewToManage, String glassSize, Context mContext) {
        if (viewToManage == null) return;
        if(glassSize == null) glassSize = "0";
        switch (glassSize) {
            case "16":
                ViewUpdateHelper.setImage(viewToManage, R.mipmap.pint_glass, mContext);
                viewToManage.setVisibility(View.VISIBLE);
                break;
            case "13":
                ViewUpdateHelper.setImage(viewToManage, R.mipmap.snifter_glass, mContext);
                viewToManage.setVisibility(View.VISIBLE);
                break;
            case "11.5":
                ViewUpdateHelper.setImage(viewToManage, R.mipmap.snifter_glass, mContext);
                viewToManage.setVisibility(View.VISIBLE);
                break;
            case "10":
                ViewUpdateHelper.setImage(viewToManage, R.mipmap.wine_glass, mContext);
                viewToManage.setVisibility(View.VISIBLE);
                break;
            case "9":
                ViewUpdateHelper.setImage(viewToManage, R.mipmap.wine_glass, mContext);
                viewToManage.setVisibility(View.VISIBLE);
                break;
            case "0":
                ViewUpdateHelper.setImage(viewToManage, R.mipmap.pint_glass, mContext);
                viewToManage.setVisibility(View.GONE);
                break;
            default:
                ViewUpdateHelper.setImage(viewToManage, R.mipmap.pint_glass, mContext);
                viewToManage.setVisibility(View.GONE);
        }
    }
    public static void setGlassPriceInView(TextView viewToManage, String glassPrice, Context mContext) {
        if (viewToManage == null) return;
        if(glassPrice == null || "".equals(glassPrice)) {
            viewToManage.setVisibility(View.INVISIBLE);
        } else {
            viewToManage.setText(mContext.getResources().getString(R.string.dollars_prefix, glassPrice));
            viewToManage.setVisibility(View.VISIBLE);
        }
    }

    public static void updateViewHighlightState(ImageView viewToManage, SaucerItem modelItem, boolean skipGagState, Context context) {
        String highlightState = modelItem.getHighlighted();
        if (highlightState == null) highlightState = "F";
        switch (highlightState) {
            case "F":
                ViewUpdateHelper.setImage(viewToManage, R.drawable.ufo_logo1, context);
                viewToManage.setVisibility(View.VISIBLE);
                modelItem.setHighlighted("T");
                break;
            case "T":
                ViewUpdateHelper.setImage(viewToManage, R.drawable.ic_not_interested, context);
                viewToManage.setVisibility(View.VISIBLE);
                modelItem.setHighlighted("X");
                if (skipGagState) updateViewHighlightState(viewToManage, modelItem, false, context); // This is for when the user is in the flagged beer list, they want to go directly to unflagged.
                break;
            case "X":
                ViewUpdateHelper.setImage(viewToManage, R.drawable.ufo_logo1, context);
                viewToManage.setVisibility(View.GONE);
                modelItem.setHighlighted("F");
                break;
        }

    }

    static void setNewArrivalStyleInView(TextView viewToManage, String newArrivalState, Context context) {
        if(newArrivalState == null) newArrivalState = "F";
        switch (newArrivalState) {
            case "F":
                viewToManage.setTypeface(null, Typeface.NORMAL);
                viewToManage.setVisibility(View.GONE);
                break;
            case "T":
                viewToManage.setTypeface(null, Typeface.BOLD);
                viewToManage.setTextColor(ContextCompat.getColor(context, R.color.colorActivePrimary));
                viewToManage.setText("New Arrival");
                viewToManage.setVisibility(View.VISIBLE);
                break;
        }
    }


    public static void setImage(ImageView viewToManage, int imageId, Context context) {
        Drawable drawable = null;
        if (context == null) {
            drawable = ContextCompat.getDrawable(getContext(), imageId);
        } else {
            drawable = ContextCompat.getDrawable(context, imageId);
        }
        viewToManage.setImageDrawable(drawable);
    }

    public static void setActiveStyleInView(TextView viewById, String active, Context context) {
        if(!"T".equals(active)){
            //Log.v(TAG, "setting non-active color");
            viewById.setTextColor(ContextCompat.getColor(context, R.color.colorNonActiveTertiary));
        } else {
            //Log.v(TAG, "setting ACTIVE color");
            viewById.setTextColor(ContextCompat.getColor(context, R.color.colorActivePrimary));
        }
    }

    public static void setNameTextStyle(String fontDeterminer, TextView tv) {
        switch (fontDeterminer) {
            case "TT":
                tv.setTypeface(null, Typeface.BOLD_ITALIC);
                break;
            case "TF":
                tv.setTypeface(null, Typeface.BOLD);
                break;
            case "FT":
                tv.setTypeface(null, Typeface.ITALIC);
                break;
            case "FF":
                tv.setTypeface(null, Typeface.NORMAL);
                break;
            default:
                tv.setTypeface(null, Typeface.NORMAL);
        }

    }
}
