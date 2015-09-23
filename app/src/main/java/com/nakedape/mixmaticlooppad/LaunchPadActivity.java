package com.nakedape.mixmaticlooppad;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.util.SparseArray;
import android.view.ActionMode;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

// Licensing imports
import com.amazon.device.ads.Ad;
import com.amazon.device.ads.AdError;
import com.amazon.device.ads.AdProperties;
import com.amazon.device.ads.AdRegistration;
import com.amazon.device.ads.DefaultAdListener;
import com.amazon.device.ads.InterstitialAd;
import com.facebook.FacebookSdk;
import com.facebook.appevents.AppEventsLogger;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.vending.licensing.AESObfuscator;
import com.google.android.vending.licensing.LicenseChecker;
import com.google.android.vending.licensing.LicenseCheckerCallback;
import com.google.android.vending.licensing.Policy;
import com.google.android.vending.licensing.ServerManagedPolicy;

import javazoom.jl.converter.WaveFile;


public class LaunchPadActivity extends Activity {

    private static final String LOG_TAG = "LaunchPadActivity";

    public static String TOUCHPAD_ID = "com.nakedape.mixmaticlooppad.touchpadid";
    public static String TOUCHPAD_ID_ARRAY = "com.nakedape.mixmaticlooppad.touchpadidarray";
    public static String SAMPLE_PATH = "com.nakedape.mixmaticlooppad.samplepath";
    public static String COLOR = "com.nakedape.mixmaticlooppad.color";
    public static String LOOPMODE = "com.nakedape.mixmaticlooppad.loop";
    public static String LAUNCHMODE = "com.nakedape.mixmaticlooppad.launchmode";
    public static String NUM_SLICES = "com.nakedape.mixmaticlooppad.numslices";
    public static String SLICE_PATHS = "com.nakedape.mixmaticlooppad.slicepaths";
    public static String SAMPLE_VOLUME = "com.nakedape.mixmaticlooppad.volume";
    private static int GET_SAMPLE = 0;
    private static int GET_SLICES = 1;

    // Licensing
    private static final String BASE_64_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjcQ7YSSmv5GSS3FrQ801508P/r5laGtv7GBG2Ax9ql6ZAJZI6UPrJIvN9gXjoRBnHOIphIg9HycJRxBwGfgcpEQ3F47uWJ/UvmPeQ3cVffFKIb/cAUqCS4puEtcDL2yDXoKjagsJNBjbRWz6tqDvzH5BtvdYoy4QUf8NqH8wd3/2R/m3PAVIr+lRlUAc1Dj2y40uOEdluDW+i9kbkMD8vrLKr+DGnB7JrKFAPaqxBNTeogv0vGNOWwJd3Tgx7VDm825Op/vyG9VQSM7W53TsyJE8NdwP8Q59B/WRlcsr+tHCyoQcjscrgVegiOyME1DfEUrQk/SPzr5AlCqa2AZ//wIDAQAB";
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
    private boolean isMicRecording = false;
    private boolean dialogCanceled = false;
    private boolean stopCounterThread = false;
    private boolean stopPlaybackThread = false;


    // Counter
    private TextView counterTextView;
    private long counter;
    private int bpm = 120;
    private int timeSignature = 4;
    private long recordingEndTime;
    private ArrayList<LaunchEvent> launchEvents = new ArrayList<LaunchEvent>(50);
    private int playEventIndex = 0;
    private ArrayList<LaunchEvent> loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
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
    private RelativeLayout rootLayout;
    private ProgressDialog progressDialog;
    private SparseArray<Sample> samples;
    private File homeDirectory, sampleDirectory;
    private int numTouchPads;
    private AudioManager am;
    private SharedPreferences launchPadprefs; // Stores setting for each launchpad
    private SharedPreferences activityPrefs; // Stores app wide preferences
    private LaunchPadData savedData;
    private View listItemPauseButton;

    private int selectedSampleID;
    private boolean multiSelect = false;
    private ArrayList<String> selections = new ArrayList<String>();
    private int[] touchPadIds = {R.id.touchPad1, R.id.touchPad2, R.id.touchPad3, R.id.touchPad4, R.id.touchPad5, R.id.touchPad6,
            R.id.touchPad7, R.id.touchPad8, R.id.touchPad9, R.id.touchPad10, R.id.touchPad11, R.id.touchPad12,
            R.id.touchPad13, R.id.touchPad14, R.id.touchPad15, R.id.touchPad16, R.id.touchPad17, R.id.touchPad18,
            R.id.touchPad19, R.id.touchPad20, R.id.touchPad21, R.id.touchPad22, R.id.touchPad23, R.id.touchPad24};
    private ActionMode launchPadActionMode;
    private Menu actionBarMenu;
    private boolean isSampleLibraryShowing = false;
    private SampleListAdapter sampleListAdapter;
    private int sampleLibraryIndex = -1;
    private MediaPlayer samplePlayer;
    private Runtime runtime;
    // Ads
    private InterstitialAd amznInterstitialAd;
    private boolean reloadAds;
    private com.google.android.gms.ads.InterstitialAd adMobInterstitial;
    private int adReloadAttempts = 0;

    // Activity overrides
    // On create methods
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        savedData = (LaunchPadData) fm.findFragmentByTag("data");
        if (savedData == null) {
            setContentView(R.layout.loading_screen);
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
        }
        else {
            loadInBackground();
        }
    }
    private void loadInBackground(){
        // Prepare shared preferences
        launchPadprefs = getPreferences(MODE_PRIVATE);
        PreferenceManager.setDefaultValues(this, R.xml.sample_edit_preferences, true);
        activityPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        // Instance of runtime to check memory use
        runtime = Runtime.getRuntime();

        // Initialize Facebook SDK
        FacebookSdk.sdkInitialize(getApplicationContext());

        // Set up audio control
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        // Prepare stoarage directories
        if (Utils.isExternalStorageWritable()){
            sampleDirectory = new File(getExternalFilesDir(null), "Samples");
            if (!sampleDirectory.exists())
                if (!sampleDirectory.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
        } else {
            sampleDirectory = new File(getFilesDir(), "Samples");
            if (!sampleDirectory.exists())
                if (!sampleDirectory.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
        }

        // Copy files over from directory used in previous app versions and delete the directories
        cleanUpStorageDirs();

        // Instantiate sampleListAdapter
        sampleListAdapter = new SampleListAdapter(context, R.layout.sample_list_item);

        // Load UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                loadUi();
            }
        });
    }
    private void loadUi(){
        setContentView(R.layout.activity_launch_pad);

        rootLayout = (RelativeLayout)findViewById(R.id.launch_pad_root_layout);
        LinearLayout library = (LinearLayout)findViewById(R.id.sample_library);
        library.setOnDragListener(new LibraryDragEventListener());

        // Setup ads
        AdRegistration.setAppKey("83c8d7d1e7ec460fbf2f8f37f88c095a");
        // Ad testing flags
        //AdRegistration.enableTesting(true);
        AdRegistration.enableLogging(true);
        // Create the interstitial.
        amznInterstitialAd = new InterstitialAd(this);
        // Set the listener to use the callbacks below.
        amznInterstitialAd.setListener(new AmazonAdListener());
        amznInterstitialAd.loadAd();

        // Setup counter
        ActionBar actionBar = getActionBar();
        if (actionBar != null){
            actionBar.setCustomView(R.layout.counter_bar);
            counterTextView = (TextView)actionBar.getCustomView().findViewById(R.id.textViewCounter);
            bpm = activityPrefs.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
            timeSignature = Integer.parseInt(activityPrefs.getString(LaunchPadPreferencesFragment.PREF_TIME_SIG, "4"));
            updateCounterMessage();
            actionBar.setDisplayShowCustomEnabled(true);
        } else {
            findViewById(R.id.counter_bar).setVisibility(View.VISIBLE);
            counterTextView = (TextView) findViewById(R.id.textViewCounter);
            bpm = activityPrefs.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
            timeSignature = Integer.parseInt(activityPrefs.getString(LaunchPadPreferencesFragment.PREF_TIME_SIG, "4"));
            updateCounterMessage();
        }

        if (savedData != null){
            samples = savedData.getSamples();
            counter = savedData.getCounter();
            double sec = (double)counter / 1000;
            int min = (int)Math.floor(sec / 60);
            counterTextView.setText(String.format(Locale.US, "%d BPM  %2d : %.2f", bpm, min, sec % 60));
            isEditMode = savedData.isEditMode();
            activePads = savedData.getActivePads();
            isRecording = savedData.isRecording();
            isPlaying = savedData.isPlaying();
            recordingEndTime = savedData.getRecordingEndTime();
            if (isPlaying)
                disconnectTouchListeners();
            launchEvents = savedData.getLaunchEvents();
            playEventIndex = savedData.getPlayEventIndex();
            if (isRecording) {
                View v = findViewById(R.id.button_play);
                v.setBackgroundResource(R.drawable.button_pause);
                new Thread(new CounterThread()).start();
            }
            if (isPlaying) {
                View v = findViewById(R.id.button_play);
                v.setBackgroundResource(R.drawable.button_pause);
                new Thread(new playBackRecording()).start();
            }
            sampleListAdapter.sampleFiles = savedData.sampleFiles;
            sampleListAdapter.sampleLengths = savedData.sampleLengths;
            // Setup touch pads from retained fragment
            setupPadsFromFrag();
        }
        else{
            FragmentManager fm = getFragmentManager();
            savedData = new LaunchPadData();
            fm.beginTransaction().add(savedData, "data").commit();
            // Setup touch pads from files
            new Thread(new Runnable() {
                @Override
                public void run() {
                    setupPadsFromFile();
                    // Prepare Sample Library list adapter
                    File[] sampleFiles = sampleDirectory.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getName().endsWith(".wav");
                        }
                    });
                    //sampleListAdapter.addAll(sampleFiles);
                }
            }).start();
        }
    }
    private void cleanUpStorageDirs(){
        File oldHomeDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/Mixmatic");
        if (oldHomeDirectory.exists() && oldHomeDirectory.isDirectory()) {
            File oldSampleDirectory = new File(oldHomeDirectory.getAbsolutePath() + "/Sample_Data");
            if (oldSampleDirectory.exists() && oldSampleDirectory.isDirectory()){
                File[] files = oldSampleDirectory.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.getPath().endsWith("wav");
                    }
                });
                int padInt = 1;
                SharedPreferences.Editor editor = launchPadprefs.edit();
                for (File file : files){
                    String fileName = file.getName();
                    if (fileName.contains("Mixmatic_Touch_Pad_")) {
                        try {
                            String padNumber = String.valueOf(padInt++);
                            File newSampleFile = new File(sampleDirectory, "Sample" + padNumber + ".wav");
                            Utils.CopyFile(file, newSampleFile);
                            fileName = fileName.replace("Mixmatic_Touch_Pad_", "");
                            fileName = fileName.replace(".wav", "");
                            editor.putString(padNumber + SAMPLE_PATH, newSampleFile.getAbsolutePath());
                            editor.putInt(padNumber + LAUNCHMODE, launchPadprefs.getInt(fileName + LAUNCHMODE, Sample.LAUNCHMODE_GATE));
                            editor.remove(fileName + LAUNCHMODE);
                            editor.putBoolean(padNumber + LOOPMODE, launchPadprefs.getBoolean(fileName + LOOPMODE, false));
                            editor.remove(fileName + LOOPMODE);
                            editor.putFloat(padNumber + SAMPLE_VOLUME, launchPadprefs.getFloat(fileName + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
                            editor.remove(fileName + SAMPLE_VOLUME);
                            editor.putInt(padNumber + COLOR, launchPadprefs.getInt(fileName + COLOR, 0));
                            editor.remove(fileName + COLOR);
                            file.delete();
                        } catch (IOException e) {e.printStackTrace(); }
                    } else {
                        try {
                            Utils.CopyFile(file, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), file.getName()));
                            file.delete();
                        } catch (IOException e) {e.printStackTrace();}
                    }
                }
                editor.apply();
                oldSampleDirectory.delete();
            }
            // Move or delete files in oldHomeDirectory
            File[] miscFiles = oldHomeDirectory.listFiles();
            for (File file : miscFiles){
                if (file.getName().endsWith(".wav")) {
                    try {
                        Utils.CopyFile(file, new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), file.getName()));
                        file.delete();
                    } catch (IOException e) {e.printStackTrace(); }
                } else {
                    file.delete();
                }
            }
            oldHomeDirectory.delete();
        }
    }
    private void setupPadsFromFile() {
        samples = new SparseArray<Sample>(24);
        activePads = new ArrayList<Integer>(24);
        int padInt = 1;
        for (int id : touchPadIds){
            final TouchPad pad = (TouchPad)findViewById(id);
            pad.setTag(String.valueOf(padInt++));
            pad.setOnTouchListener(TouchPadTouchListener);
            pad.setOnDragListener(new PadDragEventListener());
            String path = launchPadprefs.getString(pad.getTag().toString() + SAMPLE_PATH, null);
            if (path != null) {
                File sampleFile = new File(path);
                if (sampleFile.isFile()) {
                    // Configure pad settings
                    pad.setOnTouchListener(TouchPadTouchListener);
                    Sample s = new Sample(path, id);
                    if (s.isReady()) {
                        s.setOnPlayFinishedListener(samplePlayListener);
                        samples.put(id, s);
                        String padNumber = (String) pad.getTag();
                        s.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, false));
                        s.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER));
                        s.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
                        final int color = launchPadprefs.getInt(padNumber + COLOR, 0);
                        activePads.add(pad.getId());
                        // Animate the loading of the pad
                        rootLayout.post(new Runnable() {
                            @Override
                            public void run() {
                                setPadColor(color, pad);
                                // Start the animation
                                AnimatorSet set = new AnimatorSet();
                                ObjectAnimator popIn = ObjectAnimator.ofFloat(pad, "ScaleX", 0f, 1f);
                                set.setInterpolator(new AnticipateOvershootInterpolator());
                                set.play(popIn);
                                set.setTarget(pad);
                                set.start();
                            }
                        });
                    } else {
                        notifySampleLoadError(s);
                    }
                }
            } else {
                pad.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        emptyPadLongClick(v);
                        return true;
                    }
                });
            }
        }
    }
    private void setupPadsFromFrag(){
        if (samples == null)
            // Setup touch pads from files
            new Thread(new Runnable() {
                @Override
                public void run() {
                    setupPadsFromFile();
                }
            }).start();
        else {
            int padIndex = 1;
            for (int id : touchPadIds) {
                Sample sample;
                String padNumber = String.valueOf(padIndex++);
                TouchPad pad = (TouchPad) findViewById(id);
                pad.setTag(padNumber);
                pad.setOnTouchListener(TouchPadTouchListener);
                pad.setOnDragListener(new PadDragEventListener());
                if (samples.indexOfKey(pad.getId()) >= 0) {
                    setPadColor(launchPadprefs.getInt(padNumber + COLOR, 0), pad);
                    sample = samples.get(pad.getId());
                    sample.setOnPlayFinishedListener(samplePlayListener);
                    sample.setLoopMode(sample.getLoopMode());
                    pad.setPressed(sample.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING);
                }
                else {
                    pad.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            emptyPadLongClick(v);
                            return true;
                        }
                    });
                }
            }
        }
    }
    @Override
    public void onPause(){
        super.onPause();
        reloadAds = false;
        // Logs 'app deactivate' App Event to Facebook.
        AppEventsLogger.deactivateApp(this);

        if (samplePlayer != null){
            if (samplePlayer.isPlaying()) {
                samplePlayer.stop();
                if (listItemPauseButton != null)
                    listItemPauseButton.setSelected(false);
            }
            samplePlayer.release();
            samplePlayer = null;
        }
    }
    @Override
    public void onResume() {
        super.onResume();
        // Logs 'install' and 'app activate' App Events to Facebook.
        AppEventsLogger.activateApp(this);
        // Load new amazon interstitial ad
        if (amznInterstitialAd != null && !amznInterstitialAd.isLoading() && !amznInterstitialAd.isReady())
            amznInterstitialAd.loadAd();
        if (rootLayout != null) {
            int newBpm = activityPrefs.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
            int newTimeSignature = Integer.parseInt(activityPrefs.getString(LaunchPadPreferencesFragment.PREF_TIME_SIG, "4"));

            if (newBpm != bpm || newTimeSignature != timeSignature) {
                counter = 0;
                bpm = newBpm;
                timeSignature = newTimeSignature;
            }
            if (counterTextView != null)
                updateCounterMessage();
        }
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        reloadAds = false;
        if (progressDialog != null)
            if (progressDialog.isShowing())
                progressDialog.cancel();
        if (mChecker != null)
            mChecker.onDestroy();
        stopCounterThread = true;
        stopPlaybackThread = true;
        if (rootLayout != null) {
            if (isFinishing()) {
                // Release audiotrack resources
                for (Integer i : activePads) {
                    Sample s = samples.get(i);
                    isPlaying = false;
                    if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        s.stop();
                    }
                    s.audioTrack.release();
                }
            } else {
                savedData.setSamples(samples);
                savedData.setCounter(counter);
                savedData.setEditMode(isEditMode);
                savedData.setActivePads(activePads);
                savedData.setPlaying(isPlaying);
                savedData.setRecording(isRecording);
                savedData.setRecordingEndTime(recordingEndTime);
                savedData.setLaunchEvents(launchEvents);
                savedData.setPlayEventIndex(playEventIndex);
                savedData.sampleFiles = sampleListAdapter.sampleFiles;
                savedData.sampleLengths = sampleListAdapter.sampleLengths;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.touch_pad, menu);
        actionBarMenu = menu;
        if (isPlaying || isRecording){
            MenuItem playButton = menu.findItem(R.id.action_play);
            playButton.setIcon(R.drawable.ic_action_av_pause);
        }
        return true;
    }
    @Override
    public boolean onMenuOpened(int featureId, Menu menu){
        stopPlayBack();
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case R.id.action_settings:
                isRecording = false;
                Intent intent = new Intent(EditPreferencesActivity.LAUNCHPAD_PREFS, null, context, EditPreferencesActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_edit_mode:
                isRecording = false;
                gotoEditMode();
                View v = findViewById(activePads.get(0));
                v.callOnClick();
                return true;
            case R.id.action_play:
                if (isPlaying || isRecording) {
                    item.setIcon(R.drawable.ic_action_av_play_arrow);
                    stopPlayBack();
                }
                else if (launchEvents.size() > 0 && playEventIndex < launchEvents.size()){
                    item.setIcon(R.drawable.ic_action_av_pause);
                    playMix();
                } else if (launchEvents.size() > 0){
                    RewindButtonClick(null);
                    item.setIcon(R.drawable.ic_action_av_pause);
                    playMix();
                }
                return true;
            case R.id.action_rewind:
                RewindButtonClick(null);
                return true;
            case R.id.action_fast_forward:
                FastForwardButtonClick(null);
                return true;
            case R.id.action_write_wav:
                stopPlayBack();
                promptForFilename();
                return true;
            case R.id.action_reset:
                resetRecording();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == GET_SAMPLE && resultCode == RESULT_OK) {
            String path = data.getData().getPath();
            // Create a new file to contain the new sample
            File sampleFile = new File(path);
            if (sampleFile.exists() && sampleListAdapter != null)
                sampleListAdapter.add(sampleFile);
            // If the file exists and a pad was selected, prepare touchpad
            int id = data.getIntExtra(TOUCHPAD_ID, -1);
            TouchPad t = (TouchPad) findViewById(id);
            if (sampleFile.exists() && t != null) {
                selectedSampleID = id;
                Sample sample = new Sample(sampleFile.getAbsolutePath(), id);
                if (sample.isReady()) {
                    sample.setOnPlayFinishedListener(samplePlayListener);
                    String padNumber = (String) t.getTag();
                    // Set sample properties and save to shared preferences
                    SharedPreferences.Editor editor = launchPadprefs.edit();
                    sample.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE));
                    sample.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, false));
                    sample.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
                    samples.put(id, sample);
                    if (!activePads.contains((Integer) id)) activePads.add(id);
                    switch (data.getIntExtra(COLOR, 0)) { // Set and save color
                        case 0:
                            t.setBackgroundResource(R.drawable.launch_pad_blue);
                            editor.putInt(padNumber + COLOR, 0);
                            break;
                        case 1:
                            t.setBackgroundResource(R.drawable.launch_pad_red);
                            editor.putInt(padNumber + COLOR, 1);
                            break;
                        case 2:
                            t.setBackgroundResource(R.drawable.launch_pad_green);
                            editor.putInt(padNumber + COLOR, 2);
                            break;
                        case 3:
                            t.setBackgroundResource(R.drawable.launch_pad_orange);
                            editor.putInt(padNumber + COLOR, 3);
                            break;
                    }
                    editor.apply();
                    // Show the action bar for pads with loaded samples
                    if (launchPadActionMode == null)
                        launchPadActionMode = startActionMode(launchPadActionModeCallback);
                } else {
                    notifySampleLoadError(sample);
                }
            }
        }
    }

    // Amazon ad listener
    private class AmazonAdListener extends DefaultAdListener {
        @Override
        public void onAdLoaded(Ad ad, AdProperties adProperties)
        {
            if (ad == LaunchPadActivity.this.amznInterstitialAd)
            {
                //Log.d(LOG_TAG, "Amazon interstitial loaded");
                adReloadAttempts = 0;
            }
        }

        @Override
        public void onAdFailedToLoad(Ad ad, AdError error)
        {
            // Call backup ad network.
            Log.e(LOG_TAG, "Amazon interstitial failed to load");
            Log.e(LOG_TAG, error.getMessage());
            switch (error.getCode()){
                case NETWORK_ERROR:
                case NETWORK_TIMEOUT:
                case NO_FILL:
                    if (adReloadAttempts < 5){
                        reloadAds = true;
                        adReloadAttempts++;
                        new Thread(new ReloadAd()).start();
                    } else {
                        reloadAds = false;
                    }
                case INTERNAL_ERROR:
                    if (adMobInterstitial == null) {
                        adMobInterstitial = new com.google.android.gms.ads.InterstitialAd(LaunchPadActivity.this);
                        adMobInterstitial.setAdUnitId("ca-app-pub-4640479150069852/9227646327");
                        adMobInterstitial.setAdListener(new AdListener() {
                            @Override
                            public void onAdClosed() {
                                loadNewAdMobInterstitial();
                                editSample();
                            }
                        });
                        loadNewAdMobInterstitial();
                    } else if (!adMobInterstitial.isLoaded() && !adMobInterstitial.isLoading()){
                        loadNewAdMobInterstitial();
                    }
                    break;
            }
        }

        @Override
        public void onAdExpired(Ad ad){
            ad.loadAd();
        }

        @Override
        public void onAdDismissed(Ad ad)
        {
            // Start the activity once the interstitial has disappeared.
            ad.loadAd();
            editSample();
        }
    }
    private class ReloadAd implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
            //Log.d(LOG_TAG, "Reload ad thread started");
            try {
                Thread.sleep(30000);
                if (amznInterstitialAd != null && reloadAds && !amznInterstitialAd.isLoading() && !amznInterstitialAd.isReady())
                    amznInterstitialAd.loadAd();
            } catch (InterruptedException e){ e.printStackTrace();}
        }
    }
    private void loadNewAdMobInterstitial() {
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                .addTestDevice("5E3D3DD85078633EE1836B9FC5FB4D89")
                .build();
        adMobInterstitial.loadAd(adRequest);
    }

    // Methods for managing touch pads
    private void preparePad(String path, int id, int color) {
        File f = new File(path); // File to contain the new sample
        // Create a new file to contain the new sample
        File sampleFile = new File(sampleDirectory, "Mixmatic_Touch_Pad_" + String.valueOf(id) + ".wav");
        // If the file already exists, delete, but remember to keep its configuration
        boolean keepSettings = false;
        if (sampleFile.isFile()) {
            sampleFile.delete();
            keepSettings = true;
        }
        // Copy new sample over
        try {
            CopyFile(f, sampleFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (sampleFile.isFile()) { // If successful, prepare touchpad
            Sample sample = new Sample(sampleFile.getAbsolutePath(), id);
            sample.setOnPlayFinishedListener(samplePlayListener);
            TouchPad t = (TouchPad) findViewById(id);
            String padNumber = (String)t.getTag();
            // Set sample properties and save to shared preferences
            SharedPreferences.Editor editor = launchPadprefs.edit();
            if (keepSettings) {
                sample.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE));
                sample.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, false));
                sample.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
            } else { // Default properties
                sample.setLaunchMode(Sample.LAUNCHMODE_GATE);
                editor.putInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                editor.putBoolean(padNumber + LOOPMODE, false);
                editor.putFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume());
            }
            samples.put(id, sample);
            activePads.add(id);
            switch (color) { // Set and save color
                case 0:
                    t.setBackgroundResource(R.drawable.launch_pad_blue);
                    editor.putInt(padNumber + COLOR, 0);
                    break;
                case 1:
                    t.setBackgroundResource(R.drawable.launch_pad_red);
                    editor.putInt(padNumber + COLOR, 1);
                    break;
                case 2:
                    t.setBackgroundResource(R.drawable.launch_pad_green);
                    editor.putInt(padNumber + COLOR, 2);
                    break;
                case 3:
                    t.setBackgroundResource(R.drawable.launch_pad_orange);
                    editor.putInt(padNumber + COLOR, 3);
                    break;
            }
            editor.apply();
            // Show the action bar for pads with loaded samples
            if (launchPadActionMode == null)
                launchPadActionMode = startActionMode(launchPadActionModeCallback);
        }
    }
    private void loadSample(Sample s, TouchPad pad){
        int id = pad.getId();
        pad.setOnTouchListener(TouchPadTouchListener);
        s.setOnPlayFinishedListener(samplePlayListener);
        samples.put(id, s);
        String padNumber = (String)pad.getTag();
        s.setLoopMode(launchPadprefs.getBoolean(padNumber + LOOPMODE, false));
        s.setLaunchMode(launchPadprefs.getInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER));
        s.setVolume(launchPadprefs.getFloat(padNumber + SAMPLE_VOLUME, 0.5f * AudioTrack.getMaxVolume()));
        int color = launchPadprefs.getInt(padNumber + COLOR, 0);
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
        numberPicker.setMaxValue((int) (AudioTrack.getMaxVolume() * 100));
        numberPicker.setValue((int) (samples.get(sampleId).getVolume() * 100));
        builder.setView(numberPicker);
        builder.setPositiveButton(R.string.set, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                float volume = (float) numberPicker.getValue() / 100;
                samples.get(sampleId).setVolume(volume);
                TouchPad pad = (TouchPad) findViewById(sampleId);
                String padNumber = (String) pad.getTag();
                SharedPreferences.Editor editor = launchPadprefs.edit();
                editor.putFloat(padNumber + SAMPLE_VOLUME, volume);
                editor.apply();
                dialog.dismiss();
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void removeSample(int sampleId){
        Sample s = samples.get(sampleId);
        // Remove the sample from the list of active samples
        samples.remove(sampleId);
        activePads.remove((Integer)sampleId);
        // Set the launchpad background to empty
        View pad = findViewById(sampleId);
        pad.setBackgroundResource(R.drawable.launch_pad_empty);
        String padNumber = (String)pad.getTag();
        // Remove settings from shared preferences
        SharedPreferences.Editor editor = launchPadprefs.edit();
        editor.remove(padNumber + SAMPLE_PATH);
        editor.remove(padNumber + SAMPLE_VOLUME);
        editor.remove(padNumber + LAUNCHMODE);
        editor.remove(padNumber + LOOPMODE);
        editor.remove(padNumber + COLOR);
        editor.apply();
        Toast.makeText(context, "Sample removed", Toast.LENGTH_SHORT).show();
        launchPadActionMode = null;
    }
    private void notifySampleLoadError(Sample s){
        String error = getString(R.string.error_sample_load) + " " + new File(s.getPath()).getName();
        if (s.getState() == Sample.ERROR_OUT_OF_MEMORY)
            error = error + "\n" + getString(R.string.error_out_of_memory);
        else if (s.getState() == Sample.ERROR_FILE_IO)
            error = error + "\n" + getString(R.string.error_file_read);
        final String message = error;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void recordSample(){
        final File micAudioFile = new File(getExternalCacheDir(), "mic_audio_recording.wav");
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.audio_record_dialog, null);
        final ImageButton recButton = (ImageButton)view.findViewById(R.id.recordButton);
        recButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!recButton.isSelected()) {
                    recButton.setSelected(true);
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            recordAudio(micAudioFile);
                        }
                    }).start();
                } else {
                    isMicRecording = false;
                    recButton.setSelected(false);
                }
            }
        });
        builder.setView(view);
        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isMicRecording = false;
                preparePad(micAudioFile.getAbsolutePath(), selectedSampleID, 0);
                micAudioFile.delete();
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                isMicRecording = false;
                micAudioFile.delete();
                dialog.cancel();
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void recordAudio(File recordingFile){
        int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
        isMicRecording = true;
        short[] audioShorts = new short[bufferSize / 2];
        WaveFile waveFile = new WaveFile();
        waveFile.OpenForWrite(recordingFile.getAbsolutePath(), 44100, (short) 16, (short) 2);
        recorder.startRecording();
        while (isMicRecording){
            recorder.read(audioShorts, 0, audioShorts.length);
            waveFile.WriteData(audioShorts, audioShorts.length);
        }
        waveFile.Close();
    }
    // Disables pads during playback
    private void disconnectTouchListeners(){
        for (int i : touchPadIds){
            TouchPad pad = (TouchPad)findViewById(i);
            pad.setOnTouchListener(null);
            pad.setClickable(false);
        }
    }
    // Enables pads after playback is finished
    private void reconnectTouchListeners(){
        for (int i : touchPadIds) {
            TouchPad pad = (TouchPad) findViewById(i);
            pad.setOnTouchListener(TouchPadTouchListener);
            pad.setClickable(true);
        }
    }

    // Edit mode methods
    private void gotoEditMode(){
        isEditMode = true;
        for (int id : touchPadIds){
            View pad = findViewById(id);
            pad.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    touchPadEditLongClick(v);
                    return true;
                }
            });
        }
    }
    private void gotoPlayMode(){
        for (int id : touchPadIds){
            TouchPad pad = (TouchPad)findViewById(id);
            if (activePads.contains(id))
                pad.setOnLongClickListener(null);
            else
                pad.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        emptyPadLongClick(v);
                        return true;
                    }
                });
        }
        isEditMode = false;
    }
    public void touchPadClick(View v) {
        if (isEditMode) {
            // Deselect the current touchpad
            View oldView = findViewById(selectedSampleID);
            if (oldView != null)
                oldView.setSelected(false);
            // Select the new touchpad
            v.setSelected(true);
            selectedSampleID = v.getId();
            // Show the action mode
            if (launchPadActionMode == null)
                launchPadActionMode = startActionMode(launchPadActionModeCallback);
            if (samples.indexOfKey(v.getId()) >= 0) {
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
            }
            else {
                showSampleLibrary();
            }
        }
    }
    public void emptyPadLongClick(View v){
        // Deselect the current touchpad
        View oldView = findViewById(selectedSampleID);
        if (oldView != null)
            oldView.setSelected(false);
        selectedSampleID = v.getId();
        v.setSelected(true);
        if (launchPadActionMode == null)
            launchPadActionMode = startActionMode(launchPadActionModeCallback);
        gotoEditMode();
        showSampleLibrary();
    }
    public void touchPadEditLongClick(View v){
        if (activePads.contains(v.getId())) {
            // Deselect the previous touchpad
            View oldView = findViewById(selectedSampleID);
            if (oldView != null)
                oldView.setSelected(false);
            selectedSampleID = v.getId();
            v.setSelected(true);
            ClipData.Item pathItem = new ClipData.Item(String.valueOf(v.getId()));
            String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
            ClipData dragData = new ClipData(TOUCHPAD_ID, mime_type, pathItem);
            View.DragShadowBuilder myShadow = new TouchPadDragShadowBuilder(v);
            v.startDrag(dragData,  // the data to be dragged
                    myShadow,  // the drag shadow builder
                    null,      // no need to use local data
                    0          // flags (not currently used, set to 0)
            );
            v.setSelected(true);
        }
    }
    private void listItemEditClick(int index){
            Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                intent.putExtra(SAMPLE_PATH, sampleListAdapter.getItem(index).getAbsolutePath());
            startActivityForResult(intent, GET_SAMPLE);
    }
    protected class PadDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()){
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    return true;
                case DragEvent.ACTION_DROP:
                    ClipData data = event.getClipData();
                    if (data.getDescription().getLabel().equals(TOUCHPAD_ID)) {
                        int pad1Id = Integer.parseInt((String) data.getItemAt(0).getText());
                        swapPads(pad1Id, v.getId());
                        resetRecording();
                    } else if (data.getDescription().getLabel().equals(SAMPLE_PATH)){
                        loadPadFromDrop(data.getItemAt(0).getText().toString(), v.getId(), Integer.parseInt(data.getItemAt(1).getText().toString()));
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
    private void swapPads(int pad1Id, int pad2Id){
        Sample sample1 = samples.get(pad1Id);
        TouchPad pad1 = (TouchPad)findViewById(pad1Id);
        String pad1Number = (String)pad1.getTag();
        int pad1Color = launchPadprefs.getInt((String)pad1.getTag() + COLOR, 0);
        TouchPad pad2 = (TouchPad) findViewById(pad2Id);
        String pad2Number = (String)pad2.getTag();

        // Move pad1 to pad2
        Sample sample2 = null;
        if (activePads.contains((Integer)pad2Id)) {
            sample2 = samples.get(pad2Id);
        }
        else activePads.add(pad2Id);
        sample1.id = pad2Id;
        samples.remove(pad1Id);
        samples.put(pad2Id, sample1);
        SharedPreferences.Editor editor = launchPadprefs.edit();
        editor.putString(pad2Number + SAMPLE_PATH, sample1.getPath());
        editor.putInt(pad2Number + LAUNCHMODE, sample1.getLaunchMode());
        editor.putBoolean(pad2Number + LOOPMODE, sample1.getLoopMode());
        editor.putFloat(pad2Number + SAMPLE_VOLUME, sample1.getVolume());
        editor.putInt(pad2Number + COLOR, pad1Color);
        setPadColor(pad1Color, pad2);
        sample1.release();
        sample1.reloadAudioTrack();

        if (sample2 != null) { // Move pad2 to pad1
            int pad2Color = launchPadprefs.getInt(pad2Number + COLOR, 0);
            sample2.id = pad1Id;
            samples.put(pad1Id, sample2);
            editor.putString(pad1Number + SAMPLE_PATH, sample2.getPath());
            editor.putInt(pad1Number + LAUNCHMODE, sample2.getLaunchMode());
            editor.putBoolean(pad1Number + LOOPMODE, sample2.getLoopMode());
            editor.putFloat(pad1Number + SAMPLE_VOLUME, sample2.getVolume());
            editor.putInt(pad1Number + COLOR, pad2Color);
            setPadColor(pad2Color, pad1);
            sample2.release();
            sample2.reloadAudioTrack();
        } else { // Remove pad1
            activePads.remove((Integer)pad1Id);
            pad1.setBackgroundResource(R.drawable.launch_pad_empty);
            pad1.setOnLongClickListener(null);
            editor.remove(pad1Number + SAMPLE_PATH);
            editor.remove(pad1Number + LAUNCHMODE);
            editor.remove(pad1Number + LOOPMODE);
            editor.remove(pad1Number + SAMPLE_VOLUME);
            editor.remove(pad1Number + COLOR);
        }
        editor.apply();
        pad2.callOnClick();
    }
    private void loadPadFromDrop(String path, int padId, int color){
        TouchPad pad = (TouchPad)findViewById(padId);
        SharedPreferences.Editor editor = launchPadprefs.edit();
        if (activePads.contains((Integer)padId)) {
            samples.get(padId).release();
            samples.remove(padId);
        }
        Sample sample = new Sample(path, padId);
        if (sample.isReady()) {
            activePads.add(padId);
            editor.putInt(pad.getTag().toString() + COLOR, color);
            editor.putString(pad.getTag().toString() + SAMPLE_PATH, path);
            editor.commit();
            loadSample(sample, pad);
            pad.callOnClick();
        } else {
            notifySampleLoadError(sample);
        }
    }
    private ActionMode.Callback launchPadActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.launch_pad_context, menu);
            isEditMode = true;
            if (selectedSampleID > -1) {
                View oldView = findViewById(selectedSampleID);
                oldView.setSelected(true);
            }
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
                    if (amznInterstitialAd.isReady()){
                        amznInterstitialAd.showAd();
                    }
                    else if (adMobInterstitial != null && adMobInterstitial.isLoaded())
                        adMobInterstitial.show();
                    else {
                        editSample();
                    }
                    return true;
                case R.id.action_loop_mode:
                    if (item.isChecked()) {
                        item.setChecked(false);
                        if (samples.indexOfKey(selectedSampleID) >= 0) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(false);
                            String padNumber = (String)findViewById(selectedSampleID).getTag();
                            prefEditor.putBoolean(padNumber + LOOPMODE, false);
                            prefEditor.apply();
                        }
                    }
                    else {
                        item.setChecked(true);
                        if (samples.indexOfKey(selectedSampleID) >= 0) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(true);
                            String padNumber = (String)findViewById(selectedSampleID).getTag();
                            prefEditor.putBoolean(padNumber + LOOPMODE, true);
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
                        String padNumber = (String)findViewById(selectedSampleID).getTag();
                        prefEditor.putInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_GATE);
                        prefEditor.apply();
                    }
                    return true;
                case R.id.action_launch_mode_trigger:
                    if (samples.indexOfKey(selectedSampleID) >= 0){
                        Sample s = (Sample)samples.get(selectedSampleID);
                        s.setOnPlayFinishedListener(samplePlayListener);
                        s.setLaunchMode(Sample.LAUNCHMODE_TRIGGER);
                        item.setChecked(true);
                        String padNumber = (String)findViewById(selectedSampleID).getTag();
                        prefEditor.putInt(padNumber + LAUNCHMODE, Sample.LAUNCHMODE_TRIGGER);
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
                                String padNumber = (String)v.getTag();
                                SharedPreferences.Editor editor = launchPadprefs.edit();
                                switch (which){ // Set and save color
                                    case 0:
                                        v.setBackgroundResource(R.drawable.launch_pad_blue);
                                        editor.putInt(padNumber + COLOR, 0);
                                        break;
                                    case 1:
                                        v.setBackgroundResource(R.drawable.launch_pad_red);
                                        editor.putInt(padNumber + COLOR, 1);
                                        break;
                                    case 2:
                                        v.setBackgroundResource(R.drawable.launch_pad_green);
                                        editor.putInt(padNumber + COLOR, 2);
                                        break;
                                    case 3:
                                        v.setBackgroundResource(R.drawable.launch_pad_orange);
                                        editor.putInt(padNumber + COLOR, 3);
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
                case R.id.action_set_tempo:
                    matchTempo();
                    return true;
                default:
                    return true;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            launchPadActionMode = null;
            View oldView = findViewById(selectedSampleID);
            if (oldView != null)
                oldView.setSelected(false);
            selectedSampleID = -1;
            isEditMode = false;
            hideSampleLibrary();
            gotoPlayMode();
        }
    };

    // Sample Library Methods
    public void SampleLibTabClick(View v){
        if (isSampleLibraryShowing) {
            hideSampleLibrary();
        }
        else showSampleLibrary();
    }
    private void showSampleLibrary(){
        if (!isSampleLibraryShowing) {
            final LinearLayout library = (LinearLayout)findViewById(R.id.sample_library);
            ListView sampleListView = (ListView)library.findViewById(R.id.sample_listview);
            sampleListView.setAdapter(sampleListAdapter);
            sampleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    sampleLibraryIndex = position;
                }
            });
            sampleListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                    // Goto edit mode
                    if (!isEditMode)
                        gotoEditMode();

                    // Start drag
                    ClipData.Item pathItem = new ClipData.Item(sampleListAdapter.getItem(position).getAbsolutePath());
                    String[] mime_type = {ClipDescription.MIMETYPE_TEXT_PLAIN};
                    ClipData dragData = new ClipData(SAMPLE_PATH, mime_type, pathItem);
                    Random random = new Random();
                    Drawable drawable;
                    switch (random.nextInt(4)) {
                        case 0:
                            drawable = getResources().getDrawable(R.drawable.button_blue);
                            dragData.addItem(new ClipData.Item(String.valueOf(0)));
                            break;
                        case 1:
                            drawable = getResources().getDrawable(R.drawable.button_red);
                            dragData.addItem(new ClipData.Item(String.valueOf(1)));
                            break;
                        case 2:
                            drawable = getResources().getDrawable(R.drawable.button_green);
                            dragData.addItem(new ClipData.Item(String.valueOf(2)));
                            break;
                        case 3:
                            drawable = getResources().getDrawable(R.drawable.button_orange);
                            dragData.addItem(new ClipData.Item(String.valueOf(3)));
                            break;
                        default:
                            drawable = getResources().getDrawable(R.drawable.button_blue);
                            dragData.addItem(new ClipData.Item(String.valueOf(0)));
                            break;
                    }
                    View.DragShadowBuilder myShadow = new SampleDragShadowBuilder(view, drawable);
                    view.startDrag(dragData,  // the data to be dragged
                            myShadow,  // the drag shadow builder
                            null,      // no need to use local data
                            0          // flags (not currently used, set to 0)
                    );
                    return true;
                }
            });

            // Prepare Sample Library list adapter
            new Thread(new Runnable() {
                @Override
                public void run() {
                    File[] sampleFiles = sampleDirectory.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getName().endsWith(".wav");
                        }
                    });
                    sampleListAdapter.addAll(sampleFiles);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (sampleListAdapter.getCount() == 0){
                                library.findViewById(R.id.first_sample_button).setVisibility(View.VISIBLE);
                            } else {
                                library.findViewById(R.id.first_sample_button).setVisibility(View.GONE);
                            }
                        }
                    });
                }
            }).start();

            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideLeft = ObjectAnimator.ofFloat(library, "TranslationX", sampleListView.getWidth(), 0);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.play(slideLeft);
            set.setTarget(library);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    isSampleLibraryShowing = true;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }
    }
    private void hideSampleLibrary(){
        if (isSampleLibraryShowing) {
            LinearLayout library = (LinearLayout) findViewById(R.id.sample_library);
            ListView sampleListView = (ListView) library.findViewById(R.id.sample_listview);
            sampleLibraryIndex = -1;

            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator slideRight = ObjectAnimator.ofFloat(library, "TranslationX", 0, sampleListView.getWidth());
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.play(slideRight);
            set.setTarget(library);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    isSampleLibraryShowing = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {

                }

                @Override
                public void onAnimationRepeat(Animator animation) {

                }
            });
            set.start();
        }
    }
    private class SampleListAdapter extends BaseAdapter {
        public ArrayList<File> sampleFiles;
        public ArrayList<String> sampleLengths;
        private Context mContext;
        private int resource_id;
        private LayoutInflater mInflater;

        public SampleListAdapter(Context context, int resource_id) {
            this.mContext = context;
            this.resource_id = resource_id;
            mInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            sampleFiles = new ArrayList<>();
            sampleLengths = new ArrayList<>();
        }

        public void add(File file){
            if (!sampleFiles.contains(file)) {
                sampleFiles.add(file);
                // Determine length of wav file
                float length = Utils.getWavLengthInSeconds(file, 44100);
                sampleLengths.add(String.valueOf((int) Math.floor(length / 60)) + ":" + String.format("%.2f", length % 60));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyDataSetChanged();
                    }
                });
            }
        }
        public void addAll(File[] files){
            for (File f : files){
                add(f);
            }
        }
        public void remove(File file){
            if (sampleFiles.contains(file)) {
                sampleLengths.remove(sampleFiles.indexOf(file));
                sampleFiles.remove(file);
                notifyDataSetChanged();
            }
        }
        @Override
        public int getCount() {
            if (sampleFiles != null)
                return sampleFiles.size();
            else return 0;
        }

        @Override
        public File getItem(int position) {
            return sampleFiles.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, final ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(resource_id, null);
            }
            TextView nameText = (TextView)convertView.findViewById(R.id.sample_file_name);
            nameText.setText(sampleFiles.get(position).getName().replace(".wav", ""));
            TextView timeText = (TextView)convertView.findViewById(R.id.sample_length);
            timeText.setText(sampleLengths.get(position));
            Bitmap wavBitmap = Utils.decodedSampledBitmapFromFile(new File(sampleFiles.get(position).getAbsolutePath().replace(".wav", ".png")), 80, 80);
            ImageView wavIcon = (ImageView)convertView.findViewById(R.id.wave_icon);
            if (wavBitmap != null){
                wavIcon.setBackground(new BitmapDrawable(getResources(), wavBitmap));
            } else {
                wavIcon.setBackground(getResources().getDrawable(R.drawable.ic_sound_wave_icon_green));
            }
            ImageView pauseButton = (ImageView)convertView.findViewById(R.id.pause_icon);
            pauseButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(final View v) {
                    if (v.isSelected()){
                        v.setSelected(false);
                        if (samplePlayer != null)
                            samplePlayer.stop();
                    } else {
                        v.setSelected(true);
                        if (samplePlayer != null)
                            if (samplePlayer.isPlaying())
                                samplePlayer.stop();
                        samplePlayer = new MediaPlayer();
                        try {
                            samplePlayer.setDataSource(sampleFiles.get(position).getAbsolutePath());
                            samplePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                                @Override
                                public void onCompletion(MediaPlayer mp) {
                                    v.setSelected(false);
                                    samplePlayer.release();
                                    samplePlayer = null;
                                }
                            });
                            samplePlayer.prepare();
                            samplePlayer.start();
                            listItemPauseButton = v;
                        } catch (IOException e){e.printStackTrace();}
                    }
                }
            });
            ImageView editButton = (ImageView)convertView.findViewById(R.id.item_edit_icon);
            editButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.isActivated() || v.isSelected())
                        listItemEditClick(position);
                }
            });
            return  convertView;
        }
    }
    protected class LibraryDragEventListener implements View.OnDragListener {
        public boolean onDrag(View v, DragEvent event) {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    showSampleLibrary();
                    return true;
                case DragEvent.ACTION_DRAG_LOCATION:
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    hideSampleLibrary();
                    return true;
                case DragEvent.ACTION_DROP:
                    ClipData data = event.getClipData();
                    if (data.getDescription().getLabel().equals(TOUCHPAD_ID)) {
                        int padId = Integer.parseInt((String) data.getItemAt(0).getText());
                        removeSample(padId);
                        resetRecording();
                    }
                    return true;
                default:
                    return false;
            }
        }
    }
    public void AddSampleClick(View v){
        selectedSampleID = -1;
        if (amznInterstitialAd.isReady()) {
            amznInterstitialAd.showAd();
        }
        else if (adMobInterstitial != null && adMobInterstitial.isLoaded()) {
            adMobInterstitial.show();
        }
        else {
            editSample();
        }
    }
    private void editSample(){
        Intent intent = new Intent(Intent.ACTION_SEND, null, this, SampleEditActivity.class);
        if (isEditMode) {
            intent.putExtra(TOUCHPAD_ID, selectedSampleID);
            if (samples.indexOfKey(selectedSampleID) >= 0) {
                intent.putExtra(SAMPLE_PATH, samples.get(selectedSampleID).getPath());
                String padNumber = (String) findViewById(selectedSampleID).getTag();
                intent.putExtra(COLOR, launchPadprefs.getInt(padNumber + COLOR, 0));
            }
        }
        startActivityForResult(intent, GET_SAMPLE);
    }
    public void DeleteSampleClick(View v){
        // Determine if a sample is selected
        File file = null;
        if (sampleLibraryIndex > -1) {
            file = sampleListAdapter.getItem(sampleLibraryIndex);
        } else if (selectedSampleID > -1) {
            if (activePads.contains((Integer)selectedSampleID))
                file = new File(samples.get(selectedSampleID).getPath());
        }

        if (file != null) {
            final File sampleFile = file;
            // Determine what touchpads this sample is assigned to
            final ArrayList<Integer> padIds = new ArrayList<>();
            for (int i : activePads) {
                String path = samples.get(i).getPath();
                if (path.equals(sampleFile.getAbsolutePath()))
                    padIds.add(i);
            }

            // Show dialog to alert user that file will be permanently deleted
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(R.string.remove_sample_dialog_title);
            if (padIds.size() > 0)
                builder.setMessage(R.string.remove_samples_in_use_dialog_message);
            else
                builder.setMessage(R.string.remove_sample_dialog_message);
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    for (int i : padIds)
                        removeSample(i);
                    if (sampleFile.delete())
                        Toast.makeText(context, getString(R.string.sample_deleted_toast), Toast.LENGTH_SHORT).show();
                    else
                        Toast.makeText(context, getString(R.string.sample_delete_error_toast), Toast.LENGTH_SHORT).show();
                    if (sampleListAdapter != null)
                        sampleListAdapter.remove(sampleFile);
                }
            });
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        } else {
            Toast.makeText(context, getString(R.string.no_sample_selected_toast), Toast.LENGTH_SHORT).show();
        }
    }

    // Handles touch events in record mode;
    private View.OnTouchListener TouchPadTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return handlePlayTouch(v, event);
        }
    };
    private boolean handlePlayTouch(View v, MotionEvent event){
        if (!isEditMode && !isPlaying && samples.indexOfKey(v.getId()) >= 0) {
            if (!isRecording){ // Start counter thread
                isRecording = true;
                counter = recordingEndTime;
                actionBarMenu.findItem(R.id.action_play).setIcon(R.drawable.ic_action_av_pause);
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

    // Helper methods for menu commands
    private void matchTempo(){
        // Determine tempo of sample and re-sample scale factor
        AudioProcessor processor = new AudioProcessor(samples.get(selectedSampleID).getPath());
        ArrayList<BeatInfo> beats = processor.detectBeats();
        double sampleTempo = 60 * beats.size() / (samples.get(selectedSampleID).getSampleLengthMillis() / 1000);
        final double ratio = (double)bpm / sampleTempo;

        // Show re-sample dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Re-sample");
        LayoutInflater inflater = getLayoutInflater();
        final View view = inflater.inflate(R.layout.resample_dialog, null);
        TextView textGlobalTempo = (TextView)view.findViewById(R.id.textGlobalTempo);
        textGlobalTempo.setText(getString(R.string.global_tempo_msg, bpm));
        TextView textSampleTempo = (TextView)view.findViewById(R.id.textSampleTempo);
        textSampleTempo.setText(getString(R.string.sample_tempo_msg, (int)sampleTempo));
        TextView textRatio = (TextView)view.findViewById(R.id.textSuggestedRatio);
        textRatio.setText(getString(R.string.resample_suggested_ratio, ratio));
        builder.setView(view);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                progressDialog = new ProgressDialog(context);
                progressDialog.setMessage(getString(R.string.resample_msg));
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setIndeterminate(true);
                progressDialog.setCancelable(false);
                progressDialog.setCanceledOnTouchOutside(false);
                dialogCanceled = false;
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
                        final String stretchedSamplePath = samples.get(selectedSampleID).matchTempo(ratio);
                        view.post(new Runnable() {
                            @Override
                            public void run() {
                                progressDialog.dismiss();
                                playSampleDialog(stretchedSamplePath);
                            }
                        });
                    }
                }).start();
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
    private void playSampleDialog(final String samplePath){
        final SamplePlayerFragment playerFragment = new SamplePlayerFragment();
        SamplePlayerFragment.SamplePlayerListener listener = new SamplePlayerFragment.SamplePlayerListener() {
            @Override
            public void positiveButtonClick(View v) {
                preparePad(samplePath, selectedSampleID, launchPadprefs.getInt(String.valueOf(selectedSampleID) + COLOR, 0));
                playerFragment.dismiss();
            }

            @Override
            public void negativeButtonClick(View v) {
                File newSample = new File(samplePath);
                newSample.delete();
                playerFragment.getDialog().cancel();
            }
        };
        playerFragment.setOnClickListener(listener);
        Bundle args = new Bundle();
        args.putString(SamplePlayerFragment.WAV_PATH, samplePath);
        playerFragment.setArguments(args);
        playerFragment.show(getFragmentManager(), "PlayFragment");
    }

    // Methods for handling playback of mix
    private void playMix(){
        if (launchEvents.size() > 0) {
            disconnectTouchListeners();
            isRecording = false;
            new Thread(new playBackRecording()).start();
        }
    }
    private class playBackRecording implements Runnable {
        @Override
        public void run() {
            isRecording = false;
            isPlaying = true;
            new Thread(new CounterThread()).start();
            for (int i = 0; i < launchEvents.size() && launchEvents.get(i).getTimeStamp() < counter; i++){
                playEventIndex = i;
            }
            for (int i = playEventIndex; i < launchEvents.size() && isPlaying && !stopPlaybackThread; i++) {
                ArrayList<LaunchEvent> tempArray = new ArrayList<LaunchEvent>(loopingSamplesPlaying.size());
                tempArray.addAll(loopingSamplesPlaying);
                for (LaunchEvent e : tempArray){
                    if (e.timeStamp <= counter) {
                        samples.get(e.getSampleId()).play();
                        final int id = e.getSampleId();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                findViewById(id).setPressed(true);
                            }
                        });
                        loopingSamplesPlaying.remove(e);
                        //Log.d(LOG_TAG, "Restarting looped sample");
                    }
                }
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
                    final int id = event.getSampleId();
                    samples.get(id).play();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(id).setPressed(true);
                        }
                    });
                }
                else {
                    final int id = event.getSampleId();
                    samples.get(id).stop();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            findViewById(id).setPressed(false);
                        }
                    });
                }
            }
            if (!stopPlaybackThread) {
                isPlaying = false;
                reconnectTouchListeners();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ActionBar actionBar = getActionBar();
                        if (actionBar != null) {
                            MenuItem item = actionBarMenu.findItem(R.id.action_play);
                            item.setIcon(R.drawable.ic_action_av_play_arrow);
                        }
                    }
                });
            }
        }
    }
    private void resetRecording(){
        isRecording = false;
        counter = 0;
        recordingEndTime = 0;
        launchEvents = new ArrayList<LaunchEvent>(50);
        loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
        updateCounterMessage();
    }
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
    private void stopPlayBack(){
        isPlaying = false;
        loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
        for (Integer i : activePads) {
            Sample s = samples.get(i);
            View v = findViewById(i);
            v.setPressed(false);
            if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                if (isRecording)
                    launchEvents.add(new LaunchEvent(counter, LaunchEvent.PLAY_STOP, i));
                else if (s.getLoopMode()){
                    //Log.d(LOG_TAG, "Counter: " + String.valueOf(counter));
                    double newTime = counter - s.audioTrack.getPlaybackHeadPosition() * 4 / (8 * 44100) * 1000 + s.getSampleLengthMillis();
                    loopingSamplesPlaying.add(new LaunchEvent(newTime, LaunchEvent.PLAY_START, i));
                    //Log.d(LOG_TAG, "New loop sample time: " + String.valueOf(newTime));
                }
                s.stop();
            }
        }
        View v = findViewById(R.id.button_play);
        v.setBackgroundResource(R.drawable.button_play);
        reconnectTouchListeners();
        isRecording = false;
    }
    public void PlayButtonClick(View v){
        if (isPlaying || isRecording) {
            stopPlayBack();
        }
        else if (launchEvents.size() > 0 && playEventIndex < launchEvents.size()){
            playMix();
        }
    }
    public void RewindButtonClick(View v){
        stopPlayBack();
        counter = 0;
        playEventIndex = 0;
        loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
        updateCounterMessage();
    }
    public void FastForwardButtonClick(View v){
        stopPlayBack();
        counter = recordingEndTime;
        playEventIndex = Math.max(launchEvents.size() - 1, 0);
        loopingSamplesPlaying = new ArrayList<LaunchEvent>(5);
        updateCounterMessage();
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
                runOnUiThread(new Runnable() {
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
    private void WriteWavFileOld(String fileName){
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
                Log.d(LOG_TAG, "Wav max value: " + String.valueOf(max));
                for (int j = 0; j < mixedBuffer.length; j++) {
                    shortData[j] = (short) (mixedBuffer[j] * 32767 / max);
                }
            }
            waveFile.WriteData(shortData, shortData.length);
            final int progress = i;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    progressDialog.setProgress(progress);
                }
            });
            i++;
            bytesWritten += length;
        } while (i < launchEvents.size() && !dialogCanceled);
        waveFile.Close();
    }
    private void WriteWavFile(String fileName){
        // Wave file to write
        File tempFile = new File(homeDirectory, fileName + ".mix");
        if (tempFile.isFile())
            tempFile.delete();
        OutputStream outputStream = null;
        float max = 0;
        try {
            tempFile.createNewFile();
            outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
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
                    length = (int) (launchEvents.get(i + 1).timeStamp / 1000 * 44100) * 16 / 4 - bytesWritten;
                } else
                    length = 0;
                // short array to hold that data to be written before the next event
                float[] mixedBuffer = new float[length / 2];
                // For each sample that is playing load its data and add it to the array to be written
                for (String idString : playingSamples) {
                    // byte array to hold that data to be written before the next event for this sample
                    int id = Integer.parseInt(idString);
                    byte[] byteData = new byte[length];
                    byte[] sampleBytes = samples.get(id).getAudioBytes();
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
                    } else {
                        ByteBuffer.wrap(sampleBytes, offset, byteData.length).get(byteData);
                        playingSampleOffsets.put(Integer.parseInt(idString), String.valueOf(sampleBytes.length + offset + byteData.length));
                    }
                    // Convert byte data to shorts
                    short[] shorts = new short[byteData.length / 2];
                    ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    // Add the sample short data to the total data to be written to file
                    float volume = samples.get(id).getVolume();
                    for (int j = 0; j < shorts.length; j++) {
                        mixedBuffer[j] = mixedBuffer[j] + shorts[j] * volume;
                        max = Math.max(mixedBuffer[j], max);
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(4 * mixedBuffer.length);
                    for (float value : mixedBuffer){
                        buffer.putFloat(value);
                    }
                    byte[] bytes = new byte[4 * mixedBuffer.length];
                    buffer.rewind();
                    buffer.get(bytes);
                    outputStream.write(bytes);
                }
                final int progress = i;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress(progress);
                    }
                });
                i++;
                bytesWritten += length;
            } while (i < launchEvents.size() && !dialogCanceled);
            outputStream.close();
        } catch (FileNotFoundException e){e.printStackTrace();}
        catch (IOException e) {e.printStackTrace();}
        finally {
            if (outputStream != null)
                try {
                    outputStream.close();
                } catch(IOException e) {}
        }
        // Write wav file from temp mix file
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(tempFile));
            File waveFileFinal = new File(homeDirectory, fileName + ".wav");
            if (waveFileFinal.isFile())
                waveFileFinal.delete();
            WaveFile waveFile = new WaveFile();
            waveFile.OpenForWrite(waveFileFinal.getAbsolutePath(), 44100, (short)16, (short)2);
            int numBytes = inputStream.available();
            while (numBytes > 0){
                byte[] bytes = new byte[numBytes];
                float[] floats = new float[bytes.length / 4];
                short[] shorts = new short[floats.length];
                inputStream.read(bytes);
                ByteBuffer.wrap(bytes).asFloatBuffer().get(floats);
                for (int j = 0; j < floats.length; j++) {
                    shorts[j] = (short) (floats[j] * 32767 / max);
                }
                waveFile.WriteData(shorts, shorts.length);
                numBytes = inputStream.available();
            }
            waveFile.Close();
        }catch (IOException e) {e.printStackTrace();}
    }
    private void WriteWavFile2(String fileName){
        // Wave file to write
        File tempFile = new File(homeDirectory, fileName + ".mix");
        if (tempFile.isFile())
            tempFile.delete();
        OutputStream outputStream = null;
        float max = 0;
        try {
            tempFile.createNewFile();
            outputStream = new BufferedOutputStream(new FileOutputStream(tempFile));
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
                    length = (int) (launchEvents.get(i + 1).timeStamp / 1000 * 44100) * 16 / 4 - bytesWritten;
                } else
                    length = 0;
                // short array to hold that data to be written before the next event
                float[] mixedBuffer = new float[length / 2];
                // For each sample that is playing load its data and add it to the array to be written
                for (String idString : playingSamples) {
                    // byte array to hold that data to be written before the next event for this sample
                    int id = Integer.parseInt(idString);
                    byte[] byteData = new byte[length];
                    byte[] sampleBytes = samples.get(id).getAudioBytes();
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
                    } else {
                        ByteBuffer.wrap(sampleBytes, offset, byteData.length).get(byteData);
                        playingSampleOffsets.put(Integer.parseInt(idString), String.valueOf(sampleBytes.length + offset + byteData.length));
                    }
                    // Convert byte data to shorts
                    short[] shorts = new short[byteData.length / 2];
                    ByteBuffer.wrap(byteData).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    // Add the sample short data to the total data to be written to file
                    float volume = samples.get(id).getVolume();
                    for (int j = 0; j < shorts.length; j++) {
                        mixedBuffer[j] = mixedBuffer[j] + shorts[j] * volume;
                        max = Math.max(mixedBuffer[j], max);
                    }
                    ByteBuffer buffer = ByteBuffer.allocate(4 * mixedBuffer.length);
                    for (float value : mixedBuffer){
                        buffer.putFloat(value);
                    }
                    byte[] bytes = new byte[4 * mixedBuffer.length];
                    buffer.rewind();
                    buffer.get(bytes);
                    outputStream.write(bytes);
                }
                final int progress = i;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog.setProgress(progress);
                    }
                });
                i++;
                bytesWritten += length;
            } while (i < launchEvents.size() && !dialogCanceled);
            outputStream.close();
        } catch (FileNotFoundException e){e.printStackTrace();}
        catch (IOException e) {e.printStackTrace();}
        finally {
            if (outputStream != null)
                try {
                    outputStream.close();
                } catch(IOException e) {}
        }
        InputStream inputStream = null;
        try {
            inputStream = new BufferedInputStream(new FileInputStream(tempFile));
            File waveFileFinal = new File(homeDirectory, fileName + ".wav");
            if (waveFileFinal.isFile())
                waveFileFinal.delete();
            WaveFile waveFile = new WaveFile();
            waveFile.OpenForWrite(waveFileFinal.getAbsolutePath(), 44100, (short)16, (short)2);
            int numBytes = inputStream.available();
            while (numBytes > 0){
                byte[] bytes = new byte[numBytes];
                float[] floats = new float[bytes.length / 4];
                short[] shorts = new short[floats.length];
                inputStream.read(bytes);
                ByteBuffer.wrap(bytes).asFloatBuffer().get(floats);
                for (int j = 0; j < floats.length; j++) {
                    shorts[j] = (short) (floats[j] * 32767 / max);
                }
                waveFile.WriteData(shorts, shorts.length);
                numBytes = inputStream.available();
            }
            waveFile.Close();
        }catch (IOException e) {e.printStackTrace();}
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        updateCounterMessage();
                    }
                });
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
        public static final int ERROR_NONE = 9000;
        public static final int ERROR_OUT_OF_MEMORY = 9001;
        public static final int ERROR_FILE_IO = 9002;

        // Private fields
        private int id;
        private String path;
        private boolean loop = false;
        private int loopMode = 0;
        private int launchMode = LAUNCHMODE_TRIGGER;
        private File sampleFile;
        private int sampleByteLength;
        private int sampleRate = 44100;
        private float volume = 0.5f * AudioTrack.getMaxVolume();
        private boolean played = false;
        private int sampleState = ERROR_NONE;
        private AudioTrack audioTrack;
        private AudioTrack.OnPlaybackPositionUpdateListener listener;

        // Constructors
        public Sample(String path, int id){
            this.path = path;
            this.id = id;
            sampleFile = new File(path);
            if (sampleFile.exists()){
                sampleByteLength = (int)sampleFile.length() - 44;
                sampleRate = Utils.getWavSampleRate(sampleFile);
                reloadAudioTrack();
            }
        }
        public Sample(String path, int launchMode, boolean loopMode){
            this.path = path;
            loop = loopMode;
            if (!setLaunchMode(launchMode))
                this.launchMode = LAUNCHMODE_TRIGGER;
            reloadAudioTrack();
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
                }*/
                if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED)
                    reloadAudioTrack();

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
        public double getSampleLengthMillis(){
            return  1000 * (double)sampleByteLength / (sampleRate * 16 / 4);
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
        public boolean isReady(){
            if (audioTrack != null)
                return audioTrack.getState() == AudioTrack.STATE_INITIALIZED;
            else
                return false;
        }
        public int getState(){
            return sampleState;
        }
        public void play(){
            try {
                played = true;
                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    resetMarker();
                    audioTrack.play();
                } else if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                    //Log.d("AudioTrack", String.valueOf(id) + " uninitialized");
                    reloadAudioTrack();
                    if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED && audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
                        audioTrack.play();
                }
            }catch (IllegalStateException e) {e.printStackTrace();}
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
                reloadAudioTrack();
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
            reloadAudioTrack();
        }
        public void release(){
            audioTrack.release();
        }
        public void setVolume(float volume){
            this.volume = volume;
            reloadAudioTrack();
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
        public String matchTempo(double tempo){
            AudioProcessor processor = new AudioProcessor(path);
            int L, M;
            M = (int)(tempo * 100);
            L = 100;
            processor.resample(L, M, homeDirectory.getAbsolutePath() + "/tempo_stretch_test.wav");
            return homeDirectory.getAbsolutePath() + "/tempo_stretch_test.wav";
        }
        public void reloadAudioTrack() {
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    sampleRate,
                    AudioFormat.CHANNEL_OUT_STEREO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    sampleByteLength,
                    AudioTrack.MODE_STATIC, id);
            if (runtime.totalMemory() + (sampleByteLength - runtime.freeMemory()) < runtime.maxMemory() * 0.9) {
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

                    audioTrack.setStereoVolume(volume, volume);
                    if (listener != null) {
                        audioTrack.setPlaybackPositionUpdateListener(listener);
                        resetMarker();
                    }

                    if (loop) {
                        setLoopMode(true);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    sampleState = ERROR_FILE_IO;
                }
            } else {
                sampleState = ERROR_OUT_OF_MEMORY;
            }
        }
    }

    public class LaunchEvent{
        public static final String PLAY_START = "com.nakedape.mixmaticlooppad.playstart";
        public static final String PLAY_STOP = "com.nakedape.mixmaticlooppad.playstop";
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
            loadInBackground();
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
                runOnUiThread(new Runnable() {
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
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setCancelable(false);
                        builder.setMessage(R.string.unlicensed_message);
                        builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(Intent.ACTION_VIEW);
                                intent.setData(Uri.parse("market://details?id=com.nakedape.mixmaticlooppad"));
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

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }
    }
}
