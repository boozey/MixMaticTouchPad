package com.nakedape.mixmatictouchpad;

import android.app.Fragment;
import android.os.Bundle;

import java.util.HashMap;

/**
 * Created by Nathan on 10/11/2014.
 */
public class LaunchPadData extends Fragment {

    private HashMap samples;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setSamples(HashMap samples){this.samples = samples;}
    public HashMap getSamples() {return samples;}
}
