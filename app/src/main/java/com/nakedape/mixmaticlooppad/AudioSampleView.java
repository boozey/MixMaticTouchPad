package com.nakedape.mixmaticlooppad;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
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
    public static final int SLICE_MODE = 4004;

    public interface OnAudioProcessingFinishedListener {
        void OnProcessingFinish();
    }

    private static final String LOG_TAG = "AudioSampleView";

    private Context mContext;
    private OnAudioProcessingFinishedListener processingFinishedListener;
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
    private int selectionMode = PAN_ZOOM_MODE;
    private Paint paintWave, paintSelect, paintBackground;
    private LinearGradient gradient;
    private float selectStart, selectEnd;
    private List<BeatInfo> beatsData = new ArrayList<BeatInfo>();
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
    private boolean needsSaving = false;
    private boolean isMicRecording;

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
    public void setOnAudioProcessingFinishedListener(OnAudioProcessingFinishedListener listener){
        processingFinishedListener = listener;
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
                wavStream.skip(24);
                wavStream.read(lenInt, 0, 4);
                ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
                sampleRate = bb.getInt();
                wavStream.skip(12);
                wavStream.read(lenInt, 0, 4);
                bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
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
                sampleLength = length / sampleRate / 4;
                windowStartTime = 0;
                windowEndTime = sampleLength;
                windowStart = 0;
                windowEnd = getWidth();
                zoomFactor = (float)getWidth() / wavBitmap.getWidth();
                zoomMin = zoomFactor;
                zoomMatrix.setScale(zoomFactor, 1f);
                isLoading = false;
                needsSaving = true;
                beatsData = null;
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
    public boolean needsSaving(){
        return needsSaving;
    }
    public void undo(){
        loadFile(backupPath);
    }
    public Bitmap getWaveFormThumbnail(){
        return Utils.getScaledBitmap(wavBitmap, 80, 80);
    }
    public void startRecording(){
        if (!isMicRecording()) {
            // Prepare new recording file
            samplePath = CACHE_PATH + "/recording.wav";
            File sampleFile = new File(samplePath);
            if (sampleFile.exists()) sampleFile.delete();
            // Prepare bitmap
            int bitmapHeight = getHeight();
            int bitmapWidth = getWidth();
            wavBitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            wavBitmap.setHasAlpha(true);
            zoomFactor = 1f;
            zoomMin = 1f;
            zoomMatrix.setScale(zoomFactor, 1f);

            // Prepare paint
            paintWave.setStrokeWidth(1);
            paintWave.setColor(Color.parseColor(foregroundColor));
            paintWave.setStrokeCap(Paint.Cap.ROUND);

            // Start recording thread
            new Thread(new RecordAudio()).start();
        }
    }
    public boolean isMicRecording(){
        return isMicRecording;
    }
    private class RecordAudio implements Runnable {
        @Override
        public void run(){
            // Prepare to draw
            Canvas canvas = new Canvas(wavBitmap);
            float axis = wavBitmap.getHeight() / 2;
            int x = 0;

            // Prepare to record
            int bufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            isMicRecording = true;
            short[] audioShorts = new short[bufferSize / 2];
            int readLength;
            float total = 0;
            int sampleCount;
            int sampleSize = 1024;
            int maxValue = Short.MAX_VALUE / 2;
            WaveFile waveFile = new WaveFile();
            waveFile.OpenForWrite(samplePath, 44100, (short) 16, (short) 2);

            // Record
            recorder.startRecording();
            Log.i(LOG_TAG, "Recording started");
            while (isMicRecording){
                readLength = recorder.read(audioShorts, 0, audioShorts.length);
                waveFile.WriteData(audioShorts, audioShorts.length);
                // Draw waveform
                if (x >= wavBitmap.getWidth()){
                    Bitmap temp = wavBitmap;
                    wavBitmap = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
                    wavBitmap.setHasAlpha(true);
                    canvas = new Canvas(wavBitmap);
                    canvas.drawBitmap(temp, -getWidth() * 0.25f, 0, null);
                    temp.recycle();
                    x = (int)(0.75 * getWidth());
                }
                sampleCount = 1;
                for (int i = 0; i < readLength; i+= sampleSize) {
                    total = 0;
                    for (int n = i; n < i + sampleSize && n < readLength; n += 1) {
                        if (audioShorts[n] != 0) {
                            total += audioShorts[n];
                            maxValue = Math.max(Math.abs(audioShorts[n]), maxValue);
                            sampleCount++;
                        }
                    }
                    canvas.drawLine(x, axis, x, axis - total / (sampleCount) / maxValue * wavBitmap.getHeight(), paintWave);
                    canvas.drawLine(x, axis, x, axis + total / (sampleCount) / maxValue * wavBitmap.getHeight(), paintWave);
                    x += 1;
                }
                getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        invalidate();
                    }
                });
            }
            waveFile.Close();
            Log.i(LOG_TAG, "Recording stopped");
            getHandler().post(new Runnable() {
                @Override
                public void run() {
                    loadFile(samplePath);
                    invalidate();
                }
            });
        }
    }
    public void stopRecording(){
        isMicRecording = false;
    }

    // Methods to save/load data from retained fragment
    public void saveAudioSampleData(AudioSampleData data){
        data.setSamplePath(samplePath);
        data.beatsData = beatsData;
        data.setTimes(sampleLength, selectionStartTime, selectionEndTime, windowStartTime, windowEndTime);
        data.setColor(color, backgroundColor, foregroundColor);
        data.setShowBeats(showBeats);
        data.setSelectionMode(selectionMode);
        data.setSelectedBeat(selectedBeat);
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
        selectedBeat = null;
    }
    public void identifyBeatsAsync(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                identifyBeats();
                if (processingFinishedListener != null)
                    getHandler().post(new Runnable() {
                        @Override
                        public void run() {
                            processingFinishedListener.OnProcessingFinish();
                        }
                    });
            }
        }).start();
    }
    public boolean hasBeatInfo(){
        return (beatsData != null && beatsData.size() > 0);
    }
    public int getNumBeats(){
        return beatsData.size();
    }
    public void removeSelectedBeat(){
        beatsData.remove(selectedBeat);
        selectedBeat = null;
        invalidate();
    }
    public void insertBeat(){
        if (selectedBeat == null) {
            if (beatsData.size() > 0)
            selectedBeat = beatsData.get(0);
        }
        double newBeatTime = selectedBeat.getTime() - 0.2;
        if (newBeatTime > windowStartTime)
            selectedBeat = new BeatInfo(newBeatTime, 1);
        else
            selectedBeat = new BeatInfo(windowStartTime, 1);
        beatsData.add(selectedBeat);
        invalidate();
    }
    public void resample(float factor){
        File backup = new File(backupPath);
        if (backup.isFile())
            backup.delete();
        File currentSampleFile = new File(samplePath);
        try {
            Utils.CopyFile(currentSampleFile, backup);
        } catch (IOException e) {e.printStackTrace();}
        currentSampleFile.delete();
        AudioProcessor processor = new AudioProcessor(backupPath);
        processor.resample(100, (int) (factor * 100), samplePath);
        loadFile(samplePath);
        getHandler().post(new Runnable() {
            @Override
            public void run() {
                invalidate();
                if (processingFinishedListener != null)
                    processingFinishedListener.OnProcessingFinish();
            }
        });
    }
    public void resampleAsync(final float factor){
        new Thread(new Runnable() {
            @Override
            public void run() {
                resample(factor);
            }
        }).start();
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
                long startOffset = (long)(startTime * sampleRate) * 16 / 4;
                // The number of bytes to copy
                long length = ((long)(endTime * sampleRate) * 16 / 4) - startOffset;
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
    public boolean getSlice(File sliceFile, double startTime, double endTime){
        // Make sure start and end times are within range
        if (startTime == endTime) return false;
        if (startTime > endTime){
            double temp = startTime;
            startTime = endTime;
            endTime = startTime;
        }
        startTime = Math.max(0, startTime);
        endTime = Math.min(endTime, sampleLength);

        InputStream wavStream = null; // InputStream to stream the wav to trim
        File sampleFile = new File(samplePath); // File pointer to the current wav sample
        // If the sample file exists, try to trim it
        if (sampleFile.exists()){
            if (sliceFile.exists()) sliceFile.delete();
            // Trim the sample down and write it to file
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));
                // Javazoom WaveFile class is used to write the wav
                WaveFile waveFile = new WaveFile();
                waveFile.OpenForWrite(sliceFile.getAbsolutePath(), sampleRate, (short)16, (short)2);
                // The number of bytes of wav data to trim off the beginning
                long startOffset = (long)(startTime * sampleRate) * 16 / 4;
                // The number of bytes to copy
                long length = ((long)(endTime * sampleRate) * 16 / 4) - startOffset;
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
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            finally {
                try {if (wavStream != null) wavStream.close();} catch (IOException e){}
            }
            return true;
        } else {
            return false;
        }
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
        if (wavBitmap != null)
            return getSelectionEndTime() - getSelectionStartTime() > 0;
        else
            return false;
    }

    public void updatePlayIndicator(double time){
        playPos[0] = (float)(time / sampleLength * wavBitmap.getWidth());
        zoomMatrix.mapPoints(playPos);
        invalidate();
    }

    // Touch interaction methods
    public boolean onTouch(View view, MotionEvent event) {
        if (wavBitmap != null) {
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
        }
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
        float[] touchPoint = {event.getX(), event.getY()};
        Matrix unZoom = new Matrix(zoomMatrix);
        unZoom.invert(unZoom);
        unZoom.mapPoints(touchPoint);
        float touchTime = touchPoint[0] / wavBitmap.getWidth() * (float)sampleLength;
        if (beatsData.size() > 0) {
            selectedBeat = beatsData.get(0);
            double min = Math.abs(touchTime - selectedBeat.getTime());
            for (BeatInfo b : beatsData) {
                double distance = Math.abs(touchTime - b.getTime());
                if (distance < min) {
                    min = distance;
                    selectedBeat = b;
                }
            }
        }
    }
    private void beatMoveMode(View view, MotionEvent event){
        float[] touchPoint = {event.getX(), event.getY()};
        Matrix unZoom = new Matrix(zoomMatrix);
        unZoom.invert(unZoom);
        unZoom.mapPoints(touchPoint);
        float touchTime = touchPoint[0] / wavBitmap.getWidth() * (float)sampleLength;

        switch (event.getAction()){
            case MotionEvent.ACTION_MOVE:
                BeatInfo temp = selectedBeat;
                removeSelectedBeat();
                selectedBeat = temp;
                selectedBeat = new BeatInfo(touchTime, selectedBeat.getSalience());
                break;
            case MotionEvent.ACTION_UP:
                int index = 0;
                for (index = 0; index < beatsData.size() && beatsData.get(index).getTime() < selectedBeat.getTime(); index++);
                beatsData.add(index, selectedBeat);
                setSelectionMode(BEAT_SELECTION_MODE);
                break;
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
            if (wavBitmap != null) {
                // Draw background
                canvas.drawPaint(paintBackground);
                // Draw waveform
                canvas.drawBitmap(wavBitmap, zoomMatrix, null);
            }
            switch (selectionMode) {
                case PAN_ZOOM_MODE:
                    break;
                case BEAT_MOVE_MODE:
                case BEAT_SELECTION_MODE:
                    // Draw beat marks
                    paintWave.setColor(Color.DKGRAY);
                    paintSelect.setColor(Color.YELLOW);
                    float[] beatPoint = {0, 0};
                    for (BeatInfo beatInfo : beatsData) {
                        if (beatInfo.getTime() >= windowStartTime && beatInfo.getTime() <= windowEndTime) {
                            beatPoint[0] = (float)(beatInfo.getTime() / sampleLength * wavBitmap.getWidth());
                            zoomMatrix.mapPoints(beatPoint);
                            canvas.drawLine(beatPoint[0], 0, beatPoint[0], getHeight(), paintWave);
                            canvas.drawCircle(beatPoint[0], getHeight() / 2, 6, paintSelect);
                        }
                    }
                    // Draw selected beat
                    if (selectedBeat != null){
                        beatPoint[0] = (float)(selectedBeat.getTime() / sampleLength * wavBitmap.getWidth());
                        zoomMatrix.mapPoints(beatPoint);
                        paintWave.setColor(Color.CYAN);
                        paintSelect.setColor(Color.CYAN);
                        canvas.drawLine(beatPoint[0], 0, beatPoint[0], getHeight(), paintWave);
                        canvas.drawCircle(beatPoint[0], getHeight() / 2, 6, paintSelect);
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

            if (isPlaying) {
                // Draw play position indicator
                paintSelect.setColor(Color.RED);
                canvas.drawLine(playPos[0], 0, playPos[0], getHeight(), paintSelect);
            }
    }
}
