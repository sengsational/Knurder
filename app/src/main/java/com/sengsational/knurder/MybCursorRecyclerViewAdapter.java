package com.sengsational.knurder;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


public class MybCursorRecyclerViewAdapter extends CursorRecyclerViewAdapter<MybCursorRecyclerViewAdapter.ViewHolder> {

    private static final String TAG = MybCursorRecyclerViewAdapter.class.getSimpleName();
    private boolean mShowDateInList;
    private boolean mSkipGagState = false;
    private Context mContext;
    private String mQueryText = "";

    public MybCursorRecyclerViewAdapter(Context context, Cursor c, boolean showDateInList, boolean skipGagState) {
        super(c, true);
        mContext = context;
        mShowDateInList = showDateInList;
        mSkipGagState = skipGagState;
    }

    @Override
    public MybCursorRecyclerViewAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        //Log.v(TAG, ">>>>>>>>>>>>onCreateViewHolder<<<<<<<<<<<<<");
        Context context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        View modelView = inflater.inflate(R.layout.b_item, parent, false);
        return new ViewHolder(modelView);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, Cursor aCursor) {
        //Log.v(TAG, "onBindViewHolder with cursor " + aCursor);
        //Log.v(TAG, ">>>>>>>>>>>>onBindViewHolder<<<<<<<<<<<<<");
        final SaucerItem modelItem = new SaucerItem(aCursor);
        final int layoutPosition = viewHolder.getLayoutPosition();
        viewHolder.tvFirst.setText(modelItem.getName());
        if (mShowDateInList){
            viewHolder.tvSecond.setText(modelItem.getCreated()) ;
        } else {
            String pctString = SaucerItem.getPctString(modelItem.getAbv()) + "  -  ";
            pctString = pctString.length()==5?"":pctString;
            String specialTastedMessage = "";
            if (mSkipGagState) {
                // This is the flagged beer list.  We need to add a 'tasted' if it is tasted.
                if ("T".equals(modelItem.getTasted())) specialTastedMessage = "    [TASTED]";
            }
            viewHolder.tvSecond.setText(pctString + modelItem.getStyle() + modelItem.getQueText(mContext) + specialTastedMessage);
        }

        String newArrivalState = modelItem.getNewArrival();
        String tastedState = modelItem.getTasted();

        String fontDeterminer = (newArrivalState!=null?newArrivalState:"F") + (tastedState!=null?tastedState:"F");

        ViewUpdateHelper.setNameTextStyle(fontDeterminer, viewHolder.tvFirst);

        String active = modelItem.getActive();
        if(!"T".equals(active)){
            //Log.v(TAG, "setting non-active color");
            //viewHolder.tvFirst.setTextColor(ContextCompat.getColor(mContext, android.R.color.tertiary_text_dark));
            viewHolder.tvFirst.setTextColor(ContextCompat.getColor(mContext, R.color.colorNonActiveTertiary));
            //viewHolder.tvSecond.setTextColor(ContextCompat.getColor(mContext, android.R.color.secondary_text_dark));
            viewHolder.tvSecond.setTextColor(ContextCompat.getColor(mContext, R.color.colorNonActiveSecondary));
        } else {
            //Log.v(TAG, "setting ACTIVE color");
            //viewHolder.tvFirst.setTextColor(ContextCompat.getColor(mContext, android.R.color.primary_text_light));
            viewHolder.tvFirst.setTextColor(ContextCompat.getColor(mContext, R.color.colorActivePrimary));
            //viewHolder.tvSecond.setTextColor(ContextCompat.getColor(mContext, android.R.color.secondary_text_light));
            viewHolder.tvSecond.setTextColor(ContextCompat.getColor(mContext, R.color.colorNonActiveSecondary));
        }

        ViewUpdateHelper.setHighlightIconInView(viewHolder.viewToManage, modelItem.getHighlighted(), mContext);

        // DRS 20171128 - Added 2+ if/else - Menu scan
        //ViewUpdateHelper.setGlassShapeIconInView(viewHolder.glassSize, modelItem.getGlassSize(), mContext);
        String container = modelItem.getContainer();
        if (container == null) {
            if (!modelItem.getName().contains("(CAN)") && !modelItem.getName().contains("(BTL)")) {
                container = "draught";
            } else {
                container = "nottap";
            }
        }
        ViewUpdateHelper.setGlassShapeIconInView(viewHolder.glassSize, viewHolder.ouncesText, modelItem.getGlassSize(), container, mContext);
        String glassPrice = modelItem.getGlassPrice();
        if (glassPrice !=null && glassPrice.length() > 0) {
            viewHolder.glassPrice.setText("$" + glassPrice);
            viewHolder.glassPrice.setVisibility(View.VISIBLE);
        } else {
            viewHolder.glassPrice.setVisibility(View.GONE);
        }

        viewHolder.tvDatabaseKey.setText(modelItem.getId() + "");


        // DEFINE ACTIVITY THAT HAPPENS WHEN ITEM IS CLICKED
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.v(TAG, "setOnClickListener fired with view " + view); // view is RelativeLayout from list_item.xml
                Intent intent = new Intent(mContext, BeerSlideActivity.class);
                intent.putExtra(BeerSlideActivity.EXTRA_POSITION, layoutPosition);
                ((Activity)mContext).startActivityForResult(intent, RecyclerSqlbListActivity.DETAIL_REQUEST);

            }
        });

        // If the item is long-clicked, we want to change the icon in the model and in the database
        viewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                Log.v(TAG, "setOnLongClickListener fired with view " + view); // view is RelativeLayout from list_item.xml

                // This code was duplicated in the long click of MybCursorRecyclerViewAdapter, now uses updateViewHighlightState()
                // This code was duplicated in the long click of BeerSlideFragment, now uses updateViewHighlightState()
                // DRS 20161130 - Added new implementation
                String highlightState = modelItem.getHighlighted();
                //Log.v(TAG, "iconView was visible? " + (view.findViewById(R.id.b_icon).getVisibility() == View.VISIBLE) + " modelHighlighted letter: " + highlightState);
                ImageView viewToManage = (ImageView)view.findViewById(R.id.b_icon);
                //Log.v(TAG, "This could be a problem... what does our view to manage look like? " + viewToManage);
                Log.v(TAG, "mSkipGagState: " + mSkipGagState);
                ViewUpdateHelper.updateViewHighlightState(viewToManage, modelItem, mSkipGagState, mContext);

                //Log.v(TAG, "updating the database from the changed model with position " + layoutPosition);
                UfoDatabaseAdapter.update(modelItem, layoutPosition, mContext); // Might remove position...no need to keep track of which positions changed
                KnurderApplication.addSearchTextToQueryPackage(mQueryText, mContext); // DRS 20171019 - otherwise we lose query text on long click.
                Cursor cursor = KnurderApplication.reQuery(mContext);
                changeCursor(cursor);

                notifyItemChanged(layoutPosition);

                boolean longClickConsumed = true;
                return longClickConsumed;

            }
        });
    }

    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    public void setQueryText(String queryText) {
        this.mQueryText = queryText;
    }


    static class ViewHolder extends RecyclerView.ViewHolder {

        ImageView viewToManage;
        TextView tvFirst;
        TextView tvSecond;
        TextView tvDatabaseKey;
        TextView descriptionTextView;

        ImageView glassSize; // DRS 20171128 - Menu scan
        TextView glassPrice; // DRS 20171128 - Menu scan

        TextView ouncesText;

        ViewHolder(View view) {
            super(view);

            tvFirst = (TextView)view.findViewById(R.id.b_name);
            tvSecond = (TextView)view.findViewById(R.id.b_second_line);
            viewToManage = (ImageView)view.findViewById(R.id.b_icon);
            tvDatabaseKey = (TextView)view.findViewById(R.id.b_db_item);
            descriptionTextView = (TextView) itemView.findViewById(R.id.b_description);
            glassSize = (ImageView)view.findViewById(R.id.glass_icon); // DRS 20171128 - Menu scan
            ouncesText = (TextView)view.findViewById(R.id.ounces_text); // DRS 20171128 - Menu scan
            glassPrice = (TextView)view.findViewById(R.id.b_price); // DRS 20171128 - Menu scan
        }



    }

}



