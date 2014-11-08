package com.nakedape.mixmaticlooppad;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;


public class EditPreferencesActivity extends Activity {

    public static final String SAMPLE_EDIT_PREFS = "com.nakedape.mixmaticlooppad.sampleeditprefs";
    public static final String LAUNCHPAD_PREFS  = "com.nakedape.mixmaticlooppad.launchpadprefs";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent.getAction().equals(SAMPLE_EDIT_PREFS)) {
            // Display the fragment as the sample_edit content.
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SampleEditPreferencesFragment())
                    .commit();
        }
        else if (intent.getAction().equals(LAUNCHPAD_PREFS)){
            // Display the fragment as the sample_edit content.
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new LaunchPadPreferencesFragment())
                    .commit();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.sample_edit_preferences, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
