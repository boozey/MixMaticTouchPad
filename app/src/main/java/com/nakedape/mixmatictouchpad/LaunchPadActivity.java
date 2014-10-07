package com.nakedape.mixmatictouchpad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
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
    private SoundPool soundPool;

    View.OnClickListener TouchPadClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isEditMode) {
                Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                intent.putExtra(TOUCHPAD_ID, v.getId());
                if (samples.containsKey(v.getId())){
                    intent.putExtra(SAMPLE_PATH, homeDir.getAbsolutePath() + "/" + String.valueOf(v.getId()) + ".wav");
                }
                startActivityForResult(intent, GET_SAMPLE);
            }
            else {
                if (samples.containsKey(v.getId())) {
                    float volume = (float)am.getStreamVolume(AudioManager.STREAM_MUSIC) / (float)am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    Sample s = (Sample) samples.get(v.getId());
                    soundPool.play(s.getSoundPoolId(), volume, volume, 1, 0, 1f);
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
                LoadSoundPool();
                TouchPad t = (TouchPad)findViewById(data.getIntExtra(TOUCHPAD_ID, 0));
                t.setBackgroundColor(Color.WHITE);
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
                        t.setBackgroundColor(Color.WHITE);
                    }
                }
                id++;
            }
        }
        numTouchPads = id;

        // Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool(24, AudioManager.STREAM_MUSIC, 0);
        LoadSoundPool();
    }

    private void LoadSoundPool(){
        soundPool.release();
        soundPool = new SoundPool(24, AudioManager.STREAM_MUSIC, 0);
        for (int i = 0; i < numTouchPads; i++){
            if (samples.containsKey(i)) {
                Sample s = (Sample) samples.get(i);
                s.setSoundPoolId(soundPool.load(s.getPath(), 1));
            }

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
            return true;
        }
        if (id == R.id.action_edit_mode) {
            if (isEditMode){
                isEditMode = false;
                item.setTitle(R.string.action_edit_mode);
            }
            else {
                isEditMode = true;
                item.setTitle(R.string.action_play_mode);
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
        private int id;
        private String path;
        public Sample(String path){
            this.path = path;
        }
        public void setSoundPoolId(int id){this.id = id;}
        public int getSoundPoolId(){return id;}
        public String getPath(){return path;}
    }
}
