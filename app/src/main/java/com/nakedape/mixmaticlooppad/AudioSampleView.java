package com.nakedape.mixmaticlooppad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import javazoom.jl.converter.WaveFile;

/**
 * Created by Nathan on 8/31/2014.
 */
public class AudioSampleView extends View implements View.OnTouchListener {

    public static final int SELECTION_MODE = 4000;
    public static final int BEAT_SELECTION_MODE = 4001;
    public static final int BEAT_MOVE_MODE = 4002;
    public static final int PAN_ZOOM_MODE = 4003;

    private static final String LOG_TAG = "AudioSampleView";

    private Context mContext;
    private String CACHE_PATH;
    private String samplePath;
    private String backupPath;
    private BeatInfo selectedBeat;
    public double sampleLength;
    private double selectionStartTime, selectionEndTime, windowStartTime, windowEndTime;
    private float windowStart, windowEnd;
    private int sampleRate = 44100;
    private short bitsPerSample = 16;
    private short numChannels = 2;
    private int selectionMode = SELECTION_MODE;
    private Paint paintWave, paintSelect, paintBackground;
    private LinearGradient gradient;
    private float selectStart, selectEnd;
    private List<BeatInfo> beatsData = new ArrayList<BeatInfo>();
    private List<BeatInfo> beatsRender = new ArrayList<BeatInfo>();
    private Bitmap wavBitmap;
    private float[] playPos = {0, 0};
    public boolean isPlaying = false;
    private boolean showBeats = false;
    public boolean isLoading = false;
    public int color = 0;
    private String backgroundColor = "#ff000046";
    private String foregroundColor = "#0000FF";
    private Matrix zoomMatrix;
    private float zoomFactor;
    private float zoomMin;
    private float xStart, yStart;
    private ScaleGestureDetector scaleGestureDetector;

    public AudioSampleView(Context context) {
        super(context);
        mContext = context;
        initialize();
    }
    public AudioSampleView(Context context, AttributeSet attrs){
        super(context, attrs);
        mContext = context;
        initialize();
    }
    public AudioSampleView(Context context, AttributeSet attrs, int defStyle){
        super (context, attrs, defStyle);
        mContext = context;
        initialize();
    }
    private void initialize(){
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.setOnTouchListener(this);
        setDrawingCacheEnabled(true);

        // Initialize paint fields
        paintWave = new Paint();
        paintWave.setColor(Color.parseColor(foregroundColor));
        paintSelect = new Paint();
        paintBackground = new Paint();

        // Initialize zoom matrix
        zoomFactor = 1f;
        zoomMatrix = new Matrix();
        zoomMatrix.setScale(zoomFactor, 1f);

        // Initialize scale gesture detector
        scaleGestureDetector = new ScaleGestureDetector(mContext, new ScaleListener());
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
        backupPath = path + "/backup.wav";
    }
    public void loadFile(String source){
        InputStream wavStream = null;
        samplePath = source;
        File sampleFile = new File(samplePath); // File pointer to the current wav sample
        // If the sample file exists, try to generate the waveform
        if (sampleFile.isFile() && getWidth() > 0) {
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));
                long length;
                int sampleSize = 1024;

                // Determine length of wav file
                byte[] lenInt = new byte[4];
                wavStream.skip(40);
                wavStream.read(lenInt, 0, 4);
                ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
                length = bb.getInt();

                // Prepare bitmap
                int numSamples = (int)(length / sampleSize);
                int skipSize = Math.max(1, numSamples / getWidth());
                int bitmapHeight = getHeight();
                int bitmapWidth;
                if (skipSize > 1)
                    bitmapWidth = numSamples / skipSize;
                else
                    bitmapWidth = getWidth();
                wavBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                wavBitmap.setHasAlpha(true);
                Canvas canvas = new Canvas(wavBitmap);

                // Prepare paint
                float strokeWidth = Math.max(1, (float)bitmapWidth / numSamples);
                paintWave.setStrokeWidth(strokeWidth);
                paintWave.setColor(Color.parseColor(foregroundColor));
                paintWave.setStrokeCap(Paint.Cap.ROUND) ;

                // Draw the waveform
                float axis = bitmapHeight / 2;
                byte[] buffer = new byte[sampleSize];
                int i = 0, x = 0;
                while (i < length) {
                    if (length - i >= buffer.length) {
                        wavStream.read(buffer);
                    } else { // Read the remaining number of bytes
                        buffer = new byte[(int) length - i];
                        wavStream.read(buffer);
                    }
                    short[] shorts = new short[buffer.length / 2];
                    ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                    float leftTotal = 0, rightTotal = 0;
                    for (int n = 0; n + 1 < shorts.length; n += 2){
                        leftTotal += shorts[n];
                        rightTotal += shorts[n + 1];
                    }
                    canvas.drawLine(x, axis, x, axis - leftTotal / (shorts.length / 2) / Short.MAX_VALUE * bitmapHeight, paintWave);
                    canvas.drawLine(x, axis, x, axis + rightTotal / (shorts.length / 2) / Short.MAX_VALUE * bitmapHeight, paintWave);
                    x += strokeWidth;
                    i += buffer.length * skipSize;
                    if (skipSize > 1)
                        wavStream.skip(buffer.length * (skipSize - 1));
                }
                // adjust bitmap size to compensate for rounding inaccuracies
                if (x < bitmapWidth){
                    wavBitmap = Bitmap.createBitmap(wavBitmap, 0, 0, x, bitmapHeight);
                }
                sampleLength = length / 44100 / 4;
                Log.i(LOG_TAG, "sample length = " + String.valueOf(sampleLength));
                windowStartTime = 0;
                windowEndTime = sampleLength;
                windowStart = 0;
                windowEnd = getWidth();
                zoomFactor = (float)getWidth() / wavBitmap.getWidth();
                zoomMin = zoomFactor;
                zoomMatrix.setScale(zoomFactor, 1f);
                Log.d(LOG_TAG, "Zoom factor = " + zoomFactor);
                isLoading = false;
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (wavStream != null) wavStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
    public String getSamplePath(){
        return samplePath;
    }
    public void undo(){
        loadFile(backupPath);
    }

    // Methods to save/load data from retained fragment
    public void saveAudioSampleData(AudioSampleData data){
        data.setSamplePath(samplePath);
        data.setWaveData(beatsData, beatsRender);
        data.setTimes(sampleLength, selectionStartTime, selectionEndTime, windowStartTime, windowEndTime);
        data.setColor(color, backgroundColor, foregroundColor);
        data.setShowBeats(showBeats);
        data.setSelectionMode(selectionMode);
        data.setSelectedBeat(selectedBeat);
        data.zoomMatrix = zoomMatrix;
        data.zoomMin = zoomMin;
        data.zoomFactor = zoomFactor;
    }
    public void loadAudioSampleData(AudioSampleData data){
        samplePath = data.getSamplePath();
        sampleLength = data.getSampleLength();
        selectionMode = data.getSelectionMode();
        selectionStartTime = data.getSelectionStartTime();
        selectionEndTime = data.getSelectionEndTime();
        selectedBeat = data.getSelectedBeat();
        windowStartTime = data.getWindowStartTime();
        windowEndTime = data.getWindowEndTime();
        beatsData = data.getBeatsData();
        beatsRender = data.getBeatsRender();
        color = data.getColor();
        backgroundColor = data.getBackgroundColor();
        foregroundColor = data.getForegroundColor();
        showBeats = data.getShowBeats();
        zoomMatrix = data.zoomMatrix;
        zoomMin = data.zoomMin;
        zoomFactor = data.zoomFactor;
    }
    public void setIsLoading(boolean loading){
        isLoading = loading;
        invalidate();
    }
    public boolean getIsLoading(){
        return isLoading;
    }

    public double getSampleLength(){
        return sampleLength;
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
        beatsRender = new ArrayList<BeatInfo>(beatsData.size());
        beatsRender.addAll(beatsData);
        selectedBeat = null;
    }
    public boolean hasBeatInfo(){
        return (beatsData != null && beatsData.size() > 0);
    }
    public int getNumBeats(){
        return beatsData.size();
    }
    public void removeSelectedBeat(){
        int index = -1;
        for (int i = 0; i < beatsData.size(); i++){
            if (beatsData.get(i).getTime() == selectedBeat.getTime())
                index = i;
        }
        if (index >= 0)
            beatsData.remove(index);
        index = -1;
        for (int i = 0; i < beatsRender.size(); i++){
            if (beatsRender.get(i).getTime() == selectedBeat.getTime())
                index = i;
        }
        if (index >= 0)
            beatsRender.remove(index);
        selectedBeat = null;
        invalidate();
    }
    public void insertBeat(){
        if (selectedBeat == null)
            selectedBeat = beatsRender.get(0);
        double newBeatTime = selectedBeat.getTime() - 0.2;
        if (newBeatTime > windowStartTime)
            selectedBeat = new BeatInfo(newBeatTime, 1);
        else
            selectedBeat = new BeatInfo(windowStartTime, 1);
        beatsRender.add(selectedBeat);
        beatsData.add(selectedBeat);
        invalidate();
    }
    public void resample(double factor){
        File backup = new File(backupPath);
        if (backup.isFile())
            backup.delete();
        File currentSamplePath = new File(samplePath);
        try {
            CopyFile(currentSamplePath, backup);
        } catch (IOException e) {e.printStackTrace();}
        currentSamplePath.delete();
        AudioProcessor processor = new AudioProcessor(backupPath);
        processor.resample(100, (int) (factor * 100), samplePath);
        loadFile(samplePath);
    }

    // Trimming methods
    public void TrimToSelection(double startTime, double endTime){
        InputStream wavStream = null; // InputStream to stream the wav to trim
        File trimmedSample = null;  // File to contain the trimmed down sample
        File sampleFile = new File(samplePath); // File pointer to the current wav sample

        // If the sample file exists, try to trim it
        if (sampleFile.isFile() && endTime - startTime > 0){
            trimmedSample = new File(CACHE_PATH + "trimmed_wav_cache.wav");
            if (trimmedSample.isFile()) trimmedSample.delete();

            // Trim the sample down and write it to file
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));
                // Javazoom WaveFile class is used to write the wav
                WaveFile waveFile = new WaveFile();
                waveFile.OpenForWrite(trimmedSample.getAbsolutePath(), 44100, (short)16, (short)2);
                // The number of bytes of wav data to trim off the beginning
                long startOffset = (long)(startTime * 44100) * 16 / 4;
                // The number of bytes to copy
                long length = ((long)(endTime * 44100) * 16 / 4) - startOffset;
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
        List<BeatInfo> tempBeats = new ArrayList<BeatInfo>();
        tempBeats.addAll(beatsData);
        beatsData.clear();
        for (BeatInfo b : tempBeats) {
            if (b.getTime() >= selectionStartTime && b.getTime() <= selectionEndTime) {
                beatsData.add(new BeatInfo(b.getTime() - (float)selectionStartTime, b.getSalience()));
            }
        }
        windowStartTime = 0;
        windowEndTime = sampleLength;
        selectionStartTime = 0;
        selectionEndTime = sampleLength;
        selectStart = 0;
        selectEnd = getWidth();
        loadFile(samplePath);
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
                waveFile.OpenForWrite(sliceFile.getAbsolutePath(), 44100, (short)16, (short)2);
                // The number of bytes of wav data to trim off the beginning
                long startOffset = (long)(startTime * 44100) * 16 / 4;
                // The number of bytes to copy
                long length = ((long)(endTime * 44100) * 16 / 4) - startOffset;
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
        playPos[0] = (float)(time / sampleLength * wavBitmap.getWidth());
        zoomMatrix.mapPoints(playPos);
        invalidate();
    }

    // Touch interaction methods
    public boolean onTouch(View view, MotionEvent event) {
            switch (selectionMode) {
                case SELECTION_MODE:
                    //defaultSelectionMode(view, event);
                    handleSelectionTouch(event);
                    break;
                case BEAT_SELECTION_MODE:
                    beatSelectionMode(view, event);
                    break;
                case BEAT_MOVE_MODE:
                    beatMoveMode(view, event);
                    break;
                case PAN_ZOOM_MODE:
                    panZoomMode(event);
                    break;
            }
        invalidate();
        return false;
    }
    private void handleSelectionTouch(MotionEvent event){
        float[] point = {event.getX(), event.getY()};
        Matrix invert = new Matrix(zoomMatrix);
        invert.invert(invert);
        invert.mapPoints(point);
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                if (selectEnd == 0) {
                    selectStart = point[0];
                    selectEnd = point[0];
                    break;
                }
            case MotionEvent.ACTION_MOVE:
                    double endDist = Math.abs(selectEnd - point[0]);
                    double startDist = Math.abs(selectStart - point[0]);
                    double min = Math.min(endDist, startDist);
                    if (min == endDist) {
                        selectEnd = Math.max(0, Math.min(point[0], wavBitmap.getWidth()));
                        selectionEndTime = selectEnd / wavBitmap.getWidth() * sampleLength;
                    } else {
                        selectStart = Math.max(0, Math.min(point[0], wavBitmap.getWidth()));
                        selectionStartTime = selectStart / wavBitmap.getWidth() * sampleLength;
                    }
                break;
            case MotionEvent.ACTION_UP:
                // Make sure there is a selection
                if (Math.abs(selectEnd - selectStart) > 5) {
                    fixSelection();
                }
                break;
        }
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
                selectionStartTime = windowStartTime + (windowEndTime - windowStartTime) * selectStart / getWidth();
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
        selectStart = Math.max(0, Math.min(selectStart, wavBitmap.getWidth()));
        selectEnd = Math.max(0, Math.min(selectEnd, wavBitmap.getWidth()));

        // Set start time and end time of selection
            /* If this draw is due to a runtime change, this will be true and selectStart and selectEnd
            need to be set in order for the selection to be drawn correctly*/
        if (selectStart == 0 && selectionStartTime > 0 && getWidth() > 0){
            float[] point = {0, 0};
            point[0] = (float)(selectionStartTime / sampleLength * wavBitmap.getWidth());
            zoomMatrix.mapPoints(point);
            selectStart = point[0];
            point[0] = (float)(selectionEndTime / sampleLength * wavBitmap.getWidth());
            zoomMatrix.mapPoints(point);
            selectEnd = point[0];
        }
        else if (getWidth() > 0) {
            selectionStartTime = selectStart / wavBitmap.getWidth() * sampleLength;
            selectionEndTime = selectEnd / wavBitmap.getWidth() * sampleLength;
        }
        // Remove selection if it gets too small
        if (Math.abs(selectionEndTime - selectionStartTime) < 0.1)
        {
            selectionStartTime = 0;
            selectionEndTime = 0;
        }
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
    private void panZoomMode(MotionEvent event){
        scaleGestureDetector.onTouchEvent(event);
        Matrix invert = new Matrix(zoomMatrix);
        invert.invert(invert);
        float[] point = {event.getX(), event.getY()};
        float[] wavStart = {0, 0};
        float[] wavEnd = {wavBitmap.getWidth(), 0};
        invert.mapPoints(point);
        switch (event.getAction()){
            case MotionEvent.ACTION_DOWN:
                xStart = point[0];
                yStart = point[1];
                break;
            case MotionEvent.ACTION_MOVE:
                if (zoomFactor > zoomMin) {
                    float dx = point[0] - xStart;
                    zoomMatrix.mapPoints(wavStart);
                    if (wavStart[0] + dx > 0)
                        dx = 0;
                    zoomMatrix.mapPoints(wavEnd);
                    if (wavEnd[0] + dx < getWidth())
                        dx = 0;
                    zoomMatrix.preTranslate(dx, 0);
                }
                break;
            case MotionEvent.ACTION_UP:
                break;
        }
    }
    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            zoomFactor *= detector.getScaleFactor();
            // Don't let the object get too small or too large.
            zoomFactor = Math.max(zoomMin, Math.min(zoomFactor, 10.0f));
            zoomMatrix.setScale(zoomFactor, 1f);
            Log.d(LOG_TAG, "Zoom factor = " + zoomFactor);
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector){
            // Adjust zoom if it is very close to completely zoomed out
            if (Math.abs(zoomFactor - zoomMin) < 0.1) {
                zoomFactor = zoomMin;
                zoomMatrix.setScale(zoomFactor, 1f);
            }

            // Keep bitmap from going too far left
            float[] wavEnd = {wavBitmap.getWidth(), 0};
            zoomMatrix.mapPoints(wavEnd);
            if (wavEnd[0] < getWidth()){
                float dx = getWidth() - wavEnd[0];
                zoomMatrix.postTranslate(dx, 0);
            }

            // Map window start and end point
            Matrix invert = new Matrix(zoomMatrix);
            invert.invert(invert);
            float[] point = {0, 0};
            invert.mapPoints(point);
            windowStart = Math.max(0, point[0]);
            point[0] = (float)getWidth();
            invert.mapPoints(point);
            windowEnd = Math.min(point[0], getWidth());
            // Set window start and end times
            windowStartTime = windowStart / getWidth() * sampleLength;
            windowEndTime = windowEnd / getWidth() * sampleLength;
            Log.d(LOG_TAG, "window start = " + windowStart);
            Log.d(LOG_TAG, "window end = " + windowEnd);
        }
    }

    public void redraw(){
        requestLayout();
        invalidate();
    }

    public void setColor(int colorIndex){
        color = colorIndex;
        String[] backgroundColors = getResources().getStringArray(R.array.background_color_values);
        backgroundColor = backgroundColors[colorIndex];
        String[] foregroundColors = getResources().getStringArray(R.array.foreground_color_values);
        foregroundColor = foregroundColors[colorIndex];
        paintBackground = new Paint();
        paintBackground.setStyle(Paint.Style.FILL);
        gradient = new LinearGradient(getWidth() / 2, 0, getWidth() / 2, getHeight(), Color.BLACK, Color.parseColor(backgroundColor), Shader.TileMode.MIRROR);
        paintBackground.setShader(gradient);
        if (samplePath != null) loadFile(samplePath);
        invalidate();
    }

    @Override
    protected void onSizeChanged (int w, int h, int oldw, int oldh){
        if (w != oldw || h != oldh) {
            if (samplePath != null) loadFile(samplePath);
            paintBackground.setStyle(Paint.Style.FILL);
            gradient = new LinearGradient(w / 2, 0, w / 2, h, Color.BLACK, Color.parseColor(backgroundColor), Shader.TileMode.MIRROR);
            paintBackground.setShader(gradient);
        }
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
            // Draw background
            canvas.drawPaint(paintBackground);
            // Draw waveform
            if (wavBitmap != null) {
                canvas.drawBitmap(wavBitmap, zoomMatrix, null);
            }
            float dpPerSec = getWidth() / (float) (windowEndTime - windowStartTime);
            switch (selectionMode) {
                case PAN_ZOOM_MODE:
                    break;
                case BEAT_MOVE_MODE:
                case BEAT_SELECTION_MODE:
                    // Draw beat marks
                    paintWave.setColor(Color.DKGRAY);
                    paintSelect.setColor(Color.YELLOW);
                    float[] beatPoint = {0, 0};
                    for (BeatInfo beatInfo : beatsRender) {
                        if (beatInfo.getTime() >= windowStartTime && beatInfo.getTime() <= windowEndTime) {
                            beatPoint[0] = (float)(beatInfo.getTime() / sampleLength * wavBitmap.getWidth());
                            zoomMatrix.mapPoints(beatPoint);
                            canvas.drawLine(beatPoint[0], 0, beatPoint[0], getHeight(), paintWave);
                            canvas.drawCircle(beatPoint[0], getHeight() / 2, 6, paintSelect);
                        }
                    }
                    // Draw selected beat
                    if (selectedBeat != null){
                        paintWave.setColor(Color.CYAN);
                        paintSelect.setColor(Color.CYAN);
                        canvas.drawLine((float) (selectedBeat.getTime() - windowStartTime) * dpPerSec, 0, (float) (selectedBeat.getTime() - windowStartTime) * dpPerSec, getHeight(), paintWave);
                        canvas.drawCircle((float) (selectedBeat.getTime() - windowStartTime) * dpPerSec, getHeight() / 2, 8, paintSelect);
                    }
                    break;
                case SELECTION_MODE:
                /* If this draw is due to a runtime change, this will be true and selectStart and selectEnd
                need to be set in order for the selection to persist*/
                    if (selectStart == 0 && selectionStartTime > 0 && getWidth() > 0 && wavBitmap != null) {
                        float[] point = {0, 0};
                        point[0] = (float)(selectionStartTime / sampleLength * wavBitmap.getWidth());
                        zoomMatrix.mapPoints(point);
                        selectStart = point[0];
                        point[0] = (float)(selectionEndTime / sampleLength * wavBitmap.getWidth());
                        zoomMatrix.mapPoints(point);
                        selectEnd = point[0];
                    }
                    // Draw selection region
                    if (Math.abs(selectEnd - selectStart) > 5) {
                        float[] startPoint = {selectStart, 0};
                        float[] endPoint = {selectEnd, 0};
                        zoomMatrix.mapPoints(startPoint);
                        zoomMatrix.mapPoints(endPoint);
                        paintSelect.setColor(Color.argb(127, 65, 65, 65));
                        canvas.drawRect(startPoint[0], 0, endPoint[0], getHeight(), paintSelect);
                        paintSelect.setColor(Color.LTGRAY);
                        canvas.drawLine(startPoint[0], 0, startPoint[0], getHeight(), paintSelect);
                        canvas.drawLine(endPoint[0], 0, endPoint[0], getHeight(), paintSelect);
                        paintSelect.setColor(Color.YELLOW);
                        canvas.drawCircle(startPoint[0], 0, 5, paintSelect);
                        canvas.drawCircle(startPoint[0], getHeight(), 5, paintSelect);
                        canvas.drawCircle(endPoint[0], 0, 5, paintSelect);
                        canvas.drawCircle(endPoint[0], getHeight(), 5, paintSelect);
                        paintSelect.setColor(Color.LTGRAY);
                        canvas.drawText(String.valueOf((int) Math.floor(selectionStartTime / 60)) + ":" + String.format("%.2f", selectionStartTime % 60), startPoint[0], getHeight() - 10, paintSelect);
                        canvas.drawText(String.valueOf((int) Math.floor(selectionEndTime / 60)) + ":" + String.format("%.2f", selectionEndTime % 60), endPoint[0], getHeight() - 10, paintSelect);
                    }
                    break;
            }

            // Draw play position indicator
            if (isPlaying) {
                paintSelect.setColor(Color.RED);
                canvas.drawLine(playPos[0], 0, playPos[0], getHeight(), paintSelect);
            }
    }

    // Utility methods
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

}
