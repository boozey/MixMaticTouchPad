package com.nakedape.mixmatictouchpad;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

import javazoom.jl.converter.Converter;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.Obuffer;


public class SampleEditActivity extends Activity {

    static final int REQUEST_MUSIC_GET = 0;
    static final int AUDIO_PLAY_PROGRESS = 1;
    static final int AUDIO_PLAY_COMPLETE = 2;
    static final int AUDIO_PROCESSING_UPDATE = 3;
    static final int AUDIO_PROCESSING_COMPLETE = 4;
    static final int MP3_CONVERTER_UPDATE = 5;
    static final int MP3_CONVERSION_COMPLETE = 6;

    private String WAV_CACHE_PATH;
    private float sampleRate = 44100;
    private int sampleLength;
    private InputStream musicStream;
    private ProgressDialog dlg;
    private Context context;
    private int sampleId;
    private AudioSampleData savedData;

    // Media player variables
    private Uri fullMusicUri;
    private boolean loop;
    private boolean continuePlaying;
    private MediaPlayer mPlayer;
    private AudioManager am;
    private final AudioManager.OnAudioFocusChangeListener afChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                // Pause playback
                if (mPlayer != null)
                mPlayer.pause();
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                // Resume playback
                if (mPlayer != null)
                    mPlayer.start();
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
                am.abandonAudioFocus(afChangeListener);
                // Stop playback
                if (mPlayer != null) {
                    mPlayer.stop();
                    mPlayer.release();
                    mPlayer = null;
                }
            }
        }
    };

    Handler mHandler = new Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg){
            AudioSampleView audioSampleView = (AudioSampleView)findViewById(R.id.spectralView);
            switch (msg.what){
                case AUDIO_PLAY_PROGRESS:
                    audioSampleView.updatePlayIndicator((double)msg.arg1 / 1000);
                    break;
                case AUDIO_PLAY_COMPLETE:
                    audioSampleView.isPlaying = false;
                    Button b = (Button)findViewById(R.id.buttonPlay);
                    b.setText("Play");
                    break;
                case AUDIO_PROCESSING_UPDATE:
                    dlg.setProgress(msg.arg1);
                    break;
                case AUDIO_PROCESSING_COMPLETE:
                    dlg.dismiss();
                    audioSampleView.updateView();
                    mPlayer.release();
                    mPlayer = null;
                    break;
                case MP3_CONVERTER_UPDATE:
                    dlg.setProgress(msg.arg1);
                    break;
                case MP3_CONVERSION_COMPLETE:
                    dlg.dismiss();
                    // Display determinate progress dialog
                    dlg = new ProgressDialog(context);
                    dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dlg.setIndeterminate(false);
                    InputStream wavStream;
                    long len = 0;
                    try {
                        File file = new File(WAV_CACHE_PATH);
                        wavStream = new FileInputStream(file);
                        byte[] lenInt = new byte[4];
                        wavStream.skip(40);
                        wavStream.read(lenInt, 0, 4);
                        ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
                        len = bb.getInt();
                        sampleLength = (int)len / 4 / (int)sampleRate;
                        Log.d("Wave duration", String.valueOf(sampleLength));
                        wavStream.close();
                    } catch (IOException e) {e.printStackTrace();}
                    if (len > 0)
                        dlg.setMax(sampleLength);
                    else
                        dlg.setMax(Math.round(mPlayer.getDuration() / 1000));
                    dlg.setCancelable(false);
                    dlg.setMessage("Processing Audio");
                    dlg.show();
                    // Process audio
                    new Thread(new LoadAudioThread()).start();
                    new Thread(new AudioProcessUpdate()).start();
                    break;
            }
        }
    };


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_MUSIC_GET && resultCode == RESULT_OK) {
            fullMusicUri = data.getData();
            try {
                //Load audio stream
                ContentResolver contentResolver = this.getContentResolver();
                musicStream = contentResolver.openInputStream(fullMusicUri);

                //Load audio file to play
                LoadMediaPlayer(fullMusicUri);

                // Display indeterminate progress dialog
                dlg = new ProgressDialog(this);
                if (sampleLength > 0){
                    dlg.setIndeterminate(false);
                    dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dlg.setMax(sampleLength * 1000 / 26);
                }
                else {
                    dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                    dlg.setIndeterminate(true);
                }
                dlg.setCancelable(false);
                dlg.setMessage("Converting MP3 to wav");
                dlg.show();
                new Thread(new ConvertMp3Thread()).start();
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    public void LoadMediaPlayer(Uri uri){
        Button b = (Button)findViewById(R.id.buttonPlay);
        b.setEnabled(false);
        if (mPlayer != null){
            if (mPlayer.isPlaying()) mPlayer.pause();
            mPlayer.release();
            mPlayer = null;
        }
        mPlayer = new MediaPlayer();
        mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mPlayer.setDataSource(getApplicationContext(), uri);
            mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    Button b = (Button)findViewById(R.id.buttonPlay);
                    b.setEnabled(true);
                }
            });
            mPlayer.prepare();
            sampleLength = mPlayer.getDuration() / 1000;
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public void SelectMp3File(){
        // Allow user to select an audio file
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*.mp3");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, REQUEST_MUSIC_GET);
        }
    }

    public void Play(View view){
            // Request audio focus for playback
            int result = am.requestAudioFocus(afChangeListener,
                    // Use the music stream.
                    AudioManager.STREAM_MUSIC,
                    // Request permanent focus.
                    AudioManager.AUDIOFOCUS_GAIN);

            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Button b = (Button) findViewById(R.id.buttonPlay);
                AudioSampleView audioSampleView = (AudioSampleView) findViewById(R.id.spectralView);
                if (mPlayer != null) {
                    if (mPlayer.isPlaying()){ // If already playing, pause
                        mPlayer.pause();
                        audioSampleView.isPlaying = false;
                        continuePlaying = false;
                        b.setText("Play");
                    }
                    else { // If not playing, start
                        b.setText("Pause");
                        audioSampleView.isPlaying = true;
                        continuePlaying = true;
                        // Start playing from beginning of selection
                        if (audioSampleView.getSelectionStartTime() > 0)
                            mPlayer.seekTo((int)(audioSampleView.getSelectionStartTime() * 1000));
                        mPlayer.start();
                        new Thread(new PlayIndicator()).start();
                    }
                }
                else { // Start a new instance
                    mPlayer = new MediaPlayer();
                    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    try {
                        b.setText("Pause");
                        audioSampleView.isPlaying = true;
                        continuePlaying = true;
                        mPlayer.setDataSource(context, Uri.parse(WAV_CACHE_PATH));
                        mPlayer.prepare();
                        if (audioSampleView.getSelectionStartTime() > 0)
                            mPlayer.seekTo((int)(audioSampleView.getSelectionStartTime() * 1000));
                        mPlayer.start();
                        new Thread(new PlayIndicator()).start();
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
    }

    public void TarsosPlay(View view){
        AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        sample.Play(WAV_CACHE_PATH, sample.getSelectionStartTime(), sample.getSelectionEndTime());
    }

    public void Save(View view){
        AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        File temp = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "sample.wav");
        if (temp.isFile())
            temp.delete();
        if (mPlayer != null){
            if (mPlayer.isPlaying()) mPlayer.pause();
            mPlayer.release();
            mPlayer = null;
        }

        Intent result = new Intent("com.nakedape.mixmatictouchpad.RESULT_ACTION", Uri.parse(sample.getSamplePath()));
        result.putExtra(LaunchPadActivity.TOUCHPAD_ID, sampleId);
        result.putExtra(LaunchPadActivity.COLOR, sample.color);
        setResult(Activity.RESULT_OK, result);
        finish();

    }

    public void Trim(){
        if (mPlayer != null) {
            if (mPlayer.isPlaying()) mPlayer.pause();
            mPlayer.release();
            mPlayer = null;
        }
        AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        sample.TrimToSelection(sample.getSelectionStartTime(), sample.getSelectionEndTime());
        LoadMediaPlayer(Uri.parse(sample.getSamplePath()));
    }

    public void ZoomIn(View view){
        AudioSampleView a = (AudioSampleView)findViewById(R.id.spectralView);
        a.zoomSelection();
    }

    public void ZoomOut(View view){
        AudioSampleView a = (AudioSampleView)findViewById(R.id.spectralView);
        a.zoomExtents();
    }

    private void ChangeBeatThreshold(int level){

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_edit);

        // Store reference to activity context to use inside event handlers
        context = this;
        // Store a reference to the path for the temporary cache of the wav file
        WAV_CACHE_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "/cache.wav";

        // Setup audiosample view to handle touch events
        AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        sample.setFocusable(true);
        sample.setFocusableInTouchMode(true);
        sample.setOnTouchListener(sample);

        //Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mPlayer = new MediaPlayer();
        Button b = (Button)findViewById(R.id.buttonPlay);
        b.setEnabled(false); //Disabled until a file is loaded

        // Get data from intent
        Intent intent = getIntent();
        sampleId = intent.getIntExtra(LaunchPadActivity.TOUCHPAD_ID, 0);
        // find the retained fragment on activity restarts
        FragmentManager fm = getFragmentManager();
        savedData = (AudioSampleData) fm.findFragmentByTag("data");
        if (savedData != null){
            sample.loadAudioSampleData(savedData);
            loop = savedData.getLoop();
            LoadMediaPlayer(Uri.parse(savedData.getSamplePath()));
        }
        else if (intent.hasExtra(LaunchPadActivity.SAMPLE_PATH)){
            savedData = new AudioSampleData();
            fm.beginTransaction().add(savedData, "data").commit();
            LoadSampleFromIntent(intent);
        }
        else{
            // If the cache file already exists from a previous edit, delete it
            File temp = new File(WAV_CACHE_PATH);
            if (temp.isFile())
                temp.delete();
            savedData = new AudioSampleData();
            fm.beginTransaction().add(savedData, "data").commit();
            SelectMp3File();
        }
    }

    private void LoadSampleFromIntent(Intent intent){
        AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        sample.setColor(intent.getIntExtra(LaunchPadActivity.COLOR, 0));
        File temp = new File(intent.getStringExtra(LaunchPadActivity.SAMPLE_PATH));
        if (temp.isFile()){ // If a sample is being passed, load it and process
            File loadedSample = new File(WAV_CACHE_PATH);
            try {
                CopyFile(temp, loadedSample);
            }catch (IOException e){e.printStackTrace();}
            LoadMediaPlayer(Uri.parse(WAV_CACHE_PATH));
            // Display determinate progress dialog
            dlg = new ProgressDialog(context);
            dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            dlg.setIndeterminate(false);
            InputStream wavStream;
            long len = 0;
            try {
                File file = new File(WAV_CACHE_PATH);
                wavStream = new FileInputStream(file);
                byte[] lenInt = new byte[4];
                wavStream.skip(40);
                wavStream.read(lenInt, 0, 4);
                ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
                len = bb.getInt();
                sampleLength = (int)len / 4 / (int)sampleRate;
                Log.d("Wave duration", String.valueOf(sampleLength));
                wavStream.close();
            } catch (IOException e) {e.printStackTrace();}

            if (len > 0)
                dlg.setMax(sampleLength);
            else
                dlg.setMax(Math.round(mPlayer.getDuration() / 1000));
            dlg.setCancelable(false);
            dlg.setMessage("Processing Audio");
            dlg.show();
            // Process audio
            new Thread(new LoadAudioThread()).start();
            new Thread(new AudioProcessUpdate()).start();
        }

    }

    @Override
    protected void onDestroy(){
        if (mPlayer != null){
            if (mPlayer.isPlaying())
                mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
        AudioSampleView sampleView = (AudioSampleView)findViewById(R.id.spectralView);
        sampleView.saveAudioSampleData(savedData);
        savedData.setLoop(loop);
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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
                return true;
            case R.id.action_load_file:
                SelectMp3File();
                return true;
            case R.id.action_trim_wav:
                Trim();
                return true;
            case R.id.action_show_beats:
                if (item.isChecked()) {
                    item.setChecked(false);
                    sample.setShowBeats(false);
                }
                else {
                    item.setChecked(true);
                    sample.setShowBeats(true);
                }
                return true;
            case R.id.action_loop_selection:
                if (item.isChecked()){
                    loop = false;
                    item.setChecked(false);
                }
                else {
                    loop = true;
                    item.setChecked(true);
                }
                return true;
            case R.id.action_pick_color:
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
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * copy file from source to destination
     *
     * @param src source
     * @param dst destination
     * @throws java.io.IOException in case of any problems
     */
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

    // Thread to convert mp3 to wav
    public class ConvertMp3Thread implements Runnable{
        @Override
        public void run(){
            // Convert mp3 to wav
            Converter c = new Converter();
            Converter.ProgressListener listener = new Converter.ProgressListener() {
                @Override
                public void converterUpdate(int updateID, int param1, int param2) {
                }

                @Override
                public void parsedFrame(int frameNo, Header header) {

                }

                @Override
                public void readFrame(int frameNo, Header header) {

                }

                @Override
                public void decodedFrame(int frameNo, Header header, Obuffer o) {
                    Message m = mHandler.obtainMessage(MP3_CONVERTER_UPDATE);
                    m.arg1 = frameNo;
                    m.sendToTarget();
                }

                @Override
                public boolean converterException(Throwable t) {
                    return false;
                }
            };
            try {
                c.convert(musicStream, WAV_CACHE_PATH, listener, null);
            } catch (JavaLayerException e){e.printStackTrace();}
            Message m = mHandler.obtainMessage(MP3_CONVERSION_COMPLETE);
            m.sendToTarget();
        }
    }

    // Thread to process audio
    public class LoadAudioThread implements Runnable{
        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            AudioSampleView v = (AudioSampleView) findViewById(R.id.spectralView);
            v.LoadAudio(WAV_CACHE_PATH);

            Message m = mHandler.obtainMessage(AUDIO_PROCESSING_COMPLETE);
            m.sendToTarget();
        }
    }

    // Thread to update audio processing progress
    public class AudioProcessUpdate implements Runnable{
        @Override
        public void run(){
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            AudioSampleView view = (AudioSampleView)findViewById(R.id.spectralView);
            while (view.dispatcher == null){
                try {
                    Thread.sleep(100);
                }catch (InterruptedException e){e.printStackTrace();}
            }
            double l = mPlayer.getDuration() / 1000;
            do {
                Message m = mHandler.obtainMessage(AUDIO_PROCESSING_UPDATE);
                m.arg1 = Math.round(view.dispatcher.secondsProcessed());
                m.sendToTarget();
                try {
                    Thread.sleep(100);
                }catch (InterruptedException e){e.printStackTrace();}
            } while (view.dispatcher.secondsProcessed() < l);
        }
    }

    // Thread to update play indicator in waveform view
    public class PlayIndicator implements Runnable {
        @Override
        public void run(){
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            AudioSampleView audioSampleView = (AudioSampleView)findViewById(R.id.spectralView);
            audioSampleView.isPlaying = true;
            do {
                while (mPlayer.getCurrentPosition() < Math.round(audioSampleView.getSelectionEndTime() * 1000) && mPlayer.isPlaying()) {
                    try {
                        Message m = mHandler.obtainMessage(AUDIO_PLAY_PROGRESS);
                        m.arg1 = mPlayer.getCurrentPosition();
                        m.sendToTarget();
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                mPlayer.seekTo((int)Math.round(audioSampleView.getSelectionStartTime() * 1000));
            } while (loop && continuePlaying);
            mPlayer.pause();
            Message m = mHandler.obtainMessage(AUDIO_PLAY_COMPLETE);
            m.sendToTarget();
        }

        public Thread getCurrentThread(){
            return Thread.currentThread();
        }
    }
}
