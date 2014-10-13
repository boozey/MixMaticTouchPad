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
public class SampleEditPreferencesFragment extends PreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String PREF_BEAT_THRESHOLD = "pref_beat_threshold";

    private SharedPreferences pref;

    public SampleEditPreferencesFragment() {
        // Required empty public constructor
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.sample_edit_preferences);
        pref = getPreferenceScreen().getSharedPreferences();
        pref.registerOnSharedPreferenceChangeListener(this);
        NumberPickerPreference beatPref = (NumberPickerPreference)findPreference(PREF_BEAT_THRESHOLD);
        beatPref.setMax(60);
        beatPref.setSummary(String.valueOf(pref.getInt(PREF_BEAT_THRESHOLD, 30)));

    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                          String key) {
        if (key.equals(PREF_BEAT_THRESHOLD)){
            Preference beatPref = findPreference(key);
            // Set summary to be the user-description for the selected value
            beatPref.setSummary(String.valueOf(sharedPreferences.getInt(key, 30)));

        }
    }

}
