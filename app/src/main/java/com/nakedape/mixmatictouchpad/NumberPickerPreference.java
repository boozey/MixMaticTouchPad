package com.nakedape.mixmatictouchpad;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.SeekBar;

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
        View view = super.onCreateDialogView();

        seekBar = (SeekBar)view.findViewById(R.id.seekBar);
        seekBar.setMax(max);
        seekBar.setProgress(mCurrentValue);

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
