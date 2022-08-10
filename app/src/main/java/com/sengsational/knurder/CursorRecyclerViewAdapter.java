package com.sengsational.knurder;

import android.database.Cursor;
import android.database.DataSetObserver;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;


public abstract class CursorRecyclerViewAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
    private static final String TAG = CursorRecyclerViewAdapter.class.getSimpleName();

    private boolean mDataValid;
    private Cursor mCursor;
    private int mRowIDColumn = -1;
    private DataSetObserver mDataSetObserver;

    /* */
    CursorRecyclerViewAdapter(Cursor cursor, boolean stableIds) {
        //if (cursor != null)  Log.v(TAG, "CursorRecyclerViewAdapter constructor with cursor count: " + cursor.getCount());


        // Use the FIRST instance of "_id".  Only comes into play when we join the big UFO table with the UFOLOCAL table
        String[] columnNames = cursor.getColumnNames();
        for (int i = 0; i < columnNames.length; i++) {
            if ("_id".equals(columnNames[i])){
                mRowIDColumn = i;
                break;
            }
        }

        mCursor = cursor;
        mDataValid = cursor != null;
        mDataSetObserver = new RecyclerViewDataSetObserver();
        if (mCursor != null) {
            mCursor.registerDataSetObserver(mDataSetObserver);
        }

        setHasStableIds(stableIds);
    }

    /* */
    @Override
    public final void onBindViewHolder (VH holder, int position) {
        //Log.v(TAG, "onBindViewHolder() with position " + position);
        if (!mDataValid) {
            //Log.v(TAG, "ERROR: mDataValid is false with position " + position);
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            //Log.v(TAG, "ERROR: couldn't move to position: " + position);
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        onBindViewHolder(holder, mCursor);
    }

    /* */
    public abstract void onBindViewHolder(VH holder, Cursor cursor);

    /* */
    public Cursor getCursor() {
        return mCursor;
    }

    /* */
    @Override
    public int getItemCount () {
        if (mDataValid && mCursor != null) {
            return mCursor.getCount();
        } else {
            //Log.v(TAG, "item count zero " + "mDataValid: " + mDataValid + " mCursor: " + mCursor.getCount());
            return 0;
        }
    }

    /* */
    @Override
    public long getItemId (int position) {
        try {
            if(hasStableIds() && mDataValid && mCursor != null){
                //Log.v(TAG, "moving to position " + position);
                if (mCursor.moveToPosition(position)) {
                    long longItemId = mCursor.getLong(mRowIDColumn);
                    //Log.v(TAG, "Returning item ID: " + longItemId);
                    return longItemId;
                } else {
                    //Log.v(TAG, "NO_D1");
                    return RecyclerView.NO_ID;
                }
            } else {
                //Log.v(TAG, "NO_D2");
                return RecyclerView.NO_ID;
            }
        } catch (Throwable t) {
            //Log.v(TAG, "NO_D3");
            return RecyclerView.NO_ID;
        }
    }

    /* */
    public void changeCursor(Cursor cursor) {
        //Log.v(TAG, "Change cursor " + cursor.getCount());
        if (cursor != null) {
            Cursor old = swapCursor(cursor);
            if (old != null) {
                old.close();
            }
        }
    }

    /* */
    public Cursor swapCursor(Cursor newCursor) {
        //Log.v(TAG, "Swap cursor " + newCursor + " old cursor: " + mCursor);
        if (newCursor == mCursor) {
            return null;
        }
        Cursor oldCursor = mCursor;
        if (oldCursor != null && mDataSetObserver != null) {
            oldCursor.unregisterDataSetObserver(mDataSetObserver);
        }
        mCursor = newCursor;
        if (newCursor != null) {
            if(mDataSetObserver != null) {
                newCursor.registerDataSetObserver(mDataSetObserver);
                //Log.v(TAG, "Registered data set observer");
            }
            mRowIDColumn = newCursor.getColumnIndexOrThrow("_id");
            //Log.v(TAG, "Valid new cursor");
            mDataValid = true;
            notifyDataSetChanged();
        } else {
            mRowIDColumn = -1;
            //Log.v(TAG, "ERROR: Invalid new cursor!");
            mDataValid = false;
            notifyItemRangeRemoved(0, getItemCount());
            // notifyDataSetChanged();
        }
        return oldCursor;
    }

    public CharSequence convertToString(Cursor cursor) {
        return cursor == null ? "" : cursor.toString();
    }

    private class RecyclerViewDataSetObserver extends DataSetObserver {
        @Override
        public void onChanged() {
            super.onChanged();
            Log.v(TAG, "RecyclerViewDataSetObserver.onChanged()");
            mDataValid = true;
            notifyDataSetChanged();
        }

        @Override
        public void onInvalidated() {
            super.onInvalidated();
            Log.v(TAG, "RecyclerViewDataSetObserver.onInvalidated()");
            mDataValid = false;
            notifyItemRangeRemoved(0, getItemCount());
            // notifyDataSetChanged();
        }
    }
}