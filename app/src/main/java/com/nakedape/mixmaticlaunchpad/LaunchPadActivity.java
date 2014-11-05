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
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.Uri;
import android.os.*;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.EditText;
import android.widget.NumberPicker;
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
import java.util.Locale;

// Licensing imports
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.Policy;
import com.google.android.vending.licensing.ServerManagedPolicy;

import javazoom.jl.converter.WaveFile;


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
    public static String SAMPLE_VOLUME = "com.nakedape.mixmaticlaunchpad.volume";
    private static int GET_SAMPLE = 0;
    private static int GET_SLICES = 1;
    private static final int COUNTER_UPDATE = 3;
    private static final int WAV_FILE_WRITE_PROGRESS = 4;
    private static final int SET_PRESSED_TRUE = 5;
    private static final int SET_PRESSED_FALSE = 6;

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
    private boolean isRecording = false;
    private boolean isPlaying = false;
    private boolean dialogCanceled = false;
    private boolean stopCounterThread = false;
    private boolean stopPlaybackThread = false;


    // Counter
    private TextView counterTextView;
    private long counter;
    private int bpm = 120;
    private int timeSignature = 4;
    private long recordingEndTime;
    private Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg){
            switch (msg.what){
                case COUNTER_UPDATE:
                    updateCounterMessage();
                    break;
                case WAV_FILE_WRITE_PROGRESS:
                    progressDialog.setProgress(msg.arg1);
                    break;
            }
        }
    };
    private Handler playHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            View v = findViewById(msg.arg1);
            switch (msg.what){
                case SET_PRESSED_TRUE:
                    v.setPressed(true);
                    break;
                case SET_PRESSED_FALSE:
                    v.setPressed(false);
                    break;
            }
        }
    };
    private void updateCounterMessage(){
        double beatsPerSec = (double)bpm / 60;
        double sec = (double)counter / 1000;
        double beats = sec * beatsPerSec;
        int bars = (int)Math.floor(beats / timeSignature);
        // Subtract one from beats so that counter displays zero when zero
        if (beats == 0)
            beats = -1;
        counterTextView.setText(String.format(Locale.US, "%d BPM  %2d : %.2f", bpm, bars, beats % timeSignature + 1));
    }
    private ArrayList<LaunchEvent> launchEvents = new ArrayList<LaunchEvent>(50);
    private int playEventIndex = 0;
    private ArrayList<Integer> activePads;

    // Listener to turn off touch pads when sound is finished
    private AudioTrack.OnPlaybackPositionUpdateListener samplePlayListener = new AudioTrack.OnPlaybackPositionUpdateListener() {
        @Override
        public void onMarkerReached(AudioTrack track) {
            int id = track.getAudioSessionId();
            View v = findViewById(id);
            if (v != null) {
                v.setPressed(false);
                Sample s = (Sample) samples.get(id);
                s.stop();
                if (isRecording)
                    launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, v.getId()));
            }
        }

        @Override
        public void onPeriodicNotification(AudioTrack track) {

        }
    };

    private Context context;
    private ProgressDialog progressDialog;
    private SparseArray<Sample> samples;
    private File homeDirectory, sampleDirectory;
    private int numTouchPads;
    private AudioManager am;
    private SharedPreferences launchPadprefs; // Stores setting for each launchpad
    private SharedPreferences activityPrefs; // Stores app wide preferences
    private LaunchPadData savedData;
    private boolean savedDataLoaded = false;

    private int selectedSampleID;
    private boolean multiSelect = false;
    private ArrayList<String> selections = new ArrayList<String>();
    private ArrayList<Integer> padIds = new ArrayList<Integer>(24);
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
            if (samples.indexOfKey(selectedSampleID) >= 0) {
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
                    if (samples.indexOfKey(selectedSampleID) >= 0){
                        intent.putExtra(SAMPLE_PATH, sampleDirectory.getAbsolutePath() + "/" + "Mixmatic_Touch_Pad_" + String.valueOf(selectedSampleID) + ".wav");
                        intent.putExtra(COLOR, launchPadprefs.getInt(String.valueOf(selectedSampleID) + COLOR, 0));
                    }
                    startActivityForResult(intent, GET_SAMPLE);
                    return true;
                case R.id.action_loop_mode:
                    if (item.isChecked()) {
                        item.setChecked(false);
                        if (samples.indexOfKey(selectedSampleID) >= 0) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(false);
                            prefEditor.putBoolean(String.valueOf(selectedSampleID) + LOOPMODE, false);
                            prefEditor.apply();
                        }
                    }
                    else {
                        item.setChecked(true);
                        if (samples.indexOfKey(selectedSampleID) >= 0) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(true);
                            prefEditor.putBoolean(String.valueOf(selectedSampleID) + LOOPMODE, true);
                            prefEditor.apply();
                        }
                    }
                    return true;
                case R.id.action_remove_sample:
                    if (samples.indexOfKey(selectedSampleID) >= 0){
                        removeSample(selectedSampleID);
                    }
                    return true;
                case R.id.action_launch_mode_gate:
                    if (samples.indexOfKey(selectedSampleID) >= 0){
                        Sample s = (Sample)samples.get(selectedSampleID);
                        s.setLaunchMode(Sample.LAUNCHMODE_GATE);
                        item.setChecked(true);
                        prefEditor.putInt(String.valueOf(selectedSampleID) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                        prefEditor.apply();
                    }
                    return true;
                case R.id.action_launch_mode_trigger:
                    if (samples.indexOfKey(selectedSampleID) >= 0){
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
                            if (samples.indexOfKey(selectedSampleID) >= 0){
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
                    return true;
                case R.id.action_set_volume:
                    setSampleVolume(selectedSampleID);
                    return true;
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
            if (!isEditMode && !isPlaying && samples.indexOfKey(v.getId()) >= 0) {
                if (!isRecording){ // Start counter thread
                    isRecording = true;
                    counter = recordingEndTime;
                    new Thread(new CounterThread()).start();
                }
                Sample s = samples.get(v.getId());
                switch (event.getAction()) {
                    case MotionEvent.ACTION_UP:
                        switch (s.getLaunchMode()){
                            case Sample.LAUNCHMODE_GATE: // Stop sound and deselect pad
                                s.stop();
                                v.setPressed(false);
                                launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, v.getId()));
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
                            launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, v.getId()));
                            return true;
                        }
                        // Otherwise play the sample
                        else if (s.hasPlayed())
                            s.reset();
                        s.play();
                        v.setPressed(true);
                        launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_START, v.getId()));
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
            if (!sampleDirectory.isDirectory()) // If the home directory doesn't exist, create it
                sampleDirectory.mkdir();
            // Create a new file to contain the new sample
            File sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(data.getIntExtra(TOUCHPAD_ID, 0)) + ".wav");
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
                    sample.setVolume(launchPadprefs.getFloat(String.valueOf(id) + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
                }
                else { // Default properties
                    sample.setLaunchMode(Sample.LAUNCHMODE_GATE);
                    editor.putInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                    editor.putBoolean(String.valueOf(id) + LOOPMODE, false);
                    editor.putFloat(String.valueOf(id) + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume());
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
                File sliceFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(selections.get(i)) + ".wav");
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
                    // Default settings
                    sample.setLaunchMode(Sample.LAUNCHMODE_GATE);
                    editor.putInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                    editor.putBoolean(String.valueOf(id) + LOOPMODE, false);
                    editor.putFloat(String.valueOf(id) + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume());
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
            if (samples.indexOfKey(v.getId()) >= 0) {
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
            if (!(samples.indexOfKey(v.getId()) >= 0)) {
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

    // On create methods
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
        homeDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/Mixmatic");
        if (!homeDirectory.isDirectory()){
            if (!homeDirectory.mkdir())
                Toast.makeText(context, "ERROR: Unable to create storage folder", Toast.LENGTH_SHORT).show();
        }
        sampleDirectory = new File(homeDirectory.getAbsolutePath() + "/Sample_Data");
        if (!sampleDirectory.isDirectory())
            if (!sampleDirectory.mkdir())
                Toast.makeText(context, "ERROR: Unable to create storage folder", Toast.LENGTH_SHORT).show();

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
            padIds = savedData.getPadIds();
            activePads = savedData.getActivePads();
            isRecording = savedData.isRecording();
            isPlaying = savedData.isPlaying();
            if (isPlaying)
                disconnectTouchListeners();
            launchEvents = savedData.getLaunchEvents();
            playEventIndex = savedData.getPlayEventIndex();
            savedDataLoaded = true;
            if (isRecording)
                new Thread(new CounterThread()).start();
            if (isPlaying)
                new Thread(new playBackRecording()).start();
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
    private void setupPadsFromFile() {
        samples = new SparseArray<Sample>(24);
        activePads = new ArrayList<Integer>(24);
        resetRecording();
        TouchPad pad = (TouchPad) findViewById(R.id.touchPad1);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        File sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad2);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) { // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad3);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) { // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad4);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) { // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad5);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad6);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad7);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad8);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad9);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad10);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad11);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad12);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad13);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad14);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad15);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad16);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad17);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) { // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad18);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad19);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad20);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad21);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad22);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad23);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        pad = (TouchPad) findViewById(R.id.touchPad24);
        padIds.add(pad.getId());
        pad.setOnTouchListener(TouchPadTouchListener);
        sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(pad.getId()) + ".wav");
        if (sampleFile.isFile()) {  // If the sample exists, load it
            loadSample(sampleFile.getAbsolutePath(), pad);
            activePads.add(pad.getId());
        }
        savedData.setPadIds(padIds);
    }
    private void setupPadsFromFrag(){
        if (samples == null)
            setupPadsFromFile();
        else {
            Sample sample;
            TouchPad pad = (TouchPad) findViewById(R.id.touchPad1);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad2);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad3);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad4);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad5);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad6);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad7);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad8);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad9);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad10);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad11);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad12);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad13);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad14);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad15);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad16);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad17);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad18);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad19);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad20);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad21);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad22);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad23);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
            pad = (TouchPad) findViewById(R.id.touchPad24);
            pad.setOnTouchListener(TouchPadTouchListener);
            if (samples.indexOfKey(pad.getId()) >= 0) {
                setPadColor(launchPadprefs.getInt(String.valueOf(pad.getId()) + COLOR, 0), pad);
                sample = samples.get(pad.getId());
                sample.setOnPlayFinishedListener(samplePlayListener);
                sample.setLoopMode(sample.getLoopMode());
                pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
            }
        }
    }

    // Methods for managing launchpads
    private void loadSample(String path, TouchPad pad){
        int id = pad.getId();
        pad.setOnTouchListener(TouchPadTouchListener);
        Sample s = new Sample(path, id);
        s.setOnPlayFinishedListener(samplePlayListener);
        samples.put(id, s);
        s.setLoopMode(launchPadprefs.getBoolean(String.valueOf(id) + LOOPMODE, false));
        s.setLaunchMode(launchPadprefs.getInt(String.valueOf(id) + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER));
        s.setVolume(launchPadprefs.getFloat(String.valueOf(id) + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
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
    private void setSampleVolume(final int sampleId){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final NumberPicker numberPicker = new NumberPicker(context);
        numberPicker.setMaxValue((int)(AudioTrack.getMaxVolume() * 100));
        numberPicker.setValue((int)(samples.get(sampleId).getVolume() *100));
        builder.setView(numberPicker);
        builder.setPositiveButton(R.string.set, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                float volume = (float)numberPicker.getValue() / 100;
                samples.get(sampleId).setVolume(volume);
                SharedPreferences.Editor editor = launchPadprefs.edit();
                editor.putFloat(String.valueOf(sampleId) + SAMPLE_VOLUME, volume);
                editor.apply();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void removeSample(final int sampleId){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(getString(R.string.warning_remove_sample));
        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Sample s = samples.get(sampleId);
                // Delete the sample file
                File f = new File(s.getPath());
                f.delete();
                // Remove the sample from the list of active samples
                samples.remove(sampleId);
                // Set the launchpad background to empty
                View v = findViewById(sampleId);
                v.setBackgroundResource(R.drawable.launch_pad_empty);
                // Remove settings from shared preferences
                SharedPreferences.Editor editor = launchPadprefs.edit();
                editor.remove(String.valueOf(sampleId) + SAMPLE_VOLUME);
                editor.remove(String.valueOf(sampleId) + LAUNCHMODE);
                editor.remove(String.valueOf(sampleId) + LOOPMODE);
                editor.remove(String.valueOf(sampleId) + COLOR);
                editor.apply();
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

    private void playMix(){
        disconnectTouchListeners();
        isRecording = false;
        new Thread(new playBackRecording()).start();
    }
    private void disconnectTouchListeners(){
        for (Integer i : padIds){
            TouchPad pad = (TouchPad)findViewById(i);
            pad.setOnTouchListener(null);
            pad.setClickable(false);
        }
    }
    private void reconnectTouchListeners(){
        for (Integer i : padIds) {
            TouchPad pad = (TouchPad) findViewById(i);
            pad.setOnTouchListener(TouchPadTouchListener);
            pad.setClickable(true);
        }
    }
    private class playBackRecording implements Runnable {
        @Override
        public void run() {
            isRecording = false;
            if (!isPlaying) {
                playEventIndex = 0;
                counter = 0;
            }
            isPlaying = true;
            new Thread(new CounterThread()).start();
            for (int i = playEventIndex; i < launchEvents.size() && isPlaying && !stopPlaybackThread; i++) {
                LaunchEvent event = launchEvents.get(i);
                playEventIndex = i;
                while (event.timeStamp > counter)
                {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if (event.eventType.equals(LaunchEvent.PLAY_START)) {
                    samples.get(event.getSampleId()).play();
                    Message message = playHandler.obtainMessage(SET_PRESSED_TRUE);
                    message.arg1 = event.getSampleId();
                    message.sendToTarget();
                }
                else {
                    samples.get(event.getSampleId()).stop();
                    Message message = playHandler.obtainMessage(SET_PRESSED_FALSE);
                    message.arg1 = event.getSampleId();
                    message.sendToTarget();
                }
            }
            if (!stopPlaybackThread) {
                isPlaying = false;
                reconnectTouchListeners();
            }
        }
    }
    private void resetRecording(){
        isRecording = false;
        counter = 0;
        recordingEndTime = 0;
        launchEvents = new ArrayList<LaunchEvent>(50);
        updateCounterMessage();
    }
    private void stopPlayBack(){
        for (Integer i : activePads) {
            Sample s = samples.get(i);
            isPlaying = false;
            if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                s.stop();
                if (isRecording)
                    launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, i));
            }
        }
        reconnectTouchListeners();
        isRecording = false;
    }
    public void Stop(View v){
        stopPlayBack();
    }

    // File save/export methods
    private void promptForFilename(){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter a name");
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.enter_text_dialog, null);
        builder.setView(view);
        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                EditText text = (EditText) view.findViewById(R.id.dialogText);
                String fileName = text.getText().toString();
                if (fileName.toLowerCase().endsWith(".wav"))
                    fileName = fileName.substring(0, fileName.length() - 4);
                SaveToFile(fileName);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void SaveToFile(final String fileName){
        progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(getString(R.string.file_export_msg) + fileName + ".wav");
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelable(true);
        progressDialog.setCanceledOnTouchOutside(false);
        dialogCanceled = false;
        progressDialog.setMax(launchEvents.size());
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                progressDialog.dismiss();
                dialogCanceled = true;
            }
        });
        progressDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                WriteWavFile(fileName);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.dismiss();
                        if (!dialogCanceled)
                            Toast.makeText(context, "Saved to " + homeDirectory + "/" + fileName + ".wav", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }
    private void selectEncoder(){
        if (launchEvents.size() > 1) {
            ArrayList<String> codecs = new ArrayList<String>();
            codecs.add("wav");
            final String[] fileTypes;
            for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
                MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
                if (codecInfo.isEncoder()) {
                    String[] types = codecInfo.getSupportedTypes();
                    for (String s : types) {
                        if (s.startsWith("audio"))
                            if (codecs.indexOf(s.substring(6)) < 0)
                                codecs.add(s.substring(6));
                        Log.d(LOG_TAG, codecInfo.getName() + " supported type: " + s);
                    }
                }
            }
            fileTypes = new String[codecs.size()];
            for (int i = 0; i < codecs.size(); i++)
                fileTypes[i] = codecs.get(i);
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Select audio file format");
            builder.setItems(fileTypes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    SaveToFile(fileTypes[which]);
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        else
        {
            Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show();
        }
    }
    private void EncodeAudio(String fileType){
        if (!fileType.equals("wav")) {
            File encodedFile = new File(homeDirectory + "/saved.aac");
            if (encodedFile.isFile())
                encodedFile.delete();
            FileOutputStream fileWriter;
            long TIMEOUT_US = 10000;
            ByteBuffer[] codecInputBuffers;
            ByteBuffer[] codecOutputBuffers;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            InputStream wavStream;
            MediaFormat format = new MediaFormat();
            format.setString(MediaFormat.KEY_MIME, "audio/" + fileType);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128);
            format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, 2);
            format.setInteger(MediaFormat.KEY_SAMPLE_RATE, 44100);
            try {
                fileWriter = new FileOutputStream(encodedFile);
                wavStream = new BufferedInputStream(new FileInputStream(new File(sampleDirectory, "saved.wav")));
                wavStream.skip(44);
                MediaCodec codec = MediaCodec.createEncoderByType("audio/" + fileType);
                codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                codec.start();
                codecInputBuffers = codec.getInputBuffers();
                codecOutputBuffers = codec.getOutputBuffers();
                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;
                do {
                    // Load input buffer
                    int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    Log.d(LOG_TAG, "inputBufIndex = " + String.valueOf(inputBufIndex));
                    if (inputBufIndex >= 0) {
                        ByteBuffer inputBuffer = codecInputBuffers[inputBufIndex];
                        inputBuffer.clear();
                        byte[] inputBytes = new byte[inputBuffer.capacity()];
                        int len = wavStream.read(inputBytes);
                        inputBuffer.put(inputBytes);
                        codec.queueInputBuffer(inputBufIndex, 0, len, 0, 0);
                        Log.d(LOG_TAG, "codec read bytes " + String.valueOf(inputBytes.length));
                    }
                    else
                        sawInputEOS = true;
                    // Process output buffers
                    int outputBufIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                    if (outputBufIndex >=0){
                        ByteBuffer outputBuffer = codecOutputBuffers[outputBufIndex];
                        byte[] outputBytes = new byte[info.size];
                        outputBuffer.position(0);
                        outputBuffer.get(outputBytes);
                        fileWriter.write(outputBytes);
                        codec.releaseOutputBuffer(outputBufIndex, false);
                        Log.d(LOG_TAG, "codec wrote bytes " + String.valueOf(outputBytes.length));
                    }
                    if ((info.flags == MediaCodec.BUFFER_FLAG_END_OF_STREAM) || (info.flags == MediaCodec.BUFFER_FLAG_SYNC_FRAME + MediaCodec.BUFFER_FLAG_END_OF_STREAM))
                        sawOutputEOS = true;
                } while (!sawInputEOS && !sawOutputEOS && !dialogCanceled);
                codec.stop();
                fileWriter.close();

            } catch (IOException e) {e.printStackTrace();}
        }
    }
    private void WriteWavFile(String fileName){
        // Wave file to write
        File waveFileTemp = new File(homeDirectory, fileName + ".wav");
        if (waveFileTemp.isFile())
            waveFileTemp.delete();
        WaveFile waveFile = new WaveFile();
        waveFile.OpenForWrite(waveFileTemp.getAbsolutePath(), 44100, (short)16, (short)2);
        // Array holds all the samples that are being played at a given time
        ArrayList<String> playingSamples = new ArrayList<String>(24);
        // Array to contain offsets for samples that play longer than the next event.  Stored as strings
        SparseArray<String> playingSampleOffsets = new SparseArray<String>(24);
        int bytesWritten = 0; // Total bytes written, also used to track time
        int i = 0;
        int length = 0;
        do {
            LaunchEvent event = launchEvents.get(i);
            if (event.eventType.equals(LaunchEvent.PLAY_START))
                playingSamples.add(String.valueOf(event.getSampleId()));
            else {
                playingSamples.remove(String.valueOf(event.getSampleId()));
                playingSampleOffsets.put(event.getSampleId(), String.valueOf(0));
            }
            // Figure out how much can be written before the next start/stop event
            if (i < launchEvents.size() - 1) {
                length = (int)(launchEvents.get(i + 1).timeStamp / 1000 * 44100) * 16 / 4 - bytesWritten;
            }
            else
                length = 0;
            // short array to hold that data to be written before the next event
            short[] shortData = new short[length / 2];
            // For each sample that is playing load its data and add it to the array to be written
            for (String idString : playingSamples){
                // byte array to hold that data to be written before the next event for this sample
                int id = Integer.parseInt(idString);
                byte[] byteData = new byte[length];
                byte [] sampleBytes = samples.get(id).getAudioBytes();
                int bytesCopied = 0;
                int offset = Integer.parseInt(playingSampleOffsets.get(id, "0"));
                if (offset > sampleBytes.length)
                    offset -= sampleBytes.length;
                if (sampleBytes.length - offset <= byteData.length) {
                    // Fill the byte array with copies of the sample until it is full
                    do {
                        ByteBuffer.wrap(sampleBytes, offset, sampleBytes.length - offset).get(byteData, bytesCopied, sampleBytes.length - offset);
                        bytesCopied += sampleBytes.length - offset;
                        offset = 0;
                    } while (bytesCopied + sampleBytes.length <= byteData.length);
                    ByteBuffer.wrap(sampleBytes, 0, byteData.length - bytesCopied).get(byteData, bytesCopied, byteData.length - bytesCopied);
                    playingSampleOffsets.put(id, String.valueOf((byteData.length - bytesCopied)));
                }
                else{
                    ByteBuffer.wrap(sampleBytes, offset, byteData.length).get(byteData);
                    playingSampleOffsets.put(Integer.parseInt(idString), String.valueOf(sampleBytes.length + offset + byteData.length));
                }
                // Convert byte data to shorts
                short[] shorts = new short[byteData.length / 2];
                ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                // Add the sample short data to the total data to be written to file
                float [] mixedBuffer = new float[shorts.length];
                float max = 0;
                float volume = samples.get(id).getVolume();
                for (int j = 0; j < shorts.length; j++){
                    mixedBuffer[j] = shortData[j] + shorts[j] * volume;
                    max = Math.max(mixedBuffer[j], max);
                }
                for (int j = 0; j < mixedBuffer.length; j++)
                    shortData[j] = (short) (mixedBuffer[j] * 32767 / max);
            }
            waveFile.WriteData(shortData, shortData.length);
            Message m = mHandler.obtainMessage(WAV_FILE_WRITE_PROGRESS);
            m.arg1 = i;
            m.sendToTarget();
            i++;
            bytesWritten += length;
        } while (i < launchEvents.size() && !dialogCanceled);
        waveFile.Close();
    }

    // Activity lifecycle
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.touch_pad, menu);
        return true;
    }
    @Override
    public boolean onMenuOpened(int featureId, Menu menu){
        stopPlayBack();
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            isRecording = false;
            Intent intent = new Intent(EditPreferencesActivity.LAUNCHPAD_PREFS, null, context, EditPreferencesActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_edit_mode) {
            isEditMode = true;
            isRecording = false;
            View v = findViewById(R.id.touchPad1);
            v.callOnClick();
        }
        else if (id == R.id.action_play){
            playMix();
        }
        else if (id == R.id.action_write_wav){
            stopPlayBack();
            promptForFilename();
        }
        else if (id == R.id.action_reset) {
            resetRecording();
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    public void onResume() {
        super.onResume();
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (ViewConfiguration.get(context).hasPermanentMenuKey()) {
                if (getActionBar() != null)
                    getActionBar().hide();
            }
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
                progressDialog.cancel();
        if (mChecker != null)
            mChecker.onDestroy();
        stopCounterThread = true;
        stopPlaybackThread = true;
        if (isFinishing()){
            // Release audiotrack resources
            for (Integer i : activePads) {
                Sample s = samples.get(i);
                isPlaying = false;
                if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    s.stop();
                }
                s.audioTrack.release();
            }
        }
        else {
            savedData.setSamples(samples);
            savedData.setCounter(counter);
            savedData.setEditMode(isEditMode);
            savedData.setActivePads(activePads);
            savedData.setPlaying(isPlaying);
            savedData.setRecording(isRecording);
            savedData.setLaunchEvents(launchEvents);
            savedData.setPlayEventIndex(playEventIndex);
        }
    }


    // Counter thread keeps track of time
    private class CounterThread implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            long startMillis = SystemClock.elapsedRealtime() - counter;
            do {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {e.printStackTrace();}
                counter = SystemClock.elapsedRealtime() - startMillis;
                Message msg = mHandler.obtainMessage(COUNTER_UPDATE);
                msg.sendToTarget();
                if (isRecording)
                    recordingEndTime = counter;
            } while ((isRecording || isPlaying) && !stopCounterThread);
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
        private float volume = 0.5f * AudioTrack.getMaxVolume();
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
                /*if (hasPlayed()) {
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.reloadStaticData();
                    played = false;
                }
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
                    loadAudioTrack();
                */
                audioTrack.setLoopPoints(0, sampleByteLength / 4, -1);
                audioTrack.setNotificationMarkerPosition(0);
                audioTrack.setPlaybackPositionUpdateListener(null);
            }
            else {
                loopMode = 0;
                /*if (hasPlayed()) {
                    audioTrack.stop();
                    audioTrack.flush();
                    audioTrack.reloadStaticData();
                    played = false;
                }*/
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
        public byte[] getAudioBytes(){
            InputStream stream = null;
            byte[] bytes = null;
            try {
                stream = new BufferedInputStream(new FileInputStream(sampleFile));
                stream.skip(44);
                bytes = new byte[sampleByteLength];
                stream.read(bytes);
            } catch (IOException e) {e.printStackTrace();}
            return bytes;
        }
        public void play(){
            played = true;
            resetMarker();
            if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                audioTrack.play();
            else if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING){
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
        public void setVolume(float volume){
            this.volume = volume;
            loadAudioTrack();
        }
        public float getVolume() {return volume;}
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
            audioTrack.setStereoVolume(volume, volume);
            if (listener != null) {
                audioTrack.setPlaybackPositionUpdateListener(listener);
                resetMarker();
            }

            if (loop) {
                setLoopMode(true);
            }
        }
    }

    public class LaunchEvent{
        public static final String PLAY_START = "com.nakedape.mixmaticlaunchpad.playstart";
        public static final String PLAY_STOP = "com.nakedape.mixmaticlaunchpad.playstop";
        private double timeStamp;
        private String eventType;
        private int sampleId;

        public LaunchEvent(double timeStamp, String eventType, int sampleId){
            this.timeStamp = timeStamp;
            this.eventType = eventType;
            this.sampleId = sampleId;
        }

        public double getTimeStamp() {return timeStamp;}
        public String getEventType() {return eventType;}
        public int getSampleId() {return sampleId;}
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
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setCancelable(false);
                        builder.setMessage(R.string.unlicensed_retry_message);
                        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mChecker.checkAccess(mLicenseCheckerCallback);
                            }
                        });
                        builder.setNegativeButton(getString(R.string.no), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                finish();
                            }
                        });
                        AlertDialog dialog = builder.create();
                        dialog.show();
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
                        builder.setMessage(R.string.unlicensed_message);
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
                                finish();
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
