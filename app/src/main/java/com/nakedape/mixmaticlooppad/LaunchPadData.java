package com.nakedape.mixmaticlooppad;

import android.app.Fragment;
import android.os.Bundle;
import android.util.SparseArray;

import java.util.ArrayList;

/**
 * Created by Nathan on 10/11/2014.
 */
public class LaunchPadData extends Fragment {

    private SparseArray<LaunchPadActivity.Sample> samples;
    private long counter;
    private boolean isPlaying;
    private boolean isRecording;
    private boolean isEditMode;
    private ArrayList<Integer> activePads;
    private ArrayList<LaunchPadActivity.LaunchEvent> launchEvents;
    private int playEventIndex;
    private ArrayList<Integer> padIds;
    private long recordingEndTime;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // retain this fragment
        setRetainInstance(true);
    }

    public void setSamples(SparseArray<LaunchPadActivity.Sample> samples){this.samples = samples;}
    public SparseArray<LaunchPadActivity.Sample> getSamples() {return samples;}
    public void setCounter(long counter) {this.counter = counter;}
    public long getCounter() {return counter;}
    public void setPlaying(boolean isPlaying) {this.isPlaying = isPlaying;}
    public boolean isPlaying() {return isPlaying;}
    public void setRecording(boolean isRecording) {this.isRecording = isRecording;}
    public boolean isRecording() {return  isRecording;}
    public void setEditMode(boolean isEditMode) {this.isEditMode = isEditMode;}
    public boolean isEditMode() {return isEditMode;}
    public void setActivePads(ArrayList<Integer> activePads) {this.activePads = activePads;}
    public ArrayList<Integer> getActivePads() {return activePads;}
    public void setLaunchEvents(ArrayList<LaunchPadActivity.LaunchEvent> launchEvents) {this.launchEvents = launchEvents;}
    public ArrayList<LaunchPadActivity.LaunchEvent> getLaunchEvents() {return launchEvents;}
    public void setPlayEventIndex(int playEventIndex) {this.playEventIndex = playEventIndex;}
    public int getPlayEventIndex() {return playEventIndex;}
    public void setPadIds(ArrayList<Integer> padIds) {this.padIds = padIds;}
    public ArrayList<Integer> getPadIds() {return  padIds;}
    public void setRecordingEndTime(long recordingEndTime) {this.recordingEndTime = recordingEndTime;}
    public long getRecordingEndTime() {return recordingEndTime;}
}
