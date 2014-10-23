package com.nakedape.mixmaticlaunchpad;

import android.app.Fragment;
import android.os.Bundle;
import android.util.SparseArray;

import java.util.HashMap;

/**
 * Created by Nathan on 10/11/2014.
 */
public class LaunchPadData extends Fragment {

    private SparseArray<LaunchPadActivity.Sample> samples;
    private long counter;
    private boolean isPlaying;
    private boolean isEditMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setSamples(SparseArray<LaunchPadActivity.Sample> samples){this.samples = samples;}
    public SparseArray<LaunchPadActivity.Sample> getSamples() {return samples;}
    public void setCounter(long counter) {this.counter = counter;}
    public long getCounter() {return counter;}
    public void setPlaying(boolean isPlaying) {this.isPlaying = isPlaying;}
    public boolean isPlaying() {return isPlaying;}
    public void setEditMode(boolean isEditMode) {this.isEditMode = isEditMode;}
    public boolean isEditMode() {return isEditMode;}
}
