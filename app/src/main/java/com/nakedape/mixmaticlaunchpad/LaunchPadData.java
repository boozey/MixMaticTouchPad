package com.nakedape.mixmaticlaunchpad;

import android.app.Fragment;
import android.os.Bundle;

import java.util.HashMap;

/**
 * Created by Nathan on 10/11/2014.
 */
public class LaunchPadData extends Fragment {

    private HashMap samples;
    private long counter;
    private boolean isPlaying;
    private boolean isEditMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setSamples(HashMap samples){this.samples = samples;}
    public HashMap getSamples() {return samples;}
    public void setCounter(long counter) {this.counter = counter;}
    public long getCounter() {return counter;}
    public void setPlaying(boolean isPlaying) {this.isPlaying = isPlaying;}
    public boolean isPlaying() {return isPlaying;}
    public void setEditMode(boolean isEditMode) {this.isEditMode = isEditMode;}
    public boolean isEditMode() {return isEditMode;}
}
