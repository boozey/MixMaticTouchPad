package com.nakedape.mixmaticlooppad;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AnticipateOvershootInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.amazon.device.ads.Ad;
import com.amazon.device.ads.AdError;
import com.amazon.device.ads.AdLayout;
import com.amazon.device.ads.AdProperties;
import com.amazon.device.ads.AdRegistration;
import com.amazon.device.ads.DefaultAdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;

import javazoom.jl.converter.WaveFile;


public class SampleEditActivity extends Activity {

    private static final String LOG_TAG = "SampleEditActivity";

    static final int REQUEST_MUSIC_GET = 0;

    private String WAV_CACHE_FILE_PATH;
    private File CACHE_PATH;
    private SharedPreferences pref;
    private RelativeLayout rootLayout;
    private AudioSampleView sampleView;
    private float sampleRate = 44100;
    private int sampleLength;
    private long encodedFileSize;
    private boolean showBeats = false;
    private InputStream musicStream;
    private Thread mp3ConvertThread;
    private ProgressDialog dlg;
    private boolean dlgCanceled;
    private DialogInterface.OnCancelListener dlgCancelListener = new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
            dlgCanceled = true;
        }
    };
    private Context context;
    private int sampleId = -1;
    private int numSlices = 1;
    private boolean isSliceMode = false;
    private boolean isDecoding = false;
    private boolean isGeneratingWaveForm = false;
    private File origSampleFile;
    private File sampleDirectory;
    private View popup;
    private boolean saveSlice;
    private AdLayout adView;
    private boolean reloadAds;

    private Menu actionBarMenu;
    // Sample edit context menu
    private ActionMode sampleEditActionMode;
    // Beat edit context menu
    private ActionMode beatEditActionMode;

    // Fragment to save data during runtime changes
    private AudioSampleData savedData;

    // Media player variables
    private Uri fullMusicUri;
    private MediaFormat mediaFormat;
    private boolean loop;
    private boolean continuePlaying;
    private boolean stopPlayIndicatorThread;
    private boolean continueProcessing;
    private MediaPlayer mPlayer;
    private AudioManager am;
    private final AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
           handleAudioFocusChange(focusChange);
        }
    };

    // Activity overrides
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_edit);
        rootLayout = (RelativeLayout)findViewById(R.id.rootLayout);

        // Setup Amazon Ad
        AdRegistration.setAppKey("83c8d7d1e7ec460fbf2f8f37f88c095a");
        AdRegistration.enableTesting(true);
        AdRegistration.enableLogging(true);
        adView = (AdLayout) findViewById(R.id.amazonAdView);
        adView.setListener(new AmazonAdListener());
        adView.loadAd();

        // Prepare stoarage directory
        if (Utils.isExternalStorageWritable()){
            sampleDirectory = new File(getExternalFilesDir(null), "Samples");
            if (!sampleDirectory.exists())
                if (!sampleDirectory.mkdir()) Log.e(LOG_TAG, "error creating external files directory");
        } else {
            sampleDirectory = new File(getFilesDir(), "Samples");
            if (!sampleDirectory.exists())
                if (!sampleDirectory.mkdir()) Log.e(LOG_TAG, "error creating internal files directory");
        }
        if (getExternalCacheDir() != null)
            CACHE_PATH = getExternalCacheDir();
        else
            CACHE_PATH = getCacheDir();

        PreferenceManager.setDefaultValues(this, R.xml.sample_edit_preferences, true);
        pref = PreferenceManager.getDefaultSharedPreferences(this);
        // Store reference to activity context to use inside event handlers
        context = this;
        // Store a reference to the path for the temporary cache of the wav file
        WAV_CACHE_FILE_PATH = CACHE_PATH.getAbsolutePath() + "/cache.wav";

        // Setup audio sample view
        sampleView = (AudioSampleView)findViewById(R.id.spectralView);
        sampleView.setCACHE_PATH(CACHE_PATH.getAbsolutePath());
        sampleView.setFocusable(true);
        sampleView.setFocusableInTouchMode(true);
        sampleView.setOnTouchListener(sampleView);
        sampleView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sampleView.isSelection()) {
                    if (sampleEditActionMode == null)
                        sampleEditActionMode = startActionMode(sampleEditActionModeCallback);
                }
                else if (sampleEditActionMode != null)
                    sampleEditActionMode.finish();
                else
                    sampleView.clearSelection();
            }
        });
        sampleView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (sampleView.getSelectionMode() == AudioSampleView.BEAT_SELECTION_MODE){
                    Vibrator vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
                    sampleView.setSelectionMode(AudioSampleView.BEAT_MOVE_MODE);
                    vibrator.vibrate(50);
                }
                return false;
            }
        });

        //Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

        // Get data from intent
        Intent intent = getIntent();
        sampleId = intent.getIntExtra(LaunchPadActivity.TOUCHPAD_ID, -1);
        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        savedData = (AudioSampleData) fm.findFragmentByTag("data");
        // If there is saved data, load it, otherwise determine the mode for the activity
        if (savedData != null) {
            loop = savedData.getLoop();
            if (savedData.isSliceMode()) {
                setSliceMode(savedData.getNumSlices());
            }
            switch (savedData.getSelectionMode()) {
                case AudioSampleView.SELECTION_MODE:
                    // Show the action bar if there is a selection
                    if ((savedData.getSelectionEndTime() - savedData.getSelectionStartTime()) > 0) {
                        sampleEditActionMode = startActionMode(sampleEditActionModeCallback);
                    }
                    break;
                case AudioSampleView.BEAT_SELECTION_MODE:
                    beatEditActionMode = startActionMode(beatEditActionModeCallback);
            }
            sampleView.loadAudioSampleData(savedData);
            if (savedData.isDecoding())
                decodeAudio(savedData.getFullMusicUri());
            else if (savedData.isGeneratingWaveForm())
                loadSample();
            mPlayer = savedData.getmPlayer();
            if (mPlayer != null) {
                if (mPlayer.isPlaying()) {
                    ImageButton b = (ImageButton) findViewById(R.id.buttonPlay);
                    b.setBackgroundResource(R.drawable.button_pause_large);
                    new Thread(new PlayIndicator()).start();
                }
            }
            else if (savedData.samplePath != null)
                LoadMediaPlayer(Uri.parse(savedData.samplePath));
        }
        else if (intent.hasExtra(LaunchPadActivity.SAMPLE_PATH)){
            // sample edit is loading a sample from a launch pad
            savedData = new AudioSampleData();
            fm.beginTransaction().add(savedData, "data").commit();
            LoadSampleFromIntent(intent);
        }
        else{
            // If the cache file already exists from a previous edit, delete it
            File temp = new File(WAV_CACHE_FILE_PATH);
            if (temp.isFile())
                temp.delete();
            // Create fragment to persist data during runtime changes
            savedData = new AudioSampleData();
            fm.beginTransaction().add(savedData, "data").commit();
            // Start intent to select an audio file to edit
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    showNewSamplePopup(300);
                }
            });
        }
    }
    private void LoadSampleFromIntent(Intent intent){
        sampleView.setColor(intent.getIntExtra(LaunchPadActivity.COLOR, 0));
        origSampleFile = new File(intent.getStringExtra(LaunchPadActivity.SAMPLE_PATH));
        if (origSampleFile.isFile() && origSampleFile.exists()){ // If a sample is being passed, load it and process
            File loadedSample = new File(WAV_CACHE_FILE_PATH);
            try {
                Utils.CopyFile(origSampleFile, loadedSample);
            }catch (IOException e){e.printStackTrace();}
            LoadMediaPlayer(Uri.parse(WAV_CACHE_FILE_PATH));

            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    sampleView.loadFile(WAV_CACHE_FILE_PATH);
                    sampleView.redraw();
                }
            });
        }

    }
    private void setSliceMode(int numSlices){
        isSliceMode = true;
        this.numSlices = numSlices;
    }
    @Override
    protected void onResume(){
        super.onResume();
        reloadAds = true;
        if (adView != null && !adView.isLoading())
            adView.loadAd();
    }
    @Override
    protected void onPause(){
        super.onPause();
        reloadAds = false;
    }
    @Override
    protected void onStop(){
        reloadAds = false;
        super.onStop();
        if (isFinishing()){
            dlgCanceled = true;
            stopPlayIndicatorThread = true;
            if (mPlayer != null){
                mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
        }
        else {
            dlgCanceled = true;
            stopPlayIndicatorThread = true;
            sampleView.saveAudioSampleData(savedData);
            savedData.setLoop(loop);
            savedData.setNumSlices(numSlices);
            savedData.setSliceMode(isSliceMode);
            savedData.setDecoding(isDecoding);
            savedData.setFullMusicUri(fullMusicUri);
            savedData.setGeneratingWaveForm(isGeneratingWaveForm);
            if (mPlayer != null) {
                if (mPlayer.isPlaying())
                    savedData.setmPlayer(mPlayer);
                else {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                    savedData.setmPlayer(null);
                }
            }
        }
    }
    @Override
    protected void onDestroy(){
        super.onDestroy();
        reloadAds = false;
        adView.destroy();

    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_sample_edit, menu);
        actionBarMenu = menu;
        MenuItem item = menu.findItem(R.id.action_save);
        if (isSliceMode)
            item.setTitle(getString(R.string.button_slice_mode_title));
        else
            item.setTitle(getString(R.string.save));
        return true;
    }
    @Override
    public boolean onPrepareOptionsMenu(Menu menu){
        MenuItem item = menu.findItem(R.id.action_save);
        if (isSliceMode)
            item.setTitle(getString(R.string.button_slice_mode_title));
        else
            item.setTitle(getString(R.string.save));
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        final AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        switch (id){
            case R.id.action_settings:
                Intent intent = new Intent(EditPreferencesActivity.SAMPLE_EDIT_PREFS, null, context, EditPreferencesActivity.class);
                startActivity(intent);
                return true;
            case R.id.action_load_file:
                showNewSamplePopup(0);
                return true;
            case R.id.action_edit_beats:
                enableEditBeatsMode();
                return true;
            case R.id.action_resample:
                startResampleFlow();
                return true;
            case R.id.action_pick_color:
                pickColor();
                return true;
            case R.id.action_save:
                Save(null);
                return true;
            case R.id.action_select:
                if (item.isChecked()){
                    sampleView.setSelectionMode(AudioSampleView.PAN_ZOOM_MODE);
                    item.setChecked(false);
                    item.setIcon(R.drawable.ic_action_select);
                } else {
                    sampleView.setSelectionMode(AudioSampleView.SELECTION_MODE);
                    item.setChecked(true);
                    item.setIcon(R.drawable.ic_action_select_selected);
                }
            default:
                return super.onOptionsItemSelected(item);
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MUSIC_GET && resultCode == RESULT_OK) {
            origSampleFile = null;
            decodeAudio(data.getData());
        }
    }
    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch (keycode) {
            case KeyEvent.KEYCODE_BACK:
                if (sampleView.needsSaving()){
                    promptForSave();
                    return true;
                }
            default:
                setResult(Activity.RESULT_CANCELED);
                return super.onKeyDown(keycode, e);
        }
    }

    // New sample popup methods
    private void showNewSamplePopup(int delay){
        if (popup == null) {
            // Add the popup
            popup = getLayoutInflater().inflate(R.layout.new_sample_popup, null);
            View background = popup.findViewById(R.id.popup_background);
            background.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    return true;
                }
            });
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            popup.setLayoutParams(params);
            popup.setAlpha(0f);
            rootLayout.addView(popup);

            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator alpha = ObjectAnimator.ofFloat(popup, "Alpha", 0f, 1f);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(popup, "ScaleX", 0f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(popup, "ScaleY", 0f, 1f);
            set.setInterpolator(new AnticipateOvershootInterpolator());
            set.playTogether(alpha, scaleX, scaleY);
            set.setStartDelay(delay);
            set.setTarget(popup);
            set.start();
        }
    }
    private void hidePopup(){
        if (popup != null){
            // Start the animation
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator alpha = ObjectAnimator.ofFloat(popup, "Alpha", 1f, 0f);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(popup, "ScaleX", 1f, 0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(popup, "ScaleY", 1f, 0f);
            set.setInterpolator(new AnticipateOvershootInterpolator());
            set.playTogether(alpha, scaleX, scaleY);
            set.setTarget(popup);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {

                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    rootLayout.removeView(popup);
                    popup = null;
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
    public void PopupCloseButtonClick(View v){
        hidePopup();
    }
    public void PopupLibraryButtonClick(View v){
        hidePopup();
        SelectAudioFile();
    }
    public void PopupRecordButtonClick(View v){
        hidePopup();
        ImageView recordButton = new ImageView(context);
        recordButton.setBackgroundResource(R.drawable.mic_button);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        recordButton.setLayoutParams(params);
        recordButton.setAlpha(0f);
        recordButton.setVisibility(View.VISIBLE);
        recordButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecordButtonClick(v);
            }
        });
        rootLayout.addView(recordButton);

        // Start the animation
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(recordButton, "Alpha", 0f, 1f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(recordButton, "ScaleX", 0f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(recordButton, "ScaleY", 0f, 1f);
        set.setInterpolator(new AnticipateOvershootInterpolator());
        set.playTogether(alpha, scaleX, scaleY);
        set.setStartDelay(200);
        set.setTarget(recordButton);
        set.start();
    }
    public void RecordButtonClick(View v){
        if (sampleView.isMicRecording()) {
            sampleView.stopRecording();
            LoadMediaPlayer(Uri.parse(sampleView.getSamplePath()));
            rootLayout.removeView(v);
        }
        else {
            final View recordButton = v;
            // Start the animation and the recording
            AnimatorSet set = new AnimatorSet();
            ObjectAnimator translateX = ObjectAnimator.ofFloat(recordButton, "X", rootLayout.getWidth() - recordButton.getWidth());
            ObjectAnimator translateY = ObjectAnimator.ofFloat(recordButton, "Y", rootLayout.getHeight() - recordButton.getHeight());
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(recordButton, "ScaleX", 0.5f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(recordButton, "ScaleY", 0.5f);
            set.setInterpolator(new AccelerateDecelerateInterpolator());
            set.playTogether(translateX, translateY, scaleX, scaleY);
            set.setTarget(recordButton);
            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                    recordButton.setSelected(true);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    sampleView.startRecording();
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

    public void SelectAudioFile(){
        // Allow user to select an audio file
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*.mp3");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_MUSIC_GET);
        }
    }
    private void decodeAudio(Uri uri){
        fullMusicUri = uri;
        try {
            //Load audio stream
            ContentResolver contentResolver = this.getContentResolver();
            musicStream = contentResolver.openInputStream(fullMusicUri);

            // Read media format
            readMediaFormat();

            //If the sampleLength wasn't set by MediaFormat, use MediaPlayer
            if (sampleLength <= 0)
                LoadMediaPlayer(fullMusicUri);

            // Display progress dialog
            dlg = new ProgressDialog(this);
            if (sampleLength > 0){
                dlg.setIndeterminate(false);
                dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                dlg.setMax((int)encodedFileSize);
            }
            else {
                dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dlg.setIndeterminate(true);
            }
            dlg.setCancelable(true);
            dlg.setCanceledOnTouchOutside(false);
            dlgCanceled = false;
            dlg.setOnCancelListener(dlgCancelListener);
            dlg.setMessage("Decoding audio ...");
            dlg.show();
            new Thread(new DecodeAudioThread()).start();
        }
        catch (IOException e){
            e.printStackTrace();
        }
    }
    private void loadSample(){
        if (dlg != null)
            if (dlg.isShowing())
                dlg.dismiss();
        sampleView.loadFile(WAV_CACHE_FILE_PATH);
        sampleView.redraw();
        LoadMediaPlayer(fullMusicUri);
    }

    // Media Player methods
    public void LoadMediaPlayer(Uri uri){
        if (mPlayer != null){
            if (mPlayer.isPlaying()) mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mPlayer.setDataSource(getApplicationContext(), uri);
            mPlayer.prepare();
            sampleLength = mPlayer.getDuration() / 1000;
        } catch (IOException e){
            e.printStackTrace();
        }
    }
    private void handleAudioFocusChange(int focusChange){
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // Pause playback
            if (mPlayer != null)
                mPlayer.pause();
        } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            // Resume playback
            if (mPlayer != null && continuePlaying)
                mPlayer.start();
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            am.abandonAudioFocus(afChangeListener);
            // Stop playback
            if (mPlayer != null) {
                Log.d(LOG_TAG, "Audio focus lost");
                if (mPlayer.isPlaying())
                    mPlayer.stop();
                mPlayer.release();
                mPlayer = null;
            }
        }
    }
    public void Play(View view){
        if (mPlayer != null && mPlayer.isPlaying()){
                continuePlaying = false;
                findViewById(R.id.buttonPlay).setBackgroundResource(R.drawable.button_play_large);

        } else {
            // Request audio focs for playback
            int result = am.requestAudioFocus(afChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                if (mPlayer != null ){
                    findViewById(R.id.buttonPlay).setBackgroundResource(R.drawable.button_pause_large);
                    sampleView.isPlaying = true;
                    mPlayer.start();
                    new Thread(new PlayIndicator()).start();
                } else {
                    // Start a new instance
                    mPlayer = new MediaPlayer();
                    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    try {
                        findViewById(R.id.buttonPlay).setBackgroundResource(R.drawable.button_pause_large);
                        sampleView.isPlaying = true;
                        continuePlaying = true;
                        mPlayer.setDataSource(sampleView.getSamplePath());
                        mPlayer.prepare();
                        mPlayer.start();
                        new Thread(new PlayIndicator()).start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    public void Rewind(View view){
        AudioSampleView sampleView = (AudioSampleView)findViewById(R.id.spectralView);
        if (mPlayer != null){
            mPlayer.seekTo((int)(sampleView.getSelectionStartTime() * 1000));
            sampleView.redraw();
        }
    }

    // Sample saving methods
    public void Save(View view){
        File temp = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "sample.wav");
        if (temp.isFile())
            temp.delete();
        if (mPlayer != null){
            continuePlaying = false;
            if (mPlayer.isPlaying()) mPlayer.stop();
        }
            checkSampleSize();

    }
    private void promptForSave(){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.dialog_title_check_for_save);
        builder.setMessage(R.string.dialog_message_check_for_save);
        builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (origSampleFile != null)
                    saveSample(origSampleFile.getName());
                else
                    checkSampleSize();
            }
        });
        builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setResult(Activity.RESULT_CANCELED);
                finish();
            }
        });
        builder.setNeutralButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void checkSampleSize(){
        // If sample size is more than 20 seconds, show warning
        double sampleSize = sampleView.getSampleLength();
        if (sampleView.isSelection())
            sampleSize = sampleView.getSelectionEndTime() - sampleView.getSelectionStartTime();
        if (sampleSize > 20) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(getString(R.string.sample_size_warning, sampleView.sampleLength));
            builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    promptForName();
                }
            });
            builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
        else {
            promptForName();
        }
    }
    private void promptForName(){
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final View layout = getLayoutInflater().inflate(R.layout.sample_file_name_prompt, null);
        final TextView fileNameTextView = (TextView)layout.findViewById(R.id.file_name_preview);
        fileNameTextView.setText("Sample_" + Calendar.getInstance().getTimeInMillis() + ".wav");
        final EditText editText = (EditText)layout.findViewById(R.id.sample_name_text);
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String filename;
                if (s.length() < 1) {
                    filename = "Sample_" + Calendar.getInstance().getTimeInMillis() + ".wav";
                    fileNameTextView.setText(filename);
                }
                else {
                    filename = s.toString().replaceAll("[^a-zA-Z0-9 ]", "_") + ".wav";
                    fileNameTextView.setText(filename);
                }
                File sampleFile = new File(sampleDirectory, filename);
                if (sampleFile.exists())
                    layout.findViewById(R.id.file_exists_textview).setVisibility(View.VISIBLE);
                else
                    layout.findViewById(R.id.file_exists_textview).setVisibility(View.INVISIBLE);
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
        builder.setView(layout);
        builder.setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String filename = fileNameTextView.getText().toString();
                saveSample(filename);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void saveSample(final String filename){
        dlg = new ProgressDialog(context);
        dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dlg.setIndeterminate(true);
        dlg.setMessage(getString(R.string.save_progress_msg));
        dlg.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final File sampleFile = new File(sampleDirectory, filename);
                boolean success;
                if (saveSlice && sampleView.isSelection())
                    success = sampleView.getSlice(sampleFile, sampleView.getSelectionStartTime(), sampleView.getSelectionEndTime());
                else
                    success = sampleView.getSlice(sampleFile, 0, sampleView.getSampleLength());
                if (success) {
                    Utils.WriteImage(sampleView.getWaveFormThumbnail(), sampleFile.getAbsolutePath().replace(".wav", ".png"));
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dlg.dismiss();
                            if (!saveSlice){
                                Intent result = new Intent("com.nakedape.mixmaticlooppad.RESULT_ACTION", Uri.parse(sampleFile.getAbsolutePath()));
                                result.putExtra(LaunchPadActivity.TOUCHPAD_ID, sampleId);
                                result.putExtra(LaunchPadActivity.COLOR, sampleView.color);
                                setResult(Activity.RESULT_OK, result);
                                finish();
                            }

                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dlg.dismiss();
                            Toast.makeText(context, "Error saving file", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }).start();
    }

    // Sample editing methods
    private ActionMode.Callback sampleEditActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.sample_edit_context, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()){
                case R.id.action_trim_wav:
                    Trim();
                    return true;
                case R.id.action_loop_selection:
                    if (item.isChecked()){
                        loop = false;
                        item.setChecked(false);
                        item.setIcon(R.drawable.ic_action_av_loop);
                    }
                    else {
                        loop = true;
                        item.setChecked(true);
                        item.setIcon(R.drawable.ic_action_av_loop_selected);
                    }
                    return true;
                case R.id.action_save_selection:
                    saveSlice = true;
                    Save(null);
                    return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            sampleEditActionMode = null;
            sampleView.clearSelection();
            saveSlice = false;

            // Put the sample view back in pan zoom mode
            sampleView.setSelectionMode(AudioSampleView.PAN_ZOOM_MODE);
            // Deselect action bar item and icon
            MenuItem selectItem = actionBarMenu.findItem(R.id.action_select);
            if (selectItem.isChecked()) {
                selectItem.setChecked(false);
                selectItem.setIcon(R.drawable.ic_action_select);
            }
            loop = false;
        }
    };
    public void Trim(){
        continuePlaying = false;
        if (mPlayer != null) {
            if (mPlayer.isPlaying()) mPlayer.stop();
        }
        sampleView.setOnAudioProcessingFinishedListener(new AudioSampleView.OnAudioProcessingFinishedListener() {
            @Override
            public void OnProcessingFinish() {
                LoadMediaPlayer(Uri.parse(sampleView.getSamplePath()));
            }
        });
        sampleView.TrimToSelectionAsync(sampleView.getSelectionStartTime(), sampleView.getSelectionEndTime());
    }
    private ActionMode.Callback beatEditActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.beats_edit_context, menu);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
            switch (item.getItemId()){
                case R.id.action_remove_beat:
                    sample.removeSelectedBeat();
                    break;
                case R.id.action_identify_beats:
                    sample.identifyBeats();
                    sample.redraw();
                    break;
                case R.id.action_insert_beat:
                    sample.insertBeat();
                    break;
                case R.id.action_adjust_tempo:
                    startResampleFlow();
                    break;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            beatEditActionMode = null;
            sampleView.setSelectionMode(AudioSampleView.PAN_ZOOM_MODE);
        }
    };

    public void enableEditBeatsMode() {
        if (!sampleView.hasBeatInfo()){
            sampleView.setOnAudioProcessingFinishedListener(new AudioSampleView.OnAudioProcessingFinishedListener() {
                @Override
                public void OnProcessingFinish() {
                    dlg.dismiss();
                    if (sampleView.hasBeatInfo())
                        enableEditBeatsMode();
                    else
                        Toast.makeText(context, getString(R.string.toast_no_beats_found), Toast.LENGTH_SHORT).show();
                }
            });
            showBeats();
        }
        if (beatEditActionMode == null) {
            beatEditActionMode = startActionMode(beatEditActionModeCallback);
        }
        sampleView.setSelectionMode(AudioSampleView.BEAT_SELECTION_MODE);
    }
    public void showBeats(){
        dlg = new ProgressDialog(context);
        dlg.setMessage("Processing audio");
        dlg.setIndeterminate(true);
        dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        sampleView.identifyBeatsAsync();
        dlg.show();
    }
    public void startResampleFlow(){
        if (!sampleView.hasBeatInfo()){
            sampleView.setOnAudioProcessingFinishedListener(new AudioSampleView.OnAudioProcessingFinishedListener() {
                @Override
                public void OnProcessingFinish() {
                    dlg.dismiss();
                    showResampleDialog();
                }
            });
            showBeats();
        } else {
            showResampleDialog();
        }
    }
    private void showResampleDialog(){
        int numBeats = Math.max(1, sampleView.getNumBeats());
        int globalTempo = pref.getInt(LaunchPadPreferencesFragment.PREF_BPM, 120);
        float sampleTempo = 60f * (float)numBeats / (float)(sampleView.getSampleLength());
        float tempoMatchRatio = (float)globalTempo / sampleTempo;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        final View layout = getLayoutInflater().inflate(R.layout.resample_dialog, null);
        builder.setTitle(getString(R.string.resample_positive_button));
        TextView sampleTempoText = (TextView)layout.findViewById(R.id.textSampleTempo);
        sampleTempoText.setText(getString(R.string.sample_tempo_msg, sampleTempo));
        TextView globalTempoText = (TextView)layout.findViewById(R.id.textGlobalTempo);
        globalTempoText.setText(getString(R.string.global_tempo_msg, globalTempo));
        TextView tempoMatchRatioText = (TextView)layout.findViewById(R.id.textSuggestedRatio);
        tempoMatchRatioText.setText(getString(R.string.resample_suggested_ratio, tempoMatchRatio));
        final TextView ratioText = (TextView)layout.findViewById(R.id.ratio);
        ratioText.setText(getString(R.string.resample_ratio, tempoMatchRatio));
        final SeekBar ratioBar = (SeekBar)layout.findViewById(R.id.ratio_seekbar);
        ratioBar.setProgress((int)(tempoMatchRatio * 1000) - 500);
        ratioBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ratioText.setText(getString(R.string.resample_ratio, (float)(500 + progress) / 1000));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        builder.setView(layout);
        builder.setPositiveButton(R.string.resample_positive_button, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                float ratio = (float)(ratioBar.getProgress() + 500) / 1000;
                resample(ratio);
            }
        });
        builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void resample(float ratio){
        dlg = new ProgressDialog(context);
        dlg.setMessage("Processing audio");
        dlg.setIndeterminate(true);
        dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dlg.setCancelable(false);
        dlg.setCanceledOnTouchOutside(false);
        sampleView.setOnAudioProcessingFinishedListener(new AudioSampleView.OnAudioProcessingFinishedListener() {
            @Override
            public void OnProcessingFinish() {
                LoadMediaPlayer(Uri.parse(sampleView.getSamplePath()));
                dlg.dismiss();
            }
        });
        sampleView.resampleAsync(ratio);
        dlg.show();
    }

    public void pickColor(){
        final AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.color_dialog_title);
        builder.setItems(R.array.color_names, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                sample.setColor(which);
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void readMediaFormat(){
        MediaExtractor extractor = new MediaExtractor();
        ContentResolver contentResolver = context.getContentResolver();
        try {
            AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(fullMusicUri, "r");
            extractor.setDataSource(fd.getFileDescriptor());
            encodedFileSize = fd.getLength();
            fd.close();
        } catch (IOException e) {e.printStackTrace();}
        mediaFormat = extractor.getTrackFormat(0);
        sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        if (mediaFormat.containsKey(MediaFormat.KEY_DURATION)) {
            sampleLength = (int) mediaFormat.getLong(MediaFormat.KEY_DURATION);
            Log.d(LOG_TAG, "sampleLength set by mediaFormat: " + String.valueOf(sampleLength));
        }
    }

    // Thread to decode audio into PCM/WAV
    public class DecodeAudioThread implements Runnable {

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec;
        long TIMEOUT_US = 10000;
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        Uri sourceUri = fullMusicUri;
        WaveFile waveFile = new WaveFile();
        int bytesProcessed = 0;

        @Override
        public void run() {
            isDecoding = true;
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            ContentResolver contentResolver = context.getContentResolver();
            try {
                AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(sourceUri, "r");
                extractor.setDataSource(fd.getFileDescriptor());
                fd.close();
            } catch (IOException e) {e.printStackTrace();}
            MediaFormat format = extractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            File temp = new File(WAV_CACHE_FILE_PATH);
            if (temp.exists())
                temp.delete();
            waveFile.OpenForWrite(WAV_CACHE_FILE_PATH,
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    (short)(8 * format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)),
                    (short)format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
            try {
                codec = MediaCodec.createDecoderByType(mime);
                codec.configure(format, null /* surface */, null /* crypto */, 0 /* flags */);
                codec.start();
                codecInputBuffers = codec.getInputBuffers();
                codecOutputBuffers = codec.getOutputBuffers();
                extractor.selectTrack(0);
                boolean sawInputEOS = false;
                boolean sawOutputEOS = false;
                do {
                    // Load input buffer
                    int inputBufIndex = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (inputBufIndex >= 0) {
                        ByteBuffer dstBuf = codecInputBuffers[inputBufIndex];

                        int sampleSize = extractor.readSampleData(dstBuf, 0);
                        long presentationTimeUs = 0;
                        if (sampleSize < 0) {
                            sawInputEOS = true;
                            sampleSize = 0;
                        } else {
                            presentationTimeUs = extractor.getSampleTime();
                        }

                        codec.queueInputBuffer(inputBufIndex,
                                0, //offset
                                sampleSize,
                                presentationTimeUs,
                                sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                        bytesProcessed += sampleSize;
                        // Update progress
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                dlg.setProgress(bytesProcessed);
                            }
                        });
                        if (!sawInputEOS) {
                            extractor.advance();
                        }
                        // Process output buffer
                        final int res = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                        if (res >= 0) {
                            int outputBufIndex = res;
                            ByteBuffer buf = codecOutputBuffers[outputBufIndex];

                            final byte[] chunk = new byte[info.size];
                            buf.get(chunk); // Read the buffer all at once
                            buf.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN

                            if (chunk.length > 0) {
                                short[] shorts = new short[chunk.length / 2];
                                ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                                waveFile.WriteData(shorts, shorts.length);

                            }
                            codec.releaseOutputBuffer(outputBufIndex, false /* render */);

                            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                sawOutputEOS = true;
                            }
                        } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                            codecOutputBuffers = codec.getOutputBuffers();
                        }
                    }
                } while (!sawInputEOS && !dlgCanceled);
                waveFile.Close();
                codec.stop();
                codec.release();
                codec = null;
                isDecoding = false;
            }catch (IOException e){ e.printStackTrace();}
            // Close dialog and prepare sampleView
            rootLayout.post(new Runnable() {
                @Override
                public void run() {
                    dlg.dismiss();
                    loadSample();
                }
            });
        }
    }

    // Thread to update play indicator in waveform view
    public class PlayIndicator implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_DISPLAY);
            sampleView.isPlaying = true;
            continuePlaying = true;
            double startTime, endTime;
            try {
                do {
                    if (mPlayer != null) {
                        // Start playing from beginning of selection
                        startTime = Math.round(sampleView.getSelectionStartTime() * 1000);
                        endTime = Math.round(sampleView.getSelectionEndTime() * 1000);
                        if (!(endTime - startTime > 0)){
                            startTime = 0;
                            endTime = Math.round(sampleView.sampleLength * 1000);
                        }
                        if (mPlayer.isPlaying()
                                && (mPlayer.getCurrentPosition() >= endTime
                                || mPlayer.getCurrentPosition() < startTime))
                            mPlayer.seekTo((int) Math.round(sampleView.getSelectionStartTime() * 1000));
                        do { // Send an update to the play indicator
                            try {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (sampleView != null)
                                            sampleView.updatePlayIndicator((double)mPlayer.getCurrentPosition() / 1000);
                                    }
                                });
                                Thread.sleep(50);
                            } catch (InterruptedException | NullPointerException e) {
                                e.printStackTrace();
                            }
                            if (sampleView != null) {
                                startTime = Math.round(sampleView.getSelectionStartTime() * 1000);
                                endTime = Math.round(sampleView.getSelectionEndTime() * 1000);
                                if (!(endTime - startTime > 0)) {
                                    startTime = 0;
                                    endTime = Math.round(sampleView.sampleLength * 1000);
                                }
                            }
                            // Continue updating as long as still within the selection and it hasn't been paused
                        }
                        while (mPlayer != null && continuePlaying && !stopPlayIndicatorThread &&
                                mPlayer.getCurrentPosition() < endTime &&
                                mPlayer.getCurrentPosition() >= startTime && mPlayer.isPlaying());

                        // Loop play if in loop mode and it hasn't been paused
                    }
                } while (mPlayer != null && loop && !stopPlayIndicatorThread && continuePlaying && mPlayer.isPlaying());
            } catch (IllegalStateException | NullPointerException e){e.printStackTrace();}
            if (!stopPlayIndicatorThread) {
                // Done with play, pause the player and send final update
                if (mPlayer != null && mPlayer.isPlaying())
                    mPlayer.pause();
                continuePlaying = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        sampleView.isPlaying = false;
                        sampleView.invalidate();
                        ImageButton b = (ImageButton)findViewById(R.id.buttonPlay);
                        b.setBackgroundResource(R.drawable.button_play_large);
                    }
                });
            }
        }
    }

    // Amazon Ads
    private class AmazonAdListener extends DefaultAdListener {
        @Override
        public void onAdLoaded(Ad ad, AdProperties adProperties){
            if (ad == adView)
            {
                new Thread(new ReloadAd()).start();
            }
        }

        @Override
        public void onAdFailedToLoad(Ad ad, AdError error) {
            // Call backup ad network.
            Log.e(LOG_TAG, "Amazon ad failed to load");
            Log.e(LOG_TAG, error.getMessage());
            switch (error.getCode()){
                case NETWORK_ERROR:
                case NETWORK_TIMEOUT:
                    new Thread(new ReloadAd()).start();
                    break;
                case INTERNAL_ERROR:
                case NO_FILL:
                    adView.setVisibility(View.GONE);
                    AdView mAdView = (AdView) findViewById(R.id.adMobAdView);
                    mAdView.setVisibility(View.VISIBLE);
                    AdRequest adRequest = new AdRequest.Builder()
                            .addTestDevice("84217760FD1D092D92F5FE072A2F1861")
                            .addTestDevice("19BA58A88672F3F9197685FEEB600EA7")
                            .addTestDevice("5E3D3DD85078633EE1836B9FC5FB4D89")
                            .build();
                    mAdView.loadAd(adRequest);
                    break;
            }
        }
    }
    private class ReloadAd implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST);
            Log.d(LOG_TAG, "Reload ad thread started");
            try {
                Thread.sleep(60000);
                if (adView != null && reloadAds && !adView.isLoading())
                    adView.loadAd();
            } catch (InterruptedException e){ }
        }
    }
}
