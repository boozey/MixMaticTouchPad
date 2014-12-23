package com.nakedape.mixmaticlooppad;



import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;

import java.io.IOException;


/**
 * A simple {@link Fragment} subclass.
 *
 */
public class SamplePlayerFragment extends DialogFragment {

    public static final String WAV_PATH = "com.nakedape.mixmaticlooppad.wavpath";

    private static final int PROGRESS_UPDATE = 0;
    private static final int PLAY_COMPLETE = 1;

    private MediaPlayer mPlayer;
    private boolean isPaused;
    private ProgressBar progressBar;
    private ImageButton playButton;
    private Button positiveButton, negativeButton;
    private Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what){
                case PROGRESS_UPDATE:
                    progressBar.setProgress(msg.arg1);
                    break;
                case PLAY_COMPLETE:
                    progressBar.setProgress(0);
                    playButton.setSelected(false);
                    playButton.setBackgroundResource(R.drawable.button_play_large);
                    break;
            }
        }
    };
    private Button.OnClickListener playClickListener = new Button.OnClickListener(){
        @Override
        public void onClick(View v){
            playClick(v);
        }
    };
    private Button.OnClickListener saveClickListener = new Button.OnClickListener(){
        @Override
        public void onClick(View v){
            mListener.positiveButtonClick(v);
        }
    };
    private Button.OnClickListener cancelClickListener = new Button.OnClickListener(){
        @Override
        public void onClick(View v){
            mListener.negativeButtonClick(v);
        }
    };

    public interface SamplePlayerListener {
        public void positiveButtonClick(View v);
        public void negativeButtonClick(View v);
    }
    SamplePlayerListener mListener;
    public void setOnClickListener(SamplePlayerListener listener){
        mListener = listener;
    }

    public SamplePlayerFragment() {
        // Required empty public constructor
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState){
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setTitle(R.string.save_resample_msg);
        Bundle args = getArguments();
        String wavPath = args.getString(WAV_PATH);
        mPlayer = new MediaPlayer();
        try {
            mPlayer.setDataSource(wavPath);
            mPlayer.prepare();
        } catch (IOException e) {e.printStackTrace();}

        return  dialog;
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_sample_player, container, false);
        progressBar = (ProgressBar)view.findViewById(R.id.progressBar);
        progressBar.setMax(mPlayer.getDuration());
        playButton = (ImageButton)view.findViewById(R.id.playButton);
        playButton.setOnClickListener(playClickListener);
        positiveButton = (Button)view.findViewById(R.id.positiveButton);
        positiveButton.setOnClickListener(saveClickListener);
        negativeButton = (Button)view.findViewById(R.id.negativeButton);
        negativeButton.setOnClickListener(cancelClickListener);

        return view;
    }

    public void playClick(View v){
        if (playButton.isSelected()){
            playButton.setSelected(false);
            playButton.setBackgroundResource(R.drawable.button_play_large);
            isPaused = true;
            if (mPlayer.isPlaying())
                mPlayer.pause();
        }
        else {
            playButton.setSelected(true);
            playButton.setBackgroundResource(R.drawable.button_pause_large);
            isPaused = false;
            mPlayer.start();
            new Thread(new AudioProgressThread()).start();
        }
    }
    private class AudioProgressThread implements Runnable {
        @Override
        public void run() {
            do {
                Message m = mHandler.obtainMessage(PROGRESS_UPDATE);
                m.arg1 = mPlayer.getCurrentPosition();
                m.sendToTarget();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } while (mPlayer.isPlaying());
            if (!isPaused) {
                Message m = mHandler.obtainMessage(PLAY_COMPLETE);
                m.sendToTarget();
            }
        }
    }


}
