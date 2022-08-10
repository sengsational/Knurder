package com.sengsational.chiralcode;

import android.content.Context;
import android.content.DialogInterface;
import androidx.appcompat.app.AlertDialog;
import android.widget.RelativeLayout;

/**
 * Created by Owner on 3/28/2017.
 * https://github.com/chiralcode/Android-Color-Picker
 */

    public class ColorPickerDialog extends AlertDialog {

        private ColorPicker colorPickerView;
        private final OnColorSelectedListener onColorSelectedListener;

        public ColorPickerDialog(Context context, int initialColor, OnColorSelectedListener onColorSelectedListener) {
            super(context);

            this.onColorSelectedListener = onColorSelectedListener;

            RelativeLayout relativeLayout = new RelativeLayout(context);
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

            colorPickerView = new ColorPicker(context);
            colorPickerView.setColor(initialColor);

            relativeLayout.addView(colorPickerView, layoutParams);

            setButton(BUTTON_POSITIVE, context.getString(android.R.string.ok), onClickListener);
            setButton(BUTTON_NEGATIVE, context.getString(android.R.string.cancel), onClickListener);

            setView(relativeLayout);

        }

        private OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case BUTTON_POSITIVE:
                        int selectedColor = colorPickerView.getColor();
                        onColorSelectedListener.onColorSelected(selectedColor);
                        break;
                    case BUTTON_NEGATIVE:
                        dialog.dismiss();
                        break;
                }
            }
        };

        public interface OnColorSelectedListener {
            public void onColorSelected(int color);
        }

    }

