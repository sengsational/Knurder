package com.sengsational.knurder;

import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PorterDuff;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

/**
 * Created by Owner on 11/14/2017.
 */


public class RecyclerOcrViewAdapter extends CursorRecyclerViewAdapter<RecyclerOcrViewAdapter.ViewHolder> {
    private static final String TAG = RecyclerOcrViewAdapter.class.getSimpleName();

    private Context mContext;
    private static final SimpleDateFormat NDF = new SimpleDateFormat("yyyy MM dd");
    private static java.text.DateFormat localDateFormat;
    private static DecimalFormat DF = new DecimalFormat("0.00");
    private static int colorSysGray;

    private static ArrayList<String> PRICES_LIST;
    private static ArrayList<String> SIZES_LIST;

    private LayoutInflater mLayoutInflaterGlassEdit;
    private PopupWindow mPopupWindowGlassEdit;

    private String mUpdatedPrice;
    private String mUpdatedGlassSize;

    public RecyclerOcrViewAdapter(Context context, Cursor cursor) {
        super(cursor, false);
        mContext = context;

        // Populate static lists (one time)
        if (PRICES_LIST == null) {
            PRICES_LIST = new ArrayList<>();
            int maxPrice = 16;
            int minPrice = 3;
            for (int i = minPrice; i < maxPrice; i++){
                PRICES_LIST.add(DF.format(i));
                PRICES_LIST.add(DF.format(i + 0.25));
                PRICES_LIST.add(DF.format(i + 0.5));
                PRICES_LIST.add(DF.format(i + 0.75));
            }
        }
        if (SIZES_LIST == null) {
            SIZES_LIST = new ArrayList<>();
            SIZES_LIST.add("16");
            SIZES_LIST.add("13");
            SIZES_LIST.add("10");
        }
        colorSysGray = ContextCompat.getColor(context, R.color.colorSysGray);
        localDateFormat = DateFormat.getDateFormat(context);
    }

    @Override
    public RecyclerOcrViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //Log.v(TAG, ">>>>>>>>>>>>onCreateViewHolder<<<<<<<<<<<<<");
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View modelView = inflater.inflate(R.layout.b_item, parent, false);
        return new ViewHolder(modelView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, Cursor aCursor) {
        //Log.v(TAG, ">>>>>>>>>>>>onBindViewHolder<<<<<<<<<<<<<");
        /*
                String[] pullFields = new String[]{"UFO._id", "UFO.NAME", "UFOLOCAL.GLASS_SIZE", "UFOLOCAL.LAST_UPDATED_DATE", "UFOLOCAL.ADDED_NOW_FLAG", "UFOLOCAL.GLASS_PRICE, "UFOLOCAL._id"};
                                                                  1               2                        3                           4                         5                 6
         */
        final int ufoDatabaseId = aCursor.getInt(0);
        final String beerName = aCursor.getString(1);
        final String glassSize = aCursor.getString(2);
        final String lastUpdated = aCursor.getString(3);
        final String glassPrice = aCursor.getString(5);
        final int databaseId = aCursor.getInt(6);
        Log.v(TAG, "databaseId " + databaseId + " for beer " + beerName);


        viewHolder.tvFirst.setText(beerName);
        viewHolder.tvSecond.setText(buildDataLine(glassPrice, glassSize, lastUpdated));

        String fontDeterminer = "FF";
        ViewUpdateHelper.setNameTextStyle(fontDeterminer, viewHolder.tvFirst);

        viewHolder.tvFirst.setTextColor(ContextCompat.getColor(mContext, R.color.colorActivePrimary));
        viewHolder.tvSecond.setTextColor(ContextCompat.getColor(mContext, R.color.colorNonActiveSecondary));

        ViewUpdateHelper.setGlassShapeIconInView(viewHolder.imageViewToManage, viewHolder.textViewToManage, glassSize, "draught", mContext);

        viewHolder.tvDatabaseKey.setText(databaseId+""); // This is the key from UFOLOCAL table (not UFO)
        viewHolder.tvUfoDatabaseKey.setText(ufoDatabaseId+""); // This is from the UFO table

        // If the item is clicked, we want to edit the item in a popup window
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Not sure if mContext will be null ever, but I'll wait for the crash reports
                mLayoutInflaterGlassEdit = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                ViewGroup container = (ViewGroup) mLayoutInflaterGlassEdit.inflate(R.layout.activity_edit_glass, null);
                final int layoutPosition = viewHolder.getLayoutPosition();

                // Set size of pop up window and show it
                int height = (int)(view.getWidth() * 0.8);
                int width = (int)(view.getWidth() * 0.8);
                try {
                    height = (int)(((RecyclerView)view.getParent()).getHeight() * 0.8);
                    width = (int)(((RecyclerView)view.getParent()).getWidth() * 0.8);
                } catch (Throwable t) { /*live with it*/ }
                mPopupWindowGlassEdit = new PopupWindow(container,width,height, true);
                mPopupWindowGlassEdit.showAtLocation(container, Gravity.CENTER, 0,0 );

                // Clear the edit fields
                mUpdatedGlassSize = "";
                mUpdatedPrice = "";

                // Beer database id
                Log.v(TAG, "database id of clicked item " + viewHolder.tvDatabaseKey.getText());

                // Beer Name
                final TextView beerNameTextView = container.findViewById(R.id.beername_glass_edit);
                beerNameTextView.setText(beerName);
                beerNameTextView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Log.v(TAG, "beername_class_edit was clicked " + beerNameTextView);
                    }
                });

                // Glasses Icons - glassesArray must align with SIZES_LIST
                final ImageView[] glassesArray = {container.findViewById(R.id.pint_glass_icon),container.findViewById(R.id.snifter_glass_icon),container.findViewById(R.id.wine_glass_icon)};
                int glassLoc = SIZES_LIST.indexOf(glassSize);
                manageIcons(glassLoc, glassesArray); // if glassSize is blank, then we don't highlight any glass
                glassesArray[0].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        manageIcons(0, glassesArray);
                    }
                });
                glassesArray[1].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        manageIcons(1, glassesArray);
                    }
                });
                glassesArray[2].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        manageIcons(2, glassesArray);
                    }
                });

                // Price
                NumberPicker numberPicker = container.findViewById(R.id.glass_price_picker);
                numberPicker.setDisplayedValues(PRICES_LIST.toArray(new String[PRICES_LIST.size()]));
                numberPicker.setMinValue(0);
                numberPicker.setMaxValue(PRICES_LIST.size() - 1);
                if (PRICES_LIST.contains(glassPrice)){
                    int priceIndex = PRICES_LIST.indexOf(glassPrice);
                    numberPicker.setValue(priceIndex);
                    mUpdatedPrice = PRICES_LIST.get(priceIndex);
                } else {
                    Log.v(TAG, "glassPrice " + glassPrice + " not found in the prices array");
                    numberPicker.setValue(Math.min((5 - 3)*4 + 2,PRICES_LIST.size()));   // How's THAT for random crap???  Actually, that's $5.50, but prevented from blowing up if over max
                    mUpdatedPrice = PRICES_LIST.get(numberPicker.getValue());
                }
                numberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                    @Override
                    public void onValueChange(NumberPicker numberPicker, int i, int updatedPriceIndex) {
                        //Log.v(TAG, "The value has changed old:" + i + " new:" + i1);
                        mUpdatedPrice = PRICES_LIST.get(updatedPriceIndex);
                    }
                });

                // >>>>>>>>>> OK <<<<<<<<<<<<<<<<<
                final TextView okText = container.findViewById(R.id.glass_edit_ok);
                okText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

                        /** SET UPDATED FIELDS IN THE VIEW */
                        viewHolder.tvSecond.setText(buildDataLine(mUpdatedPrice, mUpdatedGlassSize, lastUpdated));
                        ViewUpdateHelper.setGlassShapeIconInView(viewHolder.imageViewToManage, viewHolder.textViewToManage, mUpdatedGlassSize, "draught", mContext);

                        /** UPDATE THE DATABASE  or INSERT IF DOESN'T EXIST */
                        UfoDatabaseAdapter ufoDatabaseAdapter = new UfoDatabaseAdapter(mContext) ;
                        SQLiteDatabase db = ufoDatabaseAdapter.openDb(mContext);  //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<OPENING DATABASE
                        String databaseKey = viewHolder.tvDatabaseKey.getText().toString();
                        if (!"0".equals(databaseKey)) { // Already in the UFOLOCAL table
                            db.execSQL("update UFOLOCAL set GLASS_SIZE='" + mUpdatedGlassSize + "', GLASS_PRICE='" + mUpdatedPrice + "' where _id = " +  databaseKey);
                        } else {
                            // Need to look-up the beer in the main UFO table then insert it into the UFOLOCAL table
                            String[] pullFields = new String[]{SaucerItem.NAME, SaucerItem.BREWER, SaucerItem.ACTIVE, SaucerItem.CONTAINER, SaucerItem.STORE_ID, SaucerItem.BREW_ID, SaucerItem.ABV};
                            Calendar cal = Calendar.getInstance();
                            String lastUpdated = NDF.format(cal.getTime());
                            String selectionFields = "_id=?";
                            String[] selectionArgs = new String[]{viewHolder.tvUfoDatabaseKey.getText().toString()};

                            Cursor mainTableCursor = ufoDatabaseAdapter.query(       "UFO",          pullFields,        selectionFields,          selectionArgs);
                            if (mainTableCursor != null) {
                                for (mainTableCursor.moveToFirst(); !mainTableCursor.isAfterLast(); mainTableCursor.moveToNext()) {
                                    String beerName = mainTableCursor.getString(0);
                                    String brewer = mainTableCursor.getString(1);
                                    String store_id = mainTableCursor.getString(4);
                                    String brew_id = mainTableCursor.getString(5);
                                    String abv = mainTableCursor.getString(6);
                                    Log.v(TAG, "For input into other table: [" + beerName + ", " + mUpdatedGlassSize + ", " + brewer + ", " + store_id + ", " + brew_id + ", " + abv + ", " + " ]");

                                    // Populate the UFOLOCAL table - up until now, it's been in mFoundResults<String[]>
                                    ContentValues values = new ContentValues();
                                    values.put("NAME", beerName);
                                    values.put("STORE_ID", store_id);
                                    values.put("BREW_ID", brew_id);
                                    values.put("GLASS_SIZE", mUpdatedGlassSize);
                                    values.put("GLASS_PRICE", mUpdatedPrice);
                                    values.put("LAST_UPDATED_DATE", lastUpdated);
                                    values.put("ADDED_NOW_FLAG", "Y");
                                    values.put("ABV", abv); // DRS 20220726

                                    db.insert("UFOLOCAL", null, values);
                                    Log.v(TAG, "Inserted record into UFOLOCAL");
                                } // end for each found menu item
                                mainTableCursor.close();
                            } else {
                                Log.e(TAG, "NO DATA FROM THE DATABASE - null cursor");
                            }
                        }
                        // Copy from the UFOLOCAL table to the UFO table, updating tap beers with whatever glass size and prices were found.
                        Log.v(TAG, "copy UFOLOCAL data to UFO");
                        UfoDatabaseAdapter.copyMenuData(mContext);
                        db.close();
                        ufoDatabaseAdapter.close();

                        /** GET A NEW CURSOR AND REFRESH */
                        Cursor aCursor = RecyclerOcrListActivity.queryUfoLocal(null, mContext);
                        changeCursor(aCursor);
                        notifyItemChanged(layoutPosition);

                        mPopupWindowGlassEdit.dismiss();
                    }
                });

                // >>>>>>>>>>> Cancel <<<<<<<<<<<<<<<
                final TextView cancedlText = container.findViewById(R.id.glass_edit_cancel);
                cancedlText.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mPopupWindowGlassEdit.dismiss();
                    }
                });

                /*
                container.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View view, MotionEvent motionEvent) {
                        Log.v(TAG, "container.setOnTouchListener running with motion event:" + motionEvent + " and view " + view );
                        Log.v(TAG, "viewid:" + view.getId());
                        mPopupWindowGlassEdit.dismiss();
                        return false;
                    }
                });
                */
            }
        });

        // If the item is long-clicked, we want to change the icon in the model and in the database
        viewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Log.v(TAG, "setOnLongClickListener fired with view " + view); // view is RelativeLayout from list_item.xml
                android.app.AlertDialog.Builder editFeatureDialog = new android.app.AlertDialog.Builder(mContext);
                editFeatureDialog.setMessage("This is just a list of what you've scanned.  Back out of scanning to get into the regular beer listings." +
                        "\n\nIf you'd like to be able to manually edit the glass size and price, just tap an entry." +
                        "\n\nIf you want any other features, let me know!  Find \'ufoknurder\' on Facebook or email me using the address in the application\'s \'About\' section.");
                editFeatureDialog.setCancelable(true);
                editFeatureDialog.setNegativeButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });
                editFeatureDialog.create().show();

                /*
                String highlightState = modelItem.getHighlighted();
                ImageView viewToManage = (ImageView)view.findViewById(R.id.b_icon);
                ViewUpdateHelper.updateViewHighlightState(viewToManage, modelItem, false, mContext);

                UfoDatabaseAdapter.update(modelItem, layoutPosition); // Might remove position...no need to keep track of which positions changed
                KnurderApplication.addSearchTextToQueryPackage(mQueryText); // DRS 20171019 - otherwise we lose query text on long click.
                Cursor cursor = KnurderApplication.reQuery(mContext);
                changeCursor(cursor);

                notifyItemChanged(layoutPosition);
                */
                boolean longClickConsumed = false;
                return longClickConsumed;

            }
        });
    }

    private String buildDataLine(String glassPrice, String glassSize, String lastUpdated) {
        StringBuilder builder = new StringBuilder();
        if (glassPrice != null) {
            builder.append("$");
            builder.append(glassPrice);
        }
        if (glassSize != null && !"".equals(glassSize)) {
            builder.append("/");
            builder.append(glassSize);
            builder.append(" oz          ");
        } else {
            builder.append("                ");
        }
        //Log.v(TAG, "lastUpdated [" + lastUpdated + "]");
        if (lastUpdated != null) {
            builder.append("Last Update:" )  ;
            try {
                builder.append(localDateFormat.format(NDF.parse(lastUpdated)));
            } catch (ParseException e) {
                Log.e(TAG, "Could not parse lastUpdated " + lastUpdated + " " + e.getMessage());
            }

        }
        return builder.toString();
    }

    private void manageIcons(int i, ImageView[] glassesArray) {
        if (i < 0) return;
        for (ImageView view: glassesArray ) {
            view.setColorFilter(colorSysGray, PorterDuff.Mode.SRC_ATOP);
        }
        glassesArray[i].setColorFilter(R.color.colorActivePrimary, PorterDuff.Mode.SRC_ATOP);
        mUpdatedGlassSize = SIZES_LIST.get(i);
    }

    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView imageViewToManage;
        TextView textViewToManage;
        TextView tvFirst;
        TextView tvSecond;
        TextView tvDatabaseKey;
        TextView tvUfoDatabaseKey;
        TextView descriptionTextView;

        ViewHolder(View view) {
            super(view);

            tvFirst = (TextView)view.findViewById(R.id.b_name);
            tvSecond = (TextView)view.findViewById(R.id.b_second_line);
            imageViewToManage = (ImageView)view.findViewById(R.id.b_icon);
            textViewToManage = (TextView)view.findViewById(R.id.ounces_text);
            tvDatabaseKey = (TextView)view.findViewById(R.id.b_db_item);
            tvUfoDatabaseKey = (TextView)view.findViewById(R.id.b_db_ufo_item);
            descriptionTextView = (TextView) itemView.findViewById(R.id.b_description);
        }
    }
}



