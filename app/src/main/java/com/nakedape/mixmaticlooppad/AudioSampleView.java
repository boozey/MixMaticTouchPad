package com.nakedape.mixmaticlooppad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import javazoom.jl.converter.WaveFile;

/**
 * Created by Nathan on 8/31/2014.
 */
public class AudioSampleView extends View implements View.OnTouchListener {

    public static final int DEFAULT_SELECTION_MODE = 0;
    public static final int BEAT_SELECTION_MODE = 1;
    public static final int BEAT_MOVE_MODE = 2;

    private static final String LOG_TAG = "MixMatic AudioSampleView";

    private String CACHE_PATH;
    private String samplePath;
    private BeatInfo selectedBeat;
    public double sampleLength;
    private double selectionStartTime, selectionEndTime, windowStartTime, windowEndTime;
    private int sampleRate = 44100;
    private int selectionMode;
    private TarsosDSPAudioFormat audioFormat = new TarsosDSPAudioFormat(sampleRate, 16, 2, false, false);
    private Paint paintBrush = new Paint(), paintSelect = new Paint(), paintBackground = new Paint();
    private LinearGradient gradient;
    private float selectStart, selectEnd;
    private List<Line> waveFormData = new ArrayList<Line>();
    private List<BeatInfo> beatsData = new ArrayList<BeatInfo>();
    private List<Line> waveFormRender = new ArrayList<Line>();
    private List<BeatInfo> beatsRender = new ArrayList<BeatInfo>();
    private Line playPos = new Line(0, 0);
    public boolean isPlaying = false;
    private boolean showBeats = false;
    public boolean isLoading = false;
    public int color = 0;
    private String backgroundColor = "#ff000046";
    private String foregroundColor = "#0000FF";

    public AudioSampleView(Context context) {
        super(context);
    }
    public AudioSampleView(Context context, AttributeSet attrs){
        super(context, attrs);
    }
    public AudioSampleView(Context context, AttributeSet attrs, int defStyle){
        super (context, attrs, defStyle);
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.setOnTouchListener(this);
        setDrawingCacheEnabled(true);
    }

    public void setSelectionMode(int selectionMode){
        this.selectionMode = selectionMode;
        invalidate();
    }
    public int getSelectionMode(){
        return selectionMode;
    }

    public void setCACHE_PATH(String path){
        CACHE_PATH = path;
    }
    public void createWaveForm(String source){
        InputStream wavStream = null;
        samplePath = source;
        File sampleFile = new File(samplePath); // File pointer to the current wav sample
        // If the sample file exists, try to generate the waveform
        if (sampleFile.isFile()) {// Trim the sample down and write it to file
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));
                long length;// = sampleFile.length() - 44;

                // Determine length of wav file
                byte[] lenInt = new byte[4];
                wavStream.skip(40);
                wavStream.read(lenInt, 0, 4);
                ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
                length = bb.getInt();

                // Draw the waveform
                byte[] buffer = new byte[1024];
                int i = 0;
                while (i < length){
                    if (length - i >= buffer.length) {
                        wavStream.read(buffer);
                    }
                    else { // Write the remaining number of bytes
                        buffer = new byte[(int)length - i];
                        wavStream.read(buffer);
                    }
                    short[] shorts = new short[buffer.length / 2];
                    ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    float total = 0;
                    for (short s : shorts){
                        total += s;
                    }
                    i += buffer.length;
                    waveFormData.add(new Line((float) i / 44100 / 4, total / shorts.length / Short.MAX_VALUE));
                }
                sampleLength = length / 44100 / 4;
                Log.d(LOG_TAG, "sample length = " + String.valueOf(sampleLength));
                windowStartTime = 0;
                windowEndTime = sampleLength;
                isLoading = false;
                waveFormRender.addAll(waveFormData);
            } catch (IOException e) {e.printStackTrace();}
            finally {
                try {if (wavStream != null) wavStream.close();} catch (IOException e){}
            }

        }
    }

    public void saveAudioSampleData(AudioSampleData data){
        data.setSamplePath(samplePath);
        data.setWaveData(waveFormData, beatsData, waveFormRender, beatsRender);
        data.setTimes(sampleLength, selectionStartTime, selectionEndTime, windowStartTime, windowEndTime);
        data.setColor(color, backgroundColor, foregroundColor);
        data.setShowBeats(showBeats);
    }
    public void loadAudioSampleData(AudioSampleData data){
        samplePath = data.getSamplePath();
        sampleLength = data.getSampleLength();
        selectionStartTime = data.getSelectionStartTime();
        selectionEndTime = data.getSelectionEndTime();
        windowStartTime = data.getWindowStartTime();
        windowEndTime = data.getWindowEndTime();
        waveFormData = data.getWaveFormData();
        waveFormRender = data.getWaveFormRender();
        beatsData = data.getBeatsData();
        beatsRender = data.getBeatsRender();
        color = data.getColor();
        backgroundColor = data.getBackgroundColor();
        foregroundColor = data.getForegroundColor();
        showBeats = data.getShowBeats();
    }

    public void setIsLoading(boolean loading){
        isLoading = loading;
        invalidate();
    }
    public boolean getIsLoading(){
        return isLoading;
    }

    // Beat editing methods
    public void setShowBeats(boolean showBeats){
        this.showBeats = showBeats;
        invalidate();
    }
    public boolean ShowBeats(){
        return showBeats;
    }
    public void identifyBeats(){
        AudioProcessor processor = new AudioProcessor(samplePath);
        beatsData = processor.detectBeats();
        Log.d(LOG_TAG, String.valueOf(beatsData.get(1).getTime()));
        beatsRender = new ArrayList<BeatInfo>(beatsData.size());
        beatsRender.addAll(beatsData);
        selectedBeat = null;
    }
    public boolean hasBeatInfo(){
        return (beatsData != null && beatsData.size() > 0);
    }
    public void removeSelectedBeat(){
        int index = -1;
        for (int i = 0; i < beatsData.size(); i++){
            if (beatsData.get(i).getTime() == selectedBeat.getTime())
                index = i;
        }
        if (index > 0)
            beatsData.remove(index);
        index = -1;
        for (int i = 0; i < beatsRender.size(); i++){
            if (beatsRender.get(i).getTime() == selectedBeat.getTime())
                index = i;
        }
        if (index > 0)
            beatsRender.remove(index);
        selectedBeat = null;
        invalidate();
    }
    public void insertBeat(){

    }

    // Zoom methods
    public void zoomSelection(){
        // Make sure there is a selection
        if (Math.abs(selectEnd - selectStart) > 5) {
            //
            List<Line> tempWav = new ArrayList<Line>();
            tempWav.addAll(waveFormRender);
            waveFormRender.clear();
            for (Line l : tempWav) {
                if (l.getX() >= selectionStartTime && l.getX() <= selectionEndTime) {
                    waveFormRender.add(new Line((l.getX()), l.getY()));
                }
            }
            tempWav.clear();
            List<BeatInfo> tempBeats = new ArrayList<BeatInfo>();
            tempBeats.addAll(beatsRender);
            beatsRender.clear();
            for (BeatInfo b : tempBeats) {
                if (b.getTime() >= selectionStartTime && b.getTime() <= selectionEndTime) {
                    beatsRender.add(new BeatInfo(b.getTime(), b.getSalience()));
                }
            }
            windowStartTime = selectionStartTime;
            windowEndTime = selectionEndTime;
            selectStart = 0;
            selectEnd = getWidth();
            invalidate();
        }
    }
    public void zoomExtents(){
        waveFormRender.clear();
        waveFormRender.addAll(waveFormData);
        beatsRender.clear();
        beatsRender.addAll(beatsData);
        selectStart = (float)selectionStartTime * getWidth() / (float)sampleLength;
        selectEnd = (float)selectionEndTime * getWidth() / (float)sampleLength;
        windowStartTime = 0;
        windowEndTime = sampleLength;
        invalidate();
    }
    public void zoomOut(){
        double oldWindowLength = windowEndTime - windowStartTime;
        windowStartTime = Math.max(windowStartTime - oldWindowLength / 2, 0);
        windowEndTime = Math.min(windowEndTime + oldWindowLength / 2, sampleLength);
        waveFormRender.clear();
        for (Line l : waveFormData) {
            if (l.getX() >= windowStartTime && l.getX() <= windowEndTime) {
                waveFormRender.add(new Line((l.getX()), l.getY()));
            }
        }
        beatsRender.clear();
        for (BeatInfo b : beatsData) {
            if (b.getTime() >= windowStartTime && b.getTime() <= windowEndTime) {
                beatsRender.add(new BeatInfo(b.getTime(), b.getSalience()));
            }
        }
        selectStart = (float)((selectionStartTime - windowStartTime) / (windowEndTime - windowStartTime) * getWidth());
        selectEnd = (float)((selectionEndTime - windowStartTime) / (windowEndTime - windowStartTime) * getWidth());
        invalidate();
    }

    // Trimming methods
    public void TrimToSelection(double startTime, double endTime){
        InputStream wavStream = null; // InputStream to stream the wav to trim
        File trimmedSample = null;  // File to contain the trimmed down sample
        File sampleFile = new File(samplePath); // File pointer to the current wav sample

        // If the sample file exists, try to trim it
        if (sampleFile.isFile()){
            trimmedSample = new File(CACHE_PATH + "trimmed_wav_cache.wav");
            if (trimmedSample.isFile()) trimmedSample.delete();

            // Trim the sample down and write it to file
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));
                // Javazoom WaveFile class is used to write the wav
                WaveFile waveFile = new WaveFile();
                waveFile.OpenForWrite(trimmedSample.getAbsolutePath(), (int)audioFormat.getSampleRate(), (short)audioFormat.getSampleSizeInBits(), (short)audioFormat.getChannels());
                // The number of bytes of wav data to trim off the beginning
                long startOffset = (long)(startTime * audioFormat.getSampleRate()) * audioFormat.getSampleSizeInBits() / 4;
                // The number of bytes to copy
                long length = ((long)(endTime * audioFormat.getSampleRate()) * audioFormat.getSampleSizeInBits() / 4) - startOffset;
                wavStream.skip(44); // Skip the header
                wavStream.skip(startOffset);
                byte[] buffer = new byte[1024];
                int i = 0;
                while (i < length){
                    if (length - i >= buffer.length) {
                        wavStream.read(buffer);
                    }
                    else { // Write the remaining number of bytes
                        buffer = new byte[(int)length - i];
                        wavStream.read(buffer);
                    }
                    short[] shorts = new short[buffer.length / 2];
                    ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    waveFile.WriteData(shorts, shorts.length);
                    i += buffer.length;
                }
                waveFile.Close(); // Complete writing the wave file
                wavStream.close(); // Close the input stream
            } catch (IOException e) {e.printStackTrace();}
            finally {
                try {if (wavStream != null) wavStream.close();} catch (IOException e){}
            }
        }
        // Delete the original wav sample
        sampleFile.delete();
        // Copy the trimmed wav over to replace the sample
        trimmedSample.renameTo(sampleFile);
        // Set the new sample length
        sampleLength = selectionEndTime - selectionStartTime;
        Log.d(LOG_TAG, "trimmed sample length = " + String.valueOf(sampleLength));
        // Copy data over for only the trimmed section
        List<Line> temp = new ArrayList<Line>();
        temp.addAll(waveFormData);
        waveFormData.clear();
        for (Line l : temp) {
            if (l.getX() >= selectionStartTime && l.getX() <= selectionEndTime) {
                waveFormData.add(new Line((l.getX() - (float)selectionStartTime), l.getY()));
            }
        }
        waveFormRender.clear();
        waveFormRender.addAll(waveFormData);
        temp.clear();
        List<BeatInfo> tempBeats = new ArrayList<BeatInfo>();
        tempBeats.addAll(beatsData);
        beatsData.clear();
        for (BeatInfo b : tempBeats) {
            if (b.getTime() >= selectionStartTime && b.getTime() <= selectionEndTime) {
                beatsData.add(new BeatInfo(b.getTime() - (float)selectionStartTime, b.getSalience()));
            }
        }
        beatsRender.clear();
        beatsRender.addAll(beatsData);
        windowStartTime = 0;
        windowEndTime = sampleLength;
        selectionStartTime = 0;
        selectionEndTime = sampleLength;
        selectStart = 0;
        selectEnd = getWidth();
    }
    public String[] Slice(int numSlices){
        String[] paths = new String[numSlices];
        double sliceLength = sampleLength / numSlices;
        double startTime = 0, endTime = sliceLength;
        for (int i = 0; i < numSlices; i++){
            paths[i] = getSlice(i, startTime, endTime);
            startTime += sliceLength;
            endTime += sliceLength;
        }
        File sampleFile = new File(samplePath);
        sampleFile.delete();
        return paths;
    }
    private String getSlice(int sliceIndex, double startTime, double endTime){
        InputStream wavStream = null; // InputStream to stream the wav to trim
        File sampleFile = new File(samplePath); // File pointer to the current wav sample
        File sliceFile = new File(sampleFile.getParent(), String.valueOf(sliceIndex) + ".wav");  // File to contain the trimmed down sample
        // If the sample file exists, try to trim it
        if (sampleFile.isFile()){
            if (sliceFile.isFile()) sliceFile.delete();
            // Trim the sample down and write it to file
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));
                // Javazoom WaveFile class is used to write the wav
                WaveFile waveFile = new WaveFile();
                waveFile.OpenForWrite(sliceFile.getAbsolutePath(), (int)audioFormat.getSampleRate(), (short)audioFormat.getSampleSizeInBits(), (short)audioFormat.getChannels());
                // The number of bytes of wav data to trim off the beginning
                long startOffset = (long)(startTime * audioFormat.getSampleRate()) * audioFormat.getSampleSizeInBits() / 4;
                // The number of bytes to copy
                long length = ((long)(endTime * audioFormat.getSampleRate()) * audioFormat.getSampleSizeInBits() / 4) - startOffset;
                wavStream.skip(44); // Skip the header
                wavStream.skip(startOffset);
                byte[] buffer = new byte[1024];
                int i = 0;
                while (i < length){
                    if (length - i >= buffer.length) {
                        wavStream.read(buffer);
                    }
                    else { // Write the remaining number of bytes
                        buffer = new byte[(int)length - i];
                        wavStream.read(buffer);
                    }
                    short[] shorts = new short[buffer.length / 2];
                    ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    waveFile.WriteData(shorts, shorts.length);
                    i += buffer.length;
                }
                waveFile.Close(); // Complete writing the wave file
                wavStream.close(); // Close the input stream
            } catch (IOException e) {e.printStackTrace();}
            finally {
                try {if (wavStream != null) wavStream.close();} catch (IOException e){}
            }
        }
        return sliceFile.getAbsolutePath();
    }

    public String getSamplePath(){
        return samplePath;
    }

    // Selection methods
    public double getSelectionStartTime(){
        fixSelection();
            return selectionStartTime;
    }
    public double getSelectionEndTime(){
        fixSelection();
            return selectionEndTime;
    }
    public void clearSelection(){
        selectStart = 0;
        selectEnd = 0;
        selectionStartTime = 0;
        selectionEndTime = 0;
        invalidate();
    }
    public boolean isSelection(){
        double selectionLength = getSelectionEndTime() - getSelectionStartTime();
        double windowLength = windowEndTime - windowStartTime;
        return selectionLength / windowLength > 0.01;
    }

    public void updatePlayIndicator(double time){
        playPos.x = (float)(time);
        invalidate();
    }

    // Touch interaction methods
    public boolean onTouch(View view, MotionEvent event) {
        switch (selectionMode) {
            case DEFAULT_SELECTION_MODE:
                defaultSelectionMode(view, event);
                break;
            case BEAT_SELECTION_MODE:
                beatSelectionMode(view, event);
                break;
            case BEAT_MOVE_MODE:
                beatMoveMode(view, event);
                break;
        }
        invalidate();
        return false;
    }
    private void defaultSelectionMode(View view, MotionEvent event){
        // If there is already a selection, move whichever is closer - start or end
        if (Math.abs(selectEnd - selectStart) > 5) {
            double endDist = Math.abs(selectEnd - event.getX());
            double startDist = Math.abs(selectStart - event.getX());
            double min = Math.min(endDist, startDist);
            if (min == endDist) {
                selectEnd = event.getX();
                if (selectEnd > getWidth()) selectEnd = getWidth();
                if (selectEnd < 0) selectEnd = 0;
                selectionEndTime = windowStartTime + (windowEndTime - windowStartTime) * selectEnd / getWidth();
            } else {
                selectStart = event.getX();
                if (selectStart < 0) selectStart = 0;
                if (selectStart > getWidth()) selectStart = getWidth();
                selectionStartTime = windowStartTime + (windowEndTime - windowStartTime) * selectStart / getWidth();
            }
        } else {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                selectStart = event.getX();
                selectEnd = event.getX();
                fixSelection();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                selectEnd = event.getX();
                // Make sure there is a selection
                if (Math.abs(selectEnd - selectStart) > 5) {
                    fixSelection();
                }
            } else {
                selectEnd = event.getX();
                fixSelection();
            }
        }
    }
    private void fixSelection(){
        // Make sure start of selection is before end of selection
        if (selectStart > selectEnd) {
            float temp = selectEnd;
            selectEnd = selectStart;
            selectStart = temp;
        }
        // Make sure start/end of selection are within bounds
        if (selectStart < 0) selectStart = 0;
        if (selectStart > getWidth()) selectStart = getWidth();
        if (selectEnd > getWidth()) selectEnd = getWidth();
        if (selectEnd < 0) selectEnd = 0;

        // Set start time and end time of selection
            /* If this draw is due to a runtime change, this will be true and selectStart and selectEnd
            need to be set in order for the selection to be drawn correctly*/
        if (selectStart == 0 && selectionStartTime > 0 && getWidth() > 0){
            selectStart = (float)(getWidth() * (selectionStartTime - windowStartTime) / (windowEndTime - windowStartTime));
            selectEnd = (float)(getWidth() * (selectionEndTime - windowStartTime) / (windowEndTime - windowStartTime));
        }
        else if (getWidth() > 0) {
            selectionStartTime = windowStartTime + (windowEndTime - windowStartTime) * selectStart / getWidth();
            selectionEndTime = windowStartTime + (windowEndTime - windowStartTime) * selectEnd / getWidth();
        }
        if (Math.abs(selectionEndTime - selectionStartTime) < 0.1)
        {
            selectionStartTime = 0;
            selectionEndTime = 0;//sampleLength;
        }
        Log.d(LOG_TAG, "Selection End Time: " + String.valueOf(selectionEndTime));
    }
    private void beatSelectionMode(View view, MotionEvent event){
        float dpPerSec = getWidth() / (float) (windowEndTime - windowStartTime);
        double min = Math.abs(event.getX() - (beatsRender.get(0).getTime() - windowStartTime) * dpPerSec);
        selectedBeat = beatsRender.get(0);
        for (BeatInfo b : beatsRender){
            double distance = Math.abs(event.getX() - (b.getTime() - windowStartTime) * dpPerSec);
            if (distance < min) {
                min = distance;
                selectedBeat = b;
            }
        }
    }
    private void beatMoveMode(View view, MotionEvent event){
        if (event.getAction() == MotionEvent.ACTION_MOVE){
            BeatInfo temp = selectedBeat;
            removeSelectedBeat();
            selectedBeat = temp;
        }
        float dpPerSec = getWidth() / (float) (windowEndTime - windowStartTime);
        selectedBeat = new BeatInfo(event.getX() / dpPerSec + (float)windowStartTime, selectedBeat.getSalience());
        if (event.getAction() == MotionEvent.ACTION_UP) {
            int index = -1;
            for (int i = 0; i < beatsData.size(); i++){
                if (beatsData.get(i).getTime() == selectedBeat.getTime())
                    index = i;
            }
            if (index == -1)
                beatsData.add(new BeatInfo(selectedBeat.getTime(), selectedBeat.getSalience()));
            index = -1;
            for (int i = 0; i < beatsRender.size(); i++){
                if (beatsRender.get(i).getTime() == selectedBeat.getTime())
                    index = i;
            }
            if (index == -1)
                beatsRender.add(new BeatInfo(selectedBeat.getTime(), selectedBeat.getSalience()));
            setSelectionMode(BEAT_SELECTION_MODE);
        }
    }

    public void redraw(){
        invalidate();
    }

    public void setColor(int colorIndex){
        color = colorIndex;
        String[] backgroundColors = getResources().getStringArray(R.array.background_color_values);
        backgroundColor = backgroundColors[colorIndex];
        String[] foregroundColors = getResources().getStringArray(R.array.foreground_color_values);
        foregroundColor = foregroundColors[colorIndex];
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (waveFormRender.size() > 0 ) {
            // Draw background
            paintBackground.setStyle(Paint.Style.FILL);
            gradient = new LinearGradient(getWidth() / 2, 0, getWidth() / 2, getHeight(), Color.BLACK, Color.parseColor(backgroundColor), Shader.TileMode.MIRROR);
            paintBackground.setShader(gradient);
            canvas.drawPaint(paintBackground);
            // Draw waveform
            float axis = getHeight() / 2;
            float dpPerSec = getWidth() / (float) (windowEndTime - windowStartTime);
            float dpPerSample = getWidth() / waveFormRender.size();
            int increment = 1;
            if (dpPerSample < 0.1){
                increment = Math.max(1, Math.round(waveFormRender.size() / getWidth()) / 5);
            }
            paintBrush.setColor(Color.parseColor(foregroundColor));
            if (dpPerSample <= 1) {
                for (int i = 0; i < waveFormRender.size(); i += increment) {
                    canvas.drawLine((float) (waveFormRender.get(i).x - windowStartTime) * dpPerSec,
                            axis,
                            (float) (waveFormRender.get(i).x - windowStartTime) * dpPerSec,
                            axis - waveFormRender.get(i).y * getHeight(), paintBrush);
                }
            }
            else {
                float width = getWidth() / waveFormRender.size() / increment;
                for (int i = 0; i < waveFormRender.size(); i += increment) {
                    canvas.drawRect((float)(waveFormRender.get(i).x - windowStartTime) * dpPerSec,
                    axis,
                    (float)(waveFormRender.get(i).x - windowStartTime) * dpPerSec + width,
                    axis - waveFormRender.get(i).y * getHeight(),
                    paintBrush);
                }
            }

            switch (selectionMode) {
                case BEAT_MOVE_MODE:
                case BEAT_SELECTION_MODE:
                    // Draw beat marks
                    paintBrush.setColor(Color.DKGRAY);
                    paintSelect.setColor(Color.YELLOW);
                    for (BeatInfo beatInfo : beatsRender) {
                        canvas.drawLine((float) (beatInfo.getTime() - windowStartTime) * dpPerSec, 0, (float) (beatInfo.getTime() - windowStartTime) * dpPerSec, getHeight(), paintBrush);
                        canvas.drawCircle((float) (beatInfo.getTime() - windowStartTime) * dpPerSec, getHeight() / 2, 6, paintSelect);
                    }
                    // Draw selected beat
                    if (selectedBeat != null){
                        paintBrush.setColor(Color.CYAN);
                        paintSelect.setColor(Color.CYAN);
                        canvas.drawLine((float) (selectedBeat.getTime() - windowStartTime) * dpPerSec, 0, (float) (selectedBeat.getTime() - windowStartTime) * dpPerSec, getHeight(), paintBrush);
                        canvas.drawCircle((float) (selectedBeat.getTime() - windowStartTime) * dpPerSec, getHeight() / 2, 8, paintSelect);
                    }
                    break;
                case DEFAULT_SELECTION_MODE:
                /* If this draw is due to a runtime change, this will be true and selectStart and selectEnd
                need to be set in order for the selection to persist*/
                    if (selectStart == 0 && selectionStartTime > 0 && getWidth() > 0) {
                        selectStart = (float) (getWidth() * (selectionStartTime - windowStartTime) / (windowEndTime - windowStartTime));
                        selectEnd = (float) (getWidth() * (selectionEndTime - windowStartTime) / (windowEndTime - windowStartTime));
                    }
                    // Draw selection region
                    if (Math.abs(selectEnd - selectStart) > 5) {
                        paintSelect.setColor(Color.argb(127, 65, 65, 65));
                        canvas.drawRect(selectStart, 0, selectEnd, getHeight(), paintSelect);
                        paintSelect.setColor(Color.LTGRAY);
                        canvas.drawLine(selectStart, 0, selectStart, getHeight(), paintSelect);
                        canvas.drawLine(selectEnd, 0, selectEnd, getHeight(), paintSelect);
                        paintSelect.setColor(Color.YELLOW);
                        canvas.drawCircle(selectStart, 0, 5, paintSelect);
                        canvas.drawCircle(selectStart, getHeight(), 5, paintSelect);
                        canvas.drawCircle(selectEnd, 0, 5, paintSelect);
                        canvas.drawCircle(selectEnd, getHeight(), 5, paintSelect);
                        paintSelect.setColor(Color.LTGRAY);
                        canvas.drawText(String.valueOf((int) Math.floor(selectionStartTime / 60)) + ":" + String.format("%.2f", selectionStartTime % 60), selectStart, getHeight() - 10, paintSelect);
                        canvas.drawText(String.valueOf((int) Math.floor(selectionEndTime / 60)) + ":" + String.format("%.2f", selectionEndTime % 60), selectEnd, getHeight() - 10, paintSelect);
                    }
                    break;
            }

            // Draw play position indicator
            if (isPlaying) {
                paintSelect.setColor(Color.RED);
                canvas.drawLine((float)(playPos.x - windowStartTime) * dpPerSec, 0, (float)(playPos.x - windowStartTime) * dpPerSec, getHeight(), paintSelect);
            }
        }
    }

}
