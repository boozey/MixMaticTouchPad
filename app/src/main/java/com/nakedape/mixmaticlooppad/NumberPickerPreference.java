package com.nakedape.mixmaticlooppad;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * Created by Nathan on 10/12/2014.
 */
public class NumberPickerPreference extends DialogPreference {
    private int max = 100;
    private SeekBar seekBar;
    private int DEFAULT_VALUE = 100;
    private int mCurrentValue;

    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.numberpicker_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        setDialogIcon(null);
    }

    public void setMax(int max){
        this.max = max;
    }

    @Override
     protected View onCreateDialogView() {
        final View view = super.onCreateDialogView();

        seekBar = (SeekBar)view.findViewById(R.id.seekBar);
        seekBar.setMax(max);
        seekBar.setProgress(mCurrentValue);
        TextView text = (TextView)view.findViewById(R.id.textView);
        text.setText("Value: " + String.valueOf(mCurrentValue));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                TextView text = (TextView)view.findViewById(R.id.textView);
                text.setText("Value: " + String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        return view;
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        // When the user selects "OK", persist the new value
        if (positiveResult) {
            mCurrentValue = seekBar.getProgress();
            persistInt(mCurrentValue);
        }
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // Restore existing state
            mCurrentValue = this.getPersistedInt(DEFAULT_VALUE);
        } else {
            // Set default state from the XML attribute
            mCurrentValue = (Integer) defaultValue;
            persistInt(mCurrentValue);
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, DEFAULT_VALUE);
    }
}
