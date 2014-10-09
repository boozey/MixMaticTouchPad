package com.nakedape.mixmatictouchpad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

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
import java.util.HashMap;


public class LaunchPadActivity extends Activity {

    public static String TOUCHPAD_ID = "com.nakedape.mixmatictouchpad.touchpadid";
    public static String SAMPLE_PATH = "com.nakedape.mixmatictouchpad.samplepath";
    private static int GET_SAMPLE = 0;
    private boolean isEditMode = false;

    private Context context;
    private HashMap samples;
    private File homeDir;
    private int numTouchPads;
    private AudioManager am;

    private int selectedSampleID;
    private ActionMode mActionMode;
    private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.launch_pad_context, menu);
            if (samples.containsKey(selectedSampleID)) {
                Sample s = (Sample) samples.get(selectedSampleID);
                MenuItem item = menu.findItem(R.id.action_loop_mode);
                item.setChecked(s.getLoopMode());
            }
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()){
                case R.id.action_edit_sample:
                    Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                    intent.putExtra(TOUCHPAD_ID, selectedSampleID);
                    if (samples.containsKey(selectedSampleID)){
                        intent.putExtra(SAMPLE_PATH, homeDir.getAbsolutePath() + "/" + String.valueOf(selectedSampleID) + ".wav");
                    }
                    startActivityForResult(intent, GET_SAMPLE);
                    return true;
                case R.id.action_loop_mode:
                    if (item.isChecked()) {
                        item.setChecked(false);
                        if (samples.containsKey(selectedSampleID)) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(false);
                        }
                    }
                    else {
                        item.setChecked(true);
                        if (samples.containsKey(selectedSampleID)) {
                            Sample s = (Sample)samples.get(selectedSampleID);
                            s.setLoopMode(true);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    };
    private View.OnClickListener TouchPadClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isEditMode) {
                View oldView = findViewById(selectedSampleID);
                oldView.setSelected(false);
                selectedSampleID = v.getId();
                v.setSelected(true);
                    mActionMode = startActionMode(mActionModeCallback);
            }
            else {
                selectedSampleID = v.getId();
                if (samples.containsKey(v.getId())) {
                    Sample s = (Sample) samples.get(v.getId());
                    if (s.audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING)
                        s.stop();
                    if (s.hasPlayed())
                        s.reset();
                    s.play();
                }
            }
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
            File sampleFile = new File(homeDir, String.valueOf(data.getIntExtra(TOUCHPAD_ID, 0)) + ".wav");
            if (sampleFile.isFile()) // Delete it if it already exists
                sampleFile.delete();
            //boolean fileCopied = f.renameTo(sampleFile); // Copy new sample over
            try {
                CopyFile(f, sampleFile);
            } catch (IOException e){e.printStackTrace();}
            if (sampleFile.isFile()) { // If successful, add it to the sound pool
                samples.put(data.getIntExtra(TOUCHPAD_ID, 0), new Sample(sampleFile.getAbsolutePath()));
                TouchPad t = (TouchPad)findViewById(data.getIntExtra(TOUCHPAD_ID, 0));
                t.setBackgroundResource(R.drawable.launch_pad_blue);
                Log.d("Sample Id/Path", String.valueOf(sampleFile.getPath()));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_pad);
        context = this;
        homeDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/MixMatic");

        // Set up touch pads and load samples if present
        LinearLayout mainLayout = (LinearLayout)findViewById(R.id.mainLayout);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        samples = new HashMap();
        int id = 0;
        for (int i = 0; i < 6; i++){
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.HORIZONTAL);
            mainLayout.addView(l);
            for (int j = 0; j < 4; j++){
                TouchPad t = new TouchPad(this);
                t.setId(id);
                t.setWidth(metrics.widthPixels / 4);
                t.setHeight((metrics.heightPixels - 150) / 6);
                t.setOnClickListener(TouchPadClick);
                l.addView(t);
                if (homeDir.isDirectory()){
                    File sample = new File(homeDir, String.valueOf(id) + ".wav");
                    if (sample.isFile()){
                        samples.put(id, new Sample(sample.getAbsolutePath()));
                        t.setBackgroundResource(R.drawable.launch_pad_blue);
                    }
                }
                id++;
            }
        }
        numTouchPads = id;

        // Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
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
            return true;
        }
        else if (id == R.id.action_edit_mode) {
            if (isEditMode){
                isEditMode = false;
                item.setTitle(R.string.action_edit_mode);
            }
            else {
                isEditMode = true;
                item.setTitle(R.string.action_play_mode);
                mActionMode = startActionMode(mActionModeCallback);
            }
        }
        else if (id == R.id.action_stop){
            for (int i = 0; i < numTouchPads; i++) {
                if (samples.containsKey(i)) {
                    Sample s = (Sample) samples.get(i);
                    s.stop();
                }
            }

        }
        return super.onOptionsItemSelected(item);
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

    public class Sample{
        // Public fields
        public static final String LAUNCHMODE_GATE = "com.nakedape.mixmatictouchpad.launchmodegate";
        public static final String LAUNCHMODE_TRIGGER = "com.nakedape.mixmatictouchpad.launchmodetrigger";
        public int playId = -1;

        // Private fields
        private int id;
        private String path;
        private boolean loop = false;
        private int loopMode = 0;
        private String launchMode = LAUNCHMODE_TRIGGER;
        private int sampleByteLength;
        private boolean played = false;
        private AudioTrack audioTrack;

        // Constructors
        public Sample(String path){
            this.path = path;
            loadAudioTrack();
        }
        public Sample(String path, String launchMode, boolean loopMode){
            this.path = path;
            loop = loopMode;
            if (!setLaunchMode(launchMode))
                this.launchMode = LAUNCHMODE_TRIGGER;
            loadAudioTrack();
        }

        // Public methods
        public void setSoundPoolId(int id){this.id = id;}
        public int getSoundPoolId(){return id;}
        public String getPath(){return path;}
        public void setLoopMode(boolean loop){
            this.loop = loop;
            if (loop) {
                loopMode = -1;
                audioTrack.stop();
                audioTrack.reloadStaticData();
                audioTrack.setLoopPoints(0, sampleByteLength / 4, -1);
            }
            else {
                loopMode = 0;
                audioTrack.stop();
                audioTrack.reloadStaticData();
                audioTrack.setLoopPoints(0, 0, 0);
            }
        }
        public boolean getLoopMode(){
            return loop;
        }
        public int getLoopModeInt() {
            return loopMode;
        }
        public boolean setLaunchMode(String launchMode){
            if (launchMode.equals(LAUNCHMODE_GATE)){
                this.launchMode = LAUNCHMODE_GATE;
                return true;
            }
            else if (launchMode.equals(LAUNCHMODE_TRIGGER)){
                this.launchMode = LAUNCHMODE_TRIGGER;
                return true;
            }
            else
                return false;
        }
        public String getLaunchMode(){
            return launchMode;
        }
        public void play(){
            played = true;
            audioTrack.play();
        }
        public void stop(){
            audioTrack.stop();
        }
        public void reset(){
            audioTrack.reloadStaticData();
            played = false;
        }
        public boolean hasPlayed(){
            return played;
        }

        // Private methods
        private void loadAudioTrack(){
            File f = new File(path);
            if (f.isFile()){
                sampleByteLength = (int)f.length() - 44;
                audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                        44100,
                        AudioFormat.CHANNEL_OUT_STEREO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        sampleByteLength,
                        AudioTrack.MODE_STATIC);
                InputStream stream = null;
                try {
                    stream = new BufferedInputStream(new FileInputStream(f));
                    stream.skip(44);
                    byte[] bytes = new byte[sampleByteLength];
                    stream.read(bytes);
                    short[] shorts = new short[bytes.length / 2];
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    audioTrack.write(shorts, 0, shorts.length);
                    stream.close();
                } catch (FileNotFoundException e) {e.printStackTrace();}
                catch (IOException e) {e.printStackTrace();}
            }
        }
    }
}
