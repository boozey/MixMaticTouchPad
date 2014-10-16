package com.nakedape.mixmatictouchpad;

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

import javazoom.jl.converter.WaveFile;


public class SampleEditActivity extends Activity {

    private static final String LOG_TAG = "MixMatic Sample Edit Activity";

    static final int REQUEST_MUSIC_GET = 0;
    static final int AUDIO_PLAY_PROGRESS = 1;
    static final int AUDIO_PLAY_COMPLETE = 2;
    static final int AUDIO_PROCESSING_UPDATE = 3;
    static final int AUDIO_PROCESSING_COMPLETE = 4;
    static final int MP3_CONVERTER_UPDATE = 5;
    static final int MP3_CONVERSION_COMPLETE = 6;

    private String WAV_CACHE_PATH;
    private File CACHE_PATH;
    private SharedPreferences pref;
    private float sampleRate = 44100;
    private int sampleLength;
    private long encodedFileSize;
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
    private int sampleId;
    private int numSlices = 1;
    private AudioSampleData savedData;

    // Media player variables
    private Uri fullMusicUri;
    private MediaFormat mediaFormat;
    private boolean loop;
    private boolean continuePlaying;
    private boolean continueProcessing;
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
                    LoadMediaPlayer(Uri.parse(WAV_CACHE_PATH));
                    break;
                case MP3_CONVERTER_UPDATE:
                    dlg.setProgress(msg.arg1);
                    break;
                case MP3_CONVERSION_COMPLETE:
                    dlg.dismiss();
                    // Display determinate progress dialog
                    dlg = new ProgressDialog(context);
                    dlg.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    dlg.setIndeterminate(true);
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
                        wavStream.close();
                    } catch (IOException e) {e.printStackTrace();}
                    if (len > 0)
                        dlg.setMax(sampleLength);
                    else
                        dlg.setMax(Math.round(mPlayer.getDuration() / 1000));
                    dlg.setCancelable(true);
                    dlg.setCanceledOnTouchOutside(false);
                    dlgCanceled = false;
                    dlg.setOnCancelListener(dlgCancelListener);
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
                mp3ConvertThread = new Thread(new DecodeAudioThread());
                mp3ConvertThread.start();
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

    public void SelectAudioFile(){
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
        sample.Play(sample.getSelectionStartTime(), sample.getSelectionEndTime());
    }

    public void Save(View view){
        final AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        File temp = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "sample.wav");
        if (temp.isFile())
            temp.delete();
        if (mPlayer != null){
            if (mPlayer.isPlaying()) mPlayer.pause();
            mPlayer.release();
            mPlayer = null;
        }
        if (numSlices > 1) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setMessage(getString(R.string.slice_size_warning, numSlices, sample.sampleLength / numSlices));
            builder.setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    String[] slicePaths = sample.Slice(numSlices);
                    Intent result = new Intent("com.nakedape.mixmatictouchpad.RESULT_ACTION");
                    result.putExtra(LaunchPadActivity.NUM_SLICES, numSlices);
                    result.putExtra(LaunchPadActivity.COLOR, sample.color);
                    result.putExtra(LaunchPadActivity.SLICE_PATHS, slicePaths);
                    setResult(Activity.RESULT_OK, result);
                    finish();
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
        else {
            Intent result = new Intent("com.nakedape.mixmatictouchpad.RESULT_ACTION", Uri.parse(sample.getSamplePath()));
            result.putExtra(LaunchPadActivity.TOUCHPAD_ID, sampleId);
            result.putExtra(LaunchPadActivity.COLOR, sample.color);
            setResult(Activity.RESULT_OK, result);
            finish();
        }

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sample_edit);
        if (getExternalCacheDir() != null)
            CACHE_PATH = getExternalCacheDir();
        else
            CACHE_PATH = getCacheDir();

        PreferenceManager.setDefaultValues(this, R.xml.sample_edit_preferences, true);
        pref = PreferenceManager.getDefaultSharedPreferences(this);
        // Store reference to activity context to use inside event handlers
        context = this;
        // Store a reference to the path for the temporary cache of the wav file
        WAV_CACHE_PATH = CACHE_PATH.getAbsolutePath() + "/cache.wav";

        // Setup audiosample view to handle touch events
        AudioSampleView sample = (AudioSampleView)findViewById(R.id.spectralView);
        sample.setCACHE_PATH(CACHE_PATH.getAbsolutePath());
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
            if (savedData.getSamplePath() != null)
                LoadMediaPlayer(Uri.parse(savedData.getSamplePath()));
        }
        else if (intent.hasExtra(LaunchPadActivity.SAMPLE_PATH)){
            savedData = new AudioSampleData();
            fm.beginTransaction().add(savedData, "data").commit();
            LoadSampleFromIntent(intent);
        }
        else if (intent.hasExtra(LaunchPadActivity.NUM_SLICES)){
            numSlices = intent.getIntExtra(LaunchPadActivity.NUM_SLICES, 1);
            // If the cache file already exists from a previous edit, delete it
            File temp = new File(WAV_CACHE_PATH);
            if (temp.isFile())
                temp.delete();
            savedData = new AudioSampleData();
            fm.beginTransaction().add(savedData, "data").commit();
            b = (Button)findViewById(R.id.buttonSave);
            b.setText("Slice");
            SelectAudioFile();
        }
        else{
            // If the cache file already exists from a previous edit, delete it
            File temp = new File(WAV_CACHE_PATH);
            if (temp.isFile())
                temp.delete();
            savedData = new AudioSampleData();
            fm.beginTransaction().add(savedData, "data").commit();
            SelectAudioFile();
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
        }

    }

    @Override
    protected void onDestroy(){
        dlgCanceled = true;
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
        getMenuInflater().inflate(R.menu.sample_edit, menu);
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
                SelectAudioFile();
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
            case R.id.action_play_tarsos:
                sample.Play(sample.getSelectionStartTime(), sample.getSelectionEndTime());
                return true;
            default:
                return super.onOptionsItemSelected(item);
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

    /** Thread to convert mp3 to wav
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
*/ //Old mp3 decode thread

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

    public class DecodeAudioThread implements Runnable {

        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec;
        long TIMEOUT_US = 1000;
        ByteBuffer[] codecInputBuffers;
        ByteBuffer[] codecOutputBuffers;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        Uri sourceUri = fullMusicUri;
        WaveFile waveFile = new WaveFile();
        int bytesProcessed = 0;

        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            ContentResolver contentResolver = context.getContentResolver();
            try {
                AssetFileDescriptor fd = contentResolver.openAssetFileDescriptor(sourceUri, "r");
                extractor.setDataSource(fd.getFileDescriptor());
                fd.close();
            } catch (IOException e) {e.printStackTrace();}
            MediaFormat format = extractor.getTrackFormat(0);
            String mime = format.getString(MediaFormat.KEY_MIME);
            File temp = new File(WAV_CACHE_PATH);
            if (temp.isFile())
                temp.delete();
            waveFile.OpenForWrite(WAV_CACHE_PATH,
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    (short)(8 * format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)),
                    (short)format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
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
                    // Upddate progress
                    Message m = mHandler.obtainMessage(MP3_CONVERTER_UPDATE);
                    bytesProcessed += sampleSize;
                    m.arg1 = bytesProcessed;
                    m.sendToTarget();
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
            }while (!sawInputEOS && !dlgCanceled);
            waveFile.Close();
            codec.stop();
            codec.release();
            codec = null;
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
            double beatThreshold = (double)pref.getInt("pref_beat_threshold", 30) / 100;
            v.setBeatThreshold(beatThreshold);
            v.createWaveForm(WAV_CACHE_PATH);
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
            double duration = mPlayer.getDuration() / 1000;
            do {
                Message m = mHandler.obtainMessage(AUDIO_PROCESSING_UPDATE);
                m.arg1 = Math.round(view.dispatcher.secondsProcessed());
                m.sendToTarget();
                try {
                    Thread.sleep(100);
                }catch (InterruptedException e){e.printStackTrace();}
            } while (view.dispatcher.secondsProcessed() < duration && !dlgCanceled);
            if (dlgCanceled){
                view.dispatcher.stop();
            }
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
                while (mPlayer.getCurrentPosition() < Math.round(audioSampleView.getSelectionEndTime() * 1000)
                        && mPlayer.getCurrentPosition() >= Math.round(audioSampleView.getSelectionStartTime() * 1000)
                        && mPlayer.isPlaying()) {
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
