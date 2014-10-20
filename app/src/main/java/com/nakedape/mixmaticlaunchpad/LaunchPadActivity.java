package com.nakedape.mixmaticlaunchpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.Uri;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

// Licensing imports
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.Policy;
import com.google.android.vending.licensing.ServerManagedPolicy;


public class LaunchPadActivity extends Activity {

    private static final String LOG_TAG = "MixMatic Launch Pad Activity";

    public static String TOUCHPAD_ID = "com.nakedape.mixmaticlaunchpad.touchpadid";
    public static String TOUCHPAD_ID_ARRAY = "com.nakedape.mixmaticlaunchpad.touchpadidarray";
    public static String SAMPLE_PATH = "com.nakedape.mixmaticlaunchpad.samplepath";
    public static String COLOR = "com.nakedape.mixmaticlaunchpad.color";
    public static String LOOPMODE = "com.nakedape.mixmaticlaunchpad.loop";
    public static String LAUNCHMODE = "com.nakedape.mixmaticlaunchpad.launchmode";
    public static String NUM_SLICES = "com.nakedape.mixmaticlaunchpad.numslices";
    public static String SLICE_PATHS = "com.nakedape.mixmaticlaunchpad.slicepaths";
    private static int GET_SAMPLE = 0;
    private static int GET_SLICES = 1;
    private static final int COUNTER_UPDATE = 3;

    // Licensing
    private static final String BASE_64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAi8rbiVIkVPQvsF7d5CrHXnYeh/WsBRAUdjVADnto9X32e6q3O0aB0E4Kz4C7GuBV1dBvARWL7B1Cb4qI0zvjBi8fJT6/OxQDPssEFSdODXxY7xp6dexbJ1huBdGR8IVg5np06C20s9lH3iPuMdzRa26dP4xnP2vL2G90+msqpxpfR84TxG1sHrOM24o1yzg6pgGmFlHMXL7x+XDZyVZN3TNZR9CSeI+ygvVSg9DZPDQSz1T1cIebQ6MctvCQ0Vi17VT8pAnOM8BXUZUSuaetZHM/OXrhmk3MCFKW4RTrGf5NG1+3U0QQ6+wOkyJXwDdGLyz1/IEQTPCmqOs/LwYdFwIDAQAB";
    private LicenseCheckerCallback mLicenseCheckerCallback;
    private LicenseChecker mChecker;
    // Generate 20 random bytes, and put them here.
    private static final byte[] SALT = new byte[] {
            1, -15, -87, 52, 114, 11, -21, 12, 32, -63, 49,
            0, -91, 30, 110, -4, 77, -115, 18, -1
    };
    private String DEVICE_ID;
    private boolean isLicensed = false;

    private boolean isEditMode = false;
    private boolean isPlaying = false;

    // Counter
    private TextView counterTextView;
    private long counter;
    private int bpm = 120;
    private int timeSignature = 4;
    private Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case COUNTER_UPDATE:
                    updateCounterMessage();
                    break;
            }
        }
    };
    private void updateCounterMessage(){
        double beatsPerSec = (double)bpm / 60;
        double sec = (double)counter / 1000;
        double beats = sec * beatsPerSec;
        int bars = (int)Math.floor(beats / timeSignature);
        counterTextView.setText(String.format(Locale.US, "%d BPM  %2d : %.2f", bpm, bars, beats % timeSignature + 1));
    }

    private AudioTrack.OnPlaybackPositionUpdateListener samplePlayListener = new AudioTrack.OnPlaybackPositionUpdateListener() {
        @Override
        public void onMarkerReached(AudioTrack track) {
            int id = track.getAudioSessionId();
            View v = findViewById(id);
            if (v != null) {
                v.setPressed(false);
                Sample s = (Sample) samples.get(id);
                s.stop();
            }
        }

        @Override
        public void onPeriodicNotification(AudioTrack track) {

        }
    };

    private Context context;
    private ProgressDialog progressDialog;
    private HashMap<Integer, Sample> samples;
    private File homeDir;
    private int numTouchPads;
    private AudioManager am;
    private SharedPreferences launchPadprefs; // Stores setting for each launchpad
    private SharedPreferences activityPrefs; // Stores app wide preferences
    private LaunchPadData savedData;
    private boolean savedDataLoaded = false;

    private int selectedSampleID;
    private boolean multiSelect = false;
    private ArrayList<String> selections = new ArrayList<String>();
    private ActionMode launchPadActionMode;
    private ActionMode emptyPadActionMode;
    private ActionMode.Callback emptyPadActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.launch_pad_empty_context, menu);
            isEditMode = true;
            View newView = findViewById(selectedSampleID);
            newView.setSelected(true);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Intent intent;
            switch (item.getItemId()){
                case R.id.action_load_sample:
                    intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                    intent.putExtra(TOUCHPAD_ID, selectedSampleID);
                    startActivityForResult(intent, GET_SAMPLE);
                    return true;
                case R.id.action_multi_select:
                    if (!multiSelect){
                        selections = new ArrayList<String>();
                    }
                    multiSelect = true;
                    selections.add(String.valueOf(selectedSampleID));
                    Menu menu = mode.getMenu();
                    MenuItem newItem = menu.findItem(R.id.action_load_sample);
                    newItem.setVisible(false);
                    newItem = menu.findItem(R.id.action_multi_select);
                    newItem.setVisible(false);
                    Toast.makeText(context, R.string.slice_multi_select, Toast.LENGTH_SHORT).show();
                    return true;
                case R.id.action_load_sample_mode:
                    if (multiSelect){
                        if (selections.size() > 0) {
                            intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                            intent.putExtra(NUM_SLICES, selections.size());
                            startActivityForResult(intent, GET_SLICES);
                        }
                        else {
                            Toast.makeText(context, R.string.slice_selection_error, Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    }
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            emptyPadActionMode = null;
            isEditMode = false;
            multiSelect = false;
            View oldView = findViewById(selectedSampleID);
            oldView.setSelected(false);
            if (selections != null) {
                for (String s : selections) {
                    oldView = findViewById(Integer.parseInt(s));
                    oldView.setSelected(false);
                }
            }
        }
    };
    private ActionMode.Callback launchPadActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.launch_pad_context, menu);
            isEditMode = true;
            View oldView = findViewById(selectedSampleID);
            oldView.setSelected(true);
            if (samples.containsKey(selectedSampleID)) {
                Sample s = (Sample) samples.get(selectedSampleID);
                MenuItem item = menu.findItem(R.id.action_loop_mode);
                item.setChecked(s.getLoopMode());
                if (s.getLaunchMode() == Sample.LAUNCHMODE_TRIGGER) {
                    item = menu.findItem(R.id.action_launch_mode_trigger);
                    item.setChecked(true);
                }
                else {
                    item = menu.findItem(R.id.action_launch_mode_gate);
                    item.setChecked(true);
                }

            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            SharedPreferences.Editor prefEditor = launchPadprefs.edit();
            switch (item.getItemId()){
                case R.id.action_edit_sample:
                    Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                    intent.putExtra(TOUCHPAD_ID, selectedSampleID);
                    if (samples.containsKey(selectedSampleID)){
                        intent.putExtra(SAMPLE_PATH, homeDir.getAbsolutePath() + "/" + "Mixmatic_Touch_Pad_" + String.valueOf(selectedSampleID) + ".wav");
                        intent.putExtra(COLOR, launchPadprefs.getInt(String.valueOf(selectedSampleID) + COLOR, 0));
                    }
                    startActivityForResult(intent, GET_SAMPLE);
                    return true;
                case R.id.action_loop_mode:
                    if (item.isChecked()) {
                        item.setChecked(false);
                        if (samples.containsKey(selectedSampleID)) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(false);
                            prefEditor.putBoolean(String.valueOf(selectedSampleID) + LOOPMODE, false);
                            prefEditor.apply();
                        }
                    }
                    else {
                        item.setChecked(true);
                        if (samples.containsKey(selectedSampleID)) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(true);
                            prefEditor.putBoolean(String.valueOf(selectedSampleID) + LOOPMODE, true);
                            prefEditor.apply();
                        }
                    }
                    return true;
                case R.id.action_remove_sample:
                    if (samples.containsKey(selectedSampleID)){
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setMessage(getString(R.string.warning_remove_sample));
                        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Sample s = (Sample)samples.get(selectedSampleID);
                                File f = new File(s.getPath());
                                f.delete();
                                samples.remove(selectedSampleID);
                                View v = findViewById(selectedSampleID);
                                v.setBackgroundResource(R.drawable.launch_pad_empty);
                                Toast.makeText(context, "Sample removed", Toast.LENGTH_SHORT).show();
                                emptyPadActionMode = startActionMode(emptyPadActionModeCallback);
                                launchPadActionMode = null;

                            }
                        });
                        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                    return true;
                case R.id.action_launch_mode_gate:
                    if (samples.containsKey(selectedSampleID)){
                        Sample s = (Sample)samples.get(selectedSampleID);
                        s.setLaunchMode(Sample.LAUNCHMODE_GATE);
                        item.setChecked(true);
                        prefEditor.putInt(String.valueOf(selectedSampleID) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                        prefEditor.apply();
                    }
                    return true;
                case R.id.action_launch_mode_trigger:
                    if (samples.containsKey(selectedSampleID)){
                        Sample s = (Sample)samples.get(selectedSampleID);
                        s.setOnPlayFinishedListener(samplePlayListener);
                        s.setLaunchMode(Sample.LAUNCHMODE_TRIGGER);
                        item.setChecked(true);
                        prefEditor.putInt(String.valueOf(selectedSampleID) + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER);
                        prefEditor.apply();
                    }
                    return true;
                case R.id.action_pick_color:
                    AlertDialog.Builder builder = new AlertDialog.Builder(context);
                    builder.setTitle(R.string.color_dialog_title);
                    builder.setItems(R.array.color_names, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (samples.containsKey(selectedSampleID)){
                                View v = findViewById(selectedSampleID);// Load shared preferences to save color
                                SharedPreferences.Editor editor = launchPadprefs.edit();
                                switch (which){ // Set and save color
                                    case 0:
                                        v.setBackgroundResource(R.drawable.launch_pad_blue);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 0);
                                        break;
                                    case 1:
                                        v.setBackgroundResource(R.drawable.launch_pad_red);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 1);
                                        break;
                                    case 2:
                                        v.setBackgroundResource(R.drawable.launch_pad_green);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 2);
                                        break;
                                    case 3:
                                        v.setBackgroundResource(R.drawable.launch_pad_orange);
                                        editor.putInt(String.valueOf(selectedSampleID) + COLOR, 3);
                                        break;
                                }
                                editor.apply();
                            }
                        }
                    });
                    AlertDialog dialog = builder.create();
                    dialog.show();
                default:
                    return true;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            launchPadActionMode = null;
            View oldView = findViewById(selectedSampleID);
            oldView.setSelected(false);
            isEditMode = false;
        }
    };

    // Handles touch events in play mode;
    private View.OnTouchListener TouchPadTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!isEditMode && samples.containsKey(v.getId())) {
                if (!isPlaying){ // Start counter if it isn't already running
                    isPlaying = true;
                    new Thread(new CounterThread()).start();
                }
                Sample s = samples.get(v.getId());
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        switch (s.getLaunchMode()){
                            case Sample.LAUNCHMODE_GATE: // Stop sound and deselect pad
                                s.stop();
                                v.setPressed(false);
                                break;
                            default:
                                break;
                        }
                        return true;
                    case MotionEvent.ACTION_DOWN:
                        // If the sound is already playing, stop it
                        if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                            s.stop();
                            v.setPressed(false);
                            return true;
                        }
                        // Otherwise play the sample
                        else if (s.hasPlayed())
                            s.reset();
                        s.play();
                        v.setPressed(true);
                        return true;
                    default:
                        return false;
                }
            }
            return false;
        }
    };


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == GET_SAMPLE && resultCode == RESULT_OK) {
            String path = data.getData().getPath();
            File f = new File(path); // File to contain the new sample
            if (!homeDir.isDirectory()) // If the home directory doesn't exist, create it
                homeDir.mkdir();
            // Create a new file to contain the new sample
            File sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(data.getIntExtra(TOUCHPAD_ID, 0)) + ".wav");
            // If the file already exists, delete, but remember to keep its configuration
            boolean keepSettings = false;
            if (sampleFile.isFile()) {
                sampleFile.delete();
                keepSettings = true;
            }
            // Copy new sample over
            try {
                CopyFile(f, sampleFile);
            } catch (IOException e){e.printStackTrace();}
            if (sampleFile.isFile()) { // If successful, prepare touchpad
                int id = data.getIntExtra(TOUCHPAD_ID, 0);
                Sample sample = new Sample(sampleFile.getAbsolutePath(), id);
                sample.setOnPlayFinishedListener(samplePlayListener);
                TouchPad t = (TouchPad)findViewById(id);
                // Set sample properties and save to shared preferences
                SharedPreferences.Editor editor = launchPadprefs.edit();
                if (keepSettings){
                    sample.setLaunchMode(launchPadprefs.getInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_GATE));
                    sample.setLoopMode(launchPadprefs.getBoolean(String.valueOf(id) + LOOPMODE, false));
                }
                else { // Default properties
                    sample.setLaunchMode(Sample.LAUNCHMODE_GATE);
                    editor.putInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                    editor.putBoolean(String.valueOf(id) + LOOPMODE, false);
                }
                samples.put(id, sample);
                switch (data.getIntExtra(COLOR, 0)){ // Set and save color
                    case 0:
                        t.setBackgroundResource(R.drawable.launch_pad_blue);
                        editor.putInt(String.valueOf(id) + COLOR, 0);
                        break;
                    case 1:
                        t.setBackgroundResource(R.drawable.launch_pad_red);
                        editor.putInt(String.valueOf(id) + COLOR, 1);
                        break;
                    case 2:
                        t.setBackgroundResource(R.drawable.launch_pad_green);
                        editor.putInt(String.valueOf(id) + COLOR, 2);
                        break;
                    case 3:
                        t.setBackgroundResource(R.drawable.launch_pad_orange);
                        editor.putInt(String.valueOf(id) + COLOR, 3);
                        break;
                }
                editor.apply();
                // Show the action bar for pads with loaded samples
                if (emptyPadActionMode != null)
                    emptyPadActionMode = null;
                if (launchPadActionMode == null)
                    launchPadActionMode = startActionMode(launchPadActionModeCallback);
            }
        }
        else if (requestCode == GET_SLICES && resultCode == RESULT_OK){
            String[] slicePaths = data.getStringArrayExtra(SLICE_PATHS);
            for (int i = 0; i < selections.size(); i++){
                File tempFile = new File(slicePaths[i]);
                File sliceFile = new File(homeDir, selections.get(i) + ".wav" );
                if (sliceFile.isFile()) sliceFile.delete();
                // Copy new sample over
                try {
                    CopyFile(tempFile, sliceFile);
                } catch (IOException e){e.printStackTrace();}
                if (sliceFile.isFile()) { // If successful, prepare touchpad
                    int id = Integer.parseInt(selections.get(i));
                    // Set sample properties and save to shared preferences
                    SharedPreferences.Editor editor = launchPadprefs.edit();
                    Sample sample = new Sample(sliceFile.getAbsolutePath(), id);
                    sample.setOnPlayFinishedListener(samplePlayListener);
                    sample.setLaunchMode(Sample.LAUNCHMODE_GATE);
                    editor.putInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                    editor.putBoolean(String.valueOf(id) + LOOPMODE, false);
                    samples.put(id, sample);
                    TouchPad t = (TouchPad) findViewById(id);
                    switch (data.getIntExtra(COLOR, 0)) { // Set and save color
                        case 0:
                            t.setBackgroundResource(R.drawable.launch_pad_blue);
                            editor.putInt(String.valueOf(id) + COLOR, 0);
                            break;
                        case 1:
                            t.setBackgroundResource(R.drawable.launch_pad_red);
                            editor.putInt(String.valueOf(id) + COLOR, 1);
                            break;
                        case 2:
                            t.setBackgroundResource(R.drawable.launch_pad_green);
                            editor.putInt(String.valueOf(id) + COLOR, 2);
                            break;
                        case 3:
                            t.setBackgroundResource(R.drawable.launch_pad_orange);
                            editor.putInt(String.valueOf(id) + COLOR, 3);
                            break;
                    }
                    editor.apply();
                    // Show the action bar for pads with loaded samples
                    if (emptyPadActionMode != null)
                        emptyPadActionMode = null;
                    if (launchPadActionMode == null)
                        launchPadActionMode = startActionMode(launchPadActionModeCallback);
                }
            }
        }
    }

    // Handles click events in edit mode
    public void touchPadClick(View v) {
        if (isEditMode && !multiSelect) {
            // Deselect the current touchpad
            View oldView = findViewById(selectedSampleID);
            if (oldView != null)
                oldView.setSelected(false);
            selectedSampleID = v.getId();
            // If the pad contains a sample, show the edit menu
            if (samples.containsKey(v.getId())) {
                if (emptyPadActionMode != null)
                    emptyPadActionMode = null;
                if (launchPadActionMode == null)
                    launchPadActionMode = startActionMode(launchPadActionModeCallback);
                Sample s = (Sample) samples.get(v.getId());
                Menu menu = launchPadActionMode.getMenu();
                MenuItem item = menu.findItem(R.id.action_loop_mode);
                item.setChecked(s.getLoopMode());
                if (s.getLaunchMode() == Sample.LAUNCHMODE_TRIGGER) {
                    item = menu.findItem(R.id.action_launch_mode_trigger);
                    item.setChecked(true);
                } else {
                    item = menu.findItem(R.id.action_launch_mode_gate);
                    item.setChecked(true);
                }
                // Select the new touchpad
                v.setSelected(true);
                isEditMode = true;
            }
            // If the pad doesn't contain a sample, show the menu to load one
            else {
                if (launchPadActionMode != null)
                    launchPadActionMode = null;
                if (emptyPadActionMode == null)
                    emptyPadActionMode = startActionMode(emptyPadActionModeCallback);
                v.setSelected(true);
                isEditMode = true;
            }
        } // If in multiselect mode allow empty pads to be selected
        else if (multiSelect) {
            if (!samples.containsKey(v.getId())) {
                if (v.isSelected()) {
                    v.setSelected(false);
                    selections.remove(String.valueOf(v.getId()));
                } else {
                    v.setSelected(true);
                    selections.add(String.valueOf(v.getId()));
                }
            } // If a touchpad contains a sample, exit multiselect mode and show the edit menu
            else {
                multiSelect = false;
                if (emptyPadActionMode != null)
                    emptyPadActionMode = null;
                if (launchPadActionMode == null)
                    launchPadActionMode = startActionMode(launchPadActionModeCallback);
            }
        }
        else {
            selectedSampleID = v.getId();
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;

        // Do the license check
        DEVICE_ID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        // Construct the LicenseCheckerCallback.
        mLicenseCheckerCallback = new MyLicenseCheckerCallback();
        // Construct the LicenseChecker with a Policy.
        mChecker = new LicenseChecker(
                context, new ServerManagedPolicy(this,
                new AESObfuscator(SALT, getPackageName(), DEVICE_ID)),
                BASE_64_PUBLIC_KEY);
        mChecker.checkAccess(mLicenseCheckerCallback);
        licensedOnCreate();
    }
    private void licensedOnCreate(){
        setContentView(R.layout.activity_launch_pad);
        homeDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/MixMatic");
        launchPadprefs = getPreferences(MODE_PRIVATE);

        PreferenceManager.setDefaultValues(this, R.xml.sample_edit_preferences, true);
        activityPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        counterTextView = (TextView)findViewById(R.id.textViewCounter);
        bpm = activityPrefs.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
        timeSignature = Integer.parseInt(activityPrefs.getString(LaunchPadPreferencesFragment.PREF_TIME_SIG, "4"));
        updateCounterMessage();

        // Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        savedData = (LaunchPadData) fm.findFragmentByTag("data");
        if (savedData != null){
            samples = savedData.getSamples();
            counter = savedData.getCounter();
            double sec = (double)counter / 1000;
            int min = (int)Math.floor(sec / 60);
            counterTextView.setText(String.format(Locale.US, "%d BPM  %2d : %.2f", bpm, min, sec % 60));
            isEditMode = savedData.isEditMode();
            savedDataLoaded = true;
            // Setup touch pads from retained fragment
            setupPadsFromFrag();
        }
        else{
            savedData = new LaunchPadData();
            fm.beginTransaction().add(savedData, "data").commit();
            // Setup touch pads from files
            setupPadsFromFile();
        }
    }
    private void setupPadsFromFile(){
        if (!savedDataLoaded && homeDir.isDirectory()) {
            samples = new HashMap<Integer, Sample>();
            TouchPad pad = (TouchPad) findViewById(R.id.touchPad1);
            pad.setOnTouchListener(TouchPadTouchListener);
            File sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad2);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad3);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad4);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad5);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad6);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad7);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad8);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad9);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad10);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad11);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad12);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad13);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad14);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad15);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad16);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad17);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad18);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad19);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad20);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad21);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad22);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad23);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
            pad = (TouchPad) findViewById(R.id.touchPad24);
            pad.setOnTouchListener(TouchPadTouchListener);
            sampleFile = new File(homeDir, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
            if (sampleFile.isFile())  // If the sample exists, load it
                loadSample(sampleFile.getAbsolutePath(), pad);
        }
    }
    private void setupPadsFromFrag(){
        Sample sample;
        TouchPad pad = (TouchPad) findViewById(R.id.touchPad1);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad2);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad3);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad4);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad5);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad6);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad7);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad8);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad9);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad10);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad11);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad12);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad13);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad14);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad15);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad16);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad17);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad18);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad19);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad20);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad21);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad22);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad23);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
        pad = (TouchPad) findViewById(R.id.touchPad24);
        pad.setOnTouchListener(TouchPadTouchListener);
        if (samples.containsKey(pad.getId())) {
            setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
            sample = samples.get(pad.getId());
            sample.setOnPlayFinishedListener(samplePlayListener);
        }
    }
    private void loadSample(String path, TouchPad pad){
        int id = pad.getId();
        pad.setOnTouchListener(TouchPadTouchListener);
        Sample s = new Sample(path, id);
        s.setOnPlayFinishedListener(samplePlayListener);
        samples.put(id, s);
        s.setLoopMode(launchPadprefs.getBoolean(String.valueOf(id) + LOOPMODE, false));
        s.setLaunchMode(launchPadprefs.getInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER));
        int color = launchPadprefs.getInt(String.valueOf(id) + COLOR, 0);
        setPadColor(color, pad);
    }
    private void setPadColor(int color, TouchPad pad){
        switch (color){ // Load and set color
            case 0:
                pad.setBackgroundResource(R.drawable.launch_pad_blue);
                break;
            case 1:
                pad.setBackgroundResource(R.drawable.launch_pad_red);
                break;
            case 2:
                pad.setBackgroundResource(R.drawable.launch_pad_green);
                break;
            case 3:
                pad.setBackgroundResource(R.drawable.launch_pad_orange);
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.touch_pad, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            isPlaying = false;
            Intent intent = new Intent(EditPreferencesActivity.LAUNCHPAD_PREFS, null, context, EditPreferencesActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_edit_mode) {
            isEditMode = true;
            isPlaying = false;
            View v = findViewById(R.id.touchPad1);
            v.callOnClick();
        }
        else if (id == R.id.action_stop){
            isPlaying = false;
            for (int i = 0; i < numTouchPads; i++) {
                if (samples.containsKey(i)) {
                    Sample s = (Sample) samples.get(i);
                    s.stop();
                }
            }

        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onResume() {
        super.onResume();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (getActionBar() != null)
                getActionBar().hide();
        }
        int newBpm = activityPrefs.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
        int newTimeSignature = Integer.parseInt(activityPrefs.getString(LaunchPadPreferencesFragment.PREF_TIME_SIG, "4"));

        if (newBpm != bpm || newTimeSignature != timeSignature) {
            counter = 0;
            bpm = newBpm;
            timeSignature = newTimeSignature;
        }
        updateCounterMessage();
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        if (progressDialog != null)
            if (progressDialog.isShowing())
                progressDialog.dismiss();
        if (mChecker != null)
            mChecker.onDestroy();
        isPlaying = false;
        savedData.setSamples(samples);
        savedData.setCounter(counter);
        savedData.setEditMode(isEditMode);
    }


    // Counter thread keeps track of time
    private class CounterThread implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            long startMillis = SystemClock.elapsedRealtime();
            do {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {}
                counter = SystemClock.elapsedRealtime() - startMillis;
                Message msg = mHandler.obtainMessage(COUNTER_UPDATE);
                msg.sendToTarget();
            } while (isPlaying);
        }
    }

    private void CopyFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();
        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();
            if (outChannel != null)
                outChannel.close();
        }
    }

    public class Sample{
        // Public fields
        public static final int LAUNCHMODE_GATE = 0;
        public static final int LAUNCHMODE_TRIGGER = 1;

        // Private fields
        private int id;
        private String path;
        private boolean loop = false;
        private int loopMode = 0;
        private int launchMode = LAUNCHMODE_TRIGGER;
        private File sampleFile;
        private int sampleByteLength;
        private boolean played = false;
        private AudioTrack audioTrack;
        private AudioTrack.OnPlaybackPositionUpdateListener listener;

        // Constructors
        public Sample(String path, int id){
            this.path = path;
            this.id = id;
            sampleFile = new File(path);
            if (sampleFile.isFile()){
                sampleByteLength = (int)sampleFile.length() - 44;
                loadAudioTrack();
            }
        }
        public Sample(String path, int launchMode, boolean loopMode){
            this.path = path;
            loop = loopMode;
            if (!setLaunchMode(launchMode))
                this.launchMode = LAUNCHMODE_TRIGGER;
            loadAudioTrack();
        }

        // Public methods
        public void setViewId(int id){this.id = id;}
        public int getViewId(){return id;}
        public String getPath(){return path;}
        public void setLoopMode(boolean loop){
            this.loop = loop;
            if (loop) {
                loopMode = -1;
                if (hasPlayed()) {
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.reloadStaticData();
                    played = false;
                }
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
                    loadAudioTrack();
                audioTrack.setLoopPoints(0, sampleByteLength / 4, -1);
                audioTrack.setNotificationMarkerPosition(0);
                audioTrack.setPlaybackPositionUpdateListener(null);
            }
            else {
                loopMode = 0;
                if (hasPlayed()) {
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.reloadStaticData();
                    played = false;
                }
                try {
                    audioTrack.setLoopPoints(0, 0, 0);
                } catch (Exception e) {}
                setOnPlayFinishedListener(listener);
            }
        }
        public boolean getLoopMode(){
            return loop;
        }
        public int getLoopModeInt() {
            return loopMode;
        }
        public boolean setLaunchMode(int launchMode){
            if (launchMode == LAUNCHMODE_GATE){
                this.launchMode = LAUNCHMODE_GATE;
                return true;
            }
            else if (launchMode == LAUNCHMODE_TRIGGER){
                this.launchMode = LAUNCHMODE_TRIGGER;
                return true;
            }
            else
                return false;
        }
        public int getLaunchMode(){
            return launchMode;
        }
        public void play(){
            played = true;
            resetMarker();
            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
                audioTrack.play();
            else {
                Log.d("AudioTrack", String.valueOf(id) + " uninitialized");
                loadAudioTrack();
            }
        }
        public void stop(){
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                try {
                    audioTrack.pause();
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.release();
                } catch (IllegalStateException e) {
                }
                loadAudioTrack();
            }
        }
        public void pause(){
            audioTrack.pause();
        }
        public void reset(){
            if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
                audioTrack.stop();
            audioTrack.flush();
            audioTrack.release();
            loadAudioTrack();
        }
        public boolean hasPlayed(){
            return played;
        }
        public void setOnPlayFinishedListener(AudioTrack.OnPlaybackPositionUpdateListener listener){
            this.listener = listener;
            audioTrack.setPlaybackPositionUpdateListener(listener);
            resetMarker();
        }
        public void resetMarker(){
            audioTrack.setNotificationMarkerPosition(sampleByteLength / 4 - 2000);
        }

        // Private methods
        private void loadAudioTrack() {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    44100,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    sampleByteLength,
                    AudioTrack.MODE_STATIC, id);
            InputStream stream = null;
            try {
                stream = new BufferedInputStream(new FileInputStream(sampleFile));
                stream.skip(44);
                byte[] bytes = new byte[sampleByteLength];
                stream.read(bytes);
                short[] shorts = new short[bytes.length / 2];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                audioTrack.write(shorts, 0, shorts.length);
                stream.close();
                played = false;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (listener != null) {
                audioTrack.setPlaybackPositionUpdateListener(listener);
                resetMarker();
            }

            if (loop) {
                setLoopMode(true);
            }
        }
    }

    private class MyLicenseCheckerCallback implements LicenseCheckerCallback {
        public void allow(int reason) {
            if (isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }
            // Should allow user access.
            isLicensed = true;
        }

        public void dontAllow(int reason) {
            if (isFinishing()) {
                // Don't update UI if Activity is finishing.
                return;
            }

            if (reason == Policy.RETRY) {
                // If the reason received from the policy is RETRY, it was probably
                // due to a loss of connection with the service, so we should give the
                // user a chance to retry. So show a dialog to retry.
                Log.d(LOG_TAG, "Not licensed RETRY");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {

                        //finish();
                    }
                });
            } else {
                // Otherwise, the user is not licensed to use this app.
                // Your response should always inform the user that the application
                // is not licensed, but your behavior at that point can vary. You might
                // provide the user a limited access version of your app or you can
                // take them to Google Play to purchase the app.
                Log.d(LOG_TAG, "Not licensed");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setCancelable(false);
                        builder.setMessage("This app is not properly licensed.  Go to Google Play Store to download a licensed version?");
                        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("market://details?id=com.nakedape.mixmaticlaunchpad"));
                                startActivity(intent);
                                finish();
                            }
                        });
                        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                //finish();
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
            }
        }

        public void applicationError(int reason){
            if (reason == LicenseCheckerCallback.ERROR_CHECK_IN_PROGRESS)
                Log.d(LOG_TAG, "Licensing Error ERROR_CHECK_IN_PROGRESS" + String.valueOf(reason));
            else if (reason == LicenseCheckerCallback.ERROR_INVALID_PACKAGE_NAME)
                Log.d(LOG_TAG, "Licensing Error ERROR_INVALID_PACKAGE_NAME" + String.valueOf(reason));
            else if (reason == LicenseCheckerCallback.ERROR_INVALID_PUBLIC_KEY)
                Log.d(LOG_TAG, "Licensing Error ERROR_INVALID_PUBLIC_KEY" + String.valueOf(reason));
            else if (reason == LicenseCheckerCallback.ERROR_MISSING_PERMISSION)
                Log.d(LOG_TAG, "Licensing Error ERROR_MISSING_PERMISSION" + String.valueOf(reason));
            else if (reason == LicenseCheckerCallback.ERROR_NON_MATCHING_UID)
                Log.d(LOG_TAG, "Licensing Error ERROR_NON_MATCHING_UID" + String.valueOf(reason));
            else if (reason == LicenseCheckerCallback.ERROR_NOT_MARKET_MANAGED)
                Log.d(LOG_TAG, "Licensing Error ERROR_NOT_MARKET_MANAGED" + String.valueOf(reason));

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }
    }
}
