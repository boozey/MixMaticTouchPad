package com.nakedape.mixmatictouchpad;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
import android.net.Uri;
import android.os.*;
import android.os.Process;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;

import com.musicg.wave.Wave;
import com.musicg.wave.WaveFileManager;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import javazoom.jl.converter.Converter;
import javazoom.jl.decoder.JavaLayerException;


public class SampleEditActivity extends Activity {

    static final int REQUEST_MUSIC_GET = 0;
    static final int AUDIO_PROGRESS = 1;
    static final int AUDIO_PROCESSING_UPDATE = 2;
    static final int AUDIO_CONVERTED = 3;
    static final int AUDIO_PROCESSING_COMPLETE = 4;
    private String WAV_CACHE_PATH;
    private String WAV_SAMPLE_PATH = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC) + "//sample.wav";
    private float sampleRate = 44100;
    private int bufferSize = 1024;
    private int overlap = 512;
    private InputStream musicStream;
    private ProgressDialog dlg;
    private Context context;

    // Media player variables
    private Uri fullMusicUri;
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
            AudioSample view = (AudioSample)findViewById(R.id.spectralView);
            switch (msg.what){
                case AUDIO_PROGRESS:
                    view.updatePlayIndicator((double)msg.arg1/mPlayer.getDuration());
                    break;
                case AUDIO_PROCESSING_UPDATE:
                    dlg.setProgress(msg.arg1);
                    break;
                case AUDIO_PROCESSING_COMPLETE:
                    dlg.dismiss();
                    view.updateView();
                    mPlayer.release();
                    mPlayer = null;
                    break;
                case AUDIO_CONVERTED:
                    dlg.dismiss();
                    // Display indeterminate progress dialog
                    dlg = new ProgressDialog(context);
                    dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dlg.setIndeterminate(false);
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
                Button b = (Button)findViewById(R.id.buttonPlay);
                b.setEnabled(false);
                mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                try {
                    mPlayer.setDataSource(getApplicationContext(), fullMusicUri);
                    mPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            Button b = (Button)findViewById(R.id.buttonPlay);
                            b.setEnabled(true);
                        }
                    });
                    mPlayer.prepareAsync();
                } catch (IOException e){
                    e.printStackTrace();
                }
                // Display indeterminate progress dialog
                dlg = new ProgressDialog(this);
                dlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dlg.setIndeterminate(true);
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

    public void LoadAudioFile(){
        // Allow user to select an audio file
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
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
                if (mPlayer != null) {
                    if (mPlayer.isPlaying()){
                        mPlayer.pause();
                        b.setText("Play");
                    }
                    else {
                        // Start playback.
                        b.setText("Pause");
                        AudioSample v = (AudioSample) findViewById(R.id.spectralView);
                        // Start playing from beginning of selection
                        if (v.getSelectionStart() > 0)
                            mPlayer.seekTo((int) Math.round(v.getSelectionStart()));
                        mPlayer.start();
                        //new Thread(new PlayIndicator()).start();
                    }
                }
                else { // Start a new instance
                    mPlayer = new MediaPlayer();
                    mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    try {
                        //mPlayer.setDataSource(getApplicationContext(), fullMusicUri);
                        b.setText("Pause");
                        mPlayer.setDataSource(context, Uri.parse(getBaseContext().getCacheDir().getAbsolutePath() + "temp.wav"));
                        mPlayer.prepare();
                        AudioSample v = (AudioSample) findViewById(R.id.spectralView);
                        // Start playing from beginning of selection
                        if (v.getSelectionStart() > 0)
                            mPlayer.seekTo((int) Math.round(v.getSelectionStart()));
                        mPlayer.start();
                    } catch (IOException e){
                        e.printStackTrace();
                    }
                }
            }
    }

    public void TarsosPlay(View view){
        AudioSample sample = (AudioSample)findViewById(R.id.spectralView);
        InputStream wavStream;
        try{
            wavStream = new FileInputStream(WAV_CACHE_PATH);
            sample.Play(wavStream, sample.getSelectionStart(), sample.getSelectionEnd());

        } catch (FileNotFoundException e){e.printStackTrace();}
    }

    public void Save(View view){
        AudioSample sample = (AudioSample)findViewById(R.id.spectralView);
        InputStream wavStream;
        try{
            wavStream = new FileInputStream(WAV_CACHE_PATH);
            sample.WriteSelectionToFile(wavStream, WAV_SAMPLE_PATH, sample.getSelectionStart(), sample.getSelectionEnd());
            byte[] audioSample = new byte[512];
            

        } catch (FileNotFoundException e){e.printStackTrace();}
        /*
        Intent result = new Intent("com.example.RESULT_ACTION", Uri.parse("content://result_uri"));
        setResult(Activity.RESULT_OK, result);
        finish();
        */
    }

    public void PlayTrimmedWAV(View view){
        SoundPool pool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        int id = pool.load(WAV_SAMPLE_PATH, 1);
        pool.play(id,1, 1, 1, 0, 1f);
        Wave wave = new Wave(WAV_SAMPLE_PATH);
        Log.d("Wave file size", String.valueOf(wave.length()));
    }

    public void ZoomIn(View view){
        AudioSample a = (AudioSample)findViewById(R.id.spectralView);
        a.zoomSelection();
    }

    public void ZoomOut(View view){
        AudioSample a = (AudioSample)findViewById(R.id.spectralView);
        a.zoomExtents();
    }

    public void ShowBeatsClicked(View view){
        AudioSample sample = (AudioSample)findViewById(R.id.spectralView);
        sample.setShowBeats(((CheckBox) view).isChecked());
    }

    private void ChangeBeatThreshold(int level){

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_edit);
        context = this;
        WAV_CACHE_PATH = getExternalCacheDir() + "//temp.wav";
        AudioSample sample = (AudioSample)findViewById(R.id.spectralView);
        sample.setFocusable(true);
        sample.setFocusableInTouchMode(true);
        sample.setOnTouchListener(sample);

        //Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        mPlayer = new MediaPlayer();
        Button b = (Button)findViewById(R.id.buttonPlay);
        b.setEnabled(false); //Disabled until a file is loaded

        // Setup beat threshold bar
        SeekBar bar = (SeekBar)findViewById(R.id.beatSensitivity);
        bar.setProgress(30);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ChangeBeatThreshold(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

    }

    @Override
    protected void onDestroy(){
        if (mPlayer != null){
            if (mPlayer.isPlaying())
                mPlayer.stop();
            mPlayer.release();
            mPlayer = null;
        }
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
        if (id == R.id.action_settings) {
            return true;
        }
        else if (id == R.id.action_load_file){
            LoadAudioFile();
        }
        return super.onOptionsItemSelected(item);
    }

    // Thread to convert mp3 to wav
    public class ConvertMp3Thread implements Runnable{
        @Override
        public void run(){
            // Convert mp3 to wav
            Converter c = new Converter();
            try {
                c.convert(musicStream, WAV_CACHE_PATH, null, null);
            } catch (JavaLayerException e){e.printStackTrace();}
            Message m = mHandler.obtainMessage(AUDIO_CONVERTED);
            m.sendToTarget();
        }
    }

    // Thread to process audio
    public class LoadAudioThread implements Runnable{
        @Override
        public void run(){
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            InputStream wavStream;
            try {
                wavStream = new FileInputStream(WAV_CACHE_PATH);
                TarsosDSPAudioFormat audioFormat = new TarsosDSPAudioFormat(sampleRate, 16, 2, false, false);
                AudioSample v = (AudioSample)findViewById(R.id.spectralView);
                v.LoadAudio(wavStream, audioFormat, bufferSize, overlap, 0.3);
            } catch (FileNotFoundException e){e.printStackTrace();}


            try {
                musicStream.close();
            }catch (IOException e){e.printStackTrace();}

            Message m = mHandler.obtainMessage(AUDIO_PROCESSING_COMPLETE);
            m.sendToTarget();

        }
    }

    // Thread to update audio processing progress
    public class AudioProcessUpdate implements Runnable{
        @Override
        public void run(){
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            AudioSample view = (AudioSample)findViewById(R.id.spectralView);
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
            AudioSample view = (AudioSample)findViewById(R.id.spectralView);
            int selectionEnd = (int)Math.round(view.getSelectionEnd() * 1000);
            if (selectionEnd < 0)
                selectionEnd = mPlayer.getDuration();
            while (mPlayer.getCurrentPosition() < selectionEnd){
                try {
                    Message m = mHandler.obtainMessage(AUDIO_PROGRESS);
                    m.arg1 = mPlayer.getCurrentPosition();
                    m.sendToTarget();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            mPlayer.pause();
        }
    }
}
