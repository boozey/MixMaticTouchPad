package com.nakedape.mixmatictouchpad;



import android.content.SharedPreferences;
import android.os.Bundle;
import android.app.Fragment;
import android.preference.Preference;
import android.preference.PreferenceFragment;


/**
 * A simple {@link Fragment} subclass.
 *
 */
public class LaunchPadPreferencesFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String PREF_BPM = "pref_bpm";
    public static final String PREF_TIME_SIG = "pref_time_signature";

    private SharedPreferences sharedPrefs;

    public LaunchPadPreferencesFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.launchpad_preferences);
        sharedPrefs = getPreferenceScreen().getSharedPreferences();
        sharedPrefs.registerOnSharedPreferenceChangeListener(this);

        // Set initial summaries
        NumberPickerPreference bpmPref = (NumberPickerPreference) findPreference(PREF_BPM);
        bpmPref.setMax(200);
        bpmPref.setSummary(String.valueOf(sharedPrefs.getInt(PREF_BPM, 120)));
        Preference pref = findPreference(PREF_TIME_SIG);
        pref.setSummary(sharedPrefs.getString(PREF_TIME_SIG, "4") + "/4");

    }


    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
        String key) {
        Preference pref = findPreference(key);
        if (key.equals(PREF_BPM)) {
            // Set summary to be the user-description for the selected value
            pref.setSummary(String.valueOf(sharedPreferences.getInt(key, 30)));
        }
        else if (key.equals(PREF_TIME_SIG)){
            pref.setSummary(sharedPreferences.getString(key, "4") + "/4");
        }
    }
}
