package com.nakedape.mixmatictouchpad;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
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
import java.util.HashMap;


public class LaunchPadActivity extends Activity {

    public static String TOUCHPAD_ID = "com.nakedape.mixmatictouchpad.touchpadid";
    private static int GET_SAMPLE = 0;
    private boolean isEditMode = false;

    private Context context;
    private HashMap samples;
    private int numTouchPads;
    private AudioManager am;
    private SoundPool soundPool;

    View.OnClickListener TouchPadClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (isEditMode) {
                Intent intent = new Intent(Intent.ACTION_SEND, null, context, SampleEditActivity.class);
                intent.putExtra(TOUCHPAD_ID, v.getId());
                startActivityForResult(intent, GET_SAMPLE);
            }
            else {
                if (samples.containsKey(v.getId())) {
                    float volume = am.getStreamVolume(AudioManager.STREAM_MUSIC) / am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    Sample s = (Sample) samples.get(v.getId());
                    soundPool.play(s.getSoundPoolId(), volume, volume, 1, 0, 1f);
                    Log.d("Soundpool", String.valueOf(s.getSoundPoolId()));
                }
            }
        }
    };
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
        if (requestCode == GET_SAMPLE && resultCode == RESULT_OK) {
            String path = data.getData().getPath();
            File f = new File(path);
            File homeDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC).getAbsolutePath() + "/MixMatic");
            if (!homeDir.isDirectory())
                homeDir.mkdir();
            File sampleFile = new File(homeDir, String.valueOf(data.getIntExtra(TOUCHPAD_ID, 0)) + ".wav");
            if (sampleFile.isFile())
                sampleFile.delete();
            boolean fileCopied = f.renameTo(sampleFile);
            if (fileCopied) {
                samples.put(data.getIntExtra(TOUCHPAD_ID, 0), new Sample(sampleFile.getAbsolutePath()));
                LoadSoundPool();
                Log.d("Sample Id/Path", String.valueOf(sampleFile.getPath()));
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch_pad);
        context = this;

        // Set up touch pads
        LinearLayout mainLayout = (LinearLayout)findViewById(R.id.mainLayout);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int id = 0;
        for (int i = 0; i < 6; i++){
            LinearLayout l = new LinearLayout(this);
            l.setOrientation(LinearLayout.HORIZONTAL);
            mainLayout.addView(l);
            for (int j = 0; j < 4; j++){
                TouchPad t = new TouchPad(this);
                t.setId(id);
                id++;
                t.setWidth(metrics.widthPixels / 4);
                t.setHeight((metrics.heightPixels - 150) / 6);
                t.setOnClickListener(TouchPadClick);
                l.addView(t);
            }
        }
        samples = new HashMap(id);
        numTouchPads = id;

        // Set up audio
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
    }

    private void LoadSoundPool(){
        soundPool.release();
        soundPool = new SoundPool(10, AudioManager.STREAM_MUSIC, 0);
        for (int i = 0; i < numTouchPads; i++){
            if (samples.containsKey(i)) {
                Sample s = (Sample) samples.get(i);
                s.setSoundPoolId(soundPool.load(s.getPath(), 10));
                Log.d("Sample Key", String.valueOf(i));
                Log.d("Sample Id", String.valueOf(s.getSoundPoolId()));
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
