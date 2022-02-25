package com.sengsational.chiralcode;

/**
 * Created by Owner on 3/28/2017.
 * https://github.com/chiralcode/Android-Color-Picker/blob/master/src/com/chiralcode/colorpicker/demo/ColorPickerActivity.java
 */

    import android.app.Activity;
    import android.graphics.Color;
    import android.os.Bundle;
    import android.view.View;
    import android.view.View.OnClickListener;
    import android.widget.Button;
    import android.widget.Toast;

    import com.sengsational.knurder.R;


public class ColorPickerActivity extends Activity {

        private ColorPicker colorPicker;
        private Button button;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.act_picker);

            colorPicker = (ColorPicker) findViewById(R.id.colorPicker);

            button = (Button) findViewById(R.id.button);
            button.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {

                    int color = colorPicker.getColor();
                    String rgbString = "R: " + Color.red(color) + " B: " + Color.blue(color) + " G: " + Color.green(color);

                    Toast.makeText(ColorPickerActivity.this, rgbString, Toast.LENGTH_SHORT).show();

                }
            });

        }

    }
