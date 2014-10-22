package com.nakedape.mixmaticlaunchpad;

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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.Oscilloscope;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.UniversalAudioInputStream;
import be.tarsos.dsp.onsets.BeatRootSpectralFluxOnsetDetector;
import be.tarsos.dsp.onsets.ComplexOnsetDetector;
import be.tarsos.dsp.onsets.OnsetHandler;
import javazoom.jl.converter.WaveFile;

/**
 * Created by Nathan on 8/31/2014.
 */
public class AudioSampleView extends View implements View.OnTouchListener, OnsetHandler {
    @Override
    public void handleOnset(double time, double salience){
        if (salience > 0.5) {
            beats.add(new BeatInfo(time, salience));
            beatsData.add(new Line((float) time, (float) getHeight()));
        }
    }

    private static final String LOG_TAG = "MixMatic AudioSampleView";

    private String CACHE_PATH;
    private String samplePath;
    private List<BeatInfo> beats;
    public double sampleLength;
    private double selectionStartTime, selectionEndTime, windowStartTime, windowEndTime;
    private int bufferSize = 1024 * 64, overLap = bufferSize / 2, sampleRate = 44100;
    private TarsosDSPAudioFormat audioFormat = new TarsosDSPAudioFormat(sampleRate, 16, 2, false, false);
    private Paint paintBrush = new Paint(), paintSelect = new Paint(), paintBackground = new Paint();
    private LinearGradient gradient;
    private float selectStart, selectEnd;
    private List<Line> waveFormData = new ArrayList<Line>();
    private List<Line> beatsData = new ArrayList<Line>();
    private List<Line> waveFormRender = new ArrayList<Line>();
    private List<Line> beatsRender = new ArrayList<Line>();
    private Line playPos = new Line(0, 0);
    public AudioDispatcher dispatcher;
    public boolean isPlaying = false;
    public boolean continutePlaying = false;
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

    public void setCACHE_PATH(String path){
        CACHE_PATH = path;
    }
    public void LoadAudio(String source){
        InputStream wavStream = null;
        try {
            samplePath = source;
            wavStream = new BufferedInputStream(new FileInputStream(source));
            //Read the sample rate
            byte[] rateInt = new byte[4];
            wavStream.skip(24);
            wavStream.read(rateInt, 0, 4);
            ByteBuffer bb = ByteBuffer.wrap(rateInt).order(ByteOrder.LITTLE_ENDIAN);
            sampleRate = bb.getInt();
            Log.d("Sample Rate", String.valueOf(sampleRate));

            audioFormat = new TarsosDSPAudioFormat(sampleRate, 16, 2, false, false);
            // Small buffer size to allow more accurate rendering of waveform
            bufferSize = 1024;
            overLap = bufferSize / 2;
            //Set up and run Tarsos
            UniversalAudioInputStream audioStream = new UniversalAudioInputStream(wavStream, audioFormat);
            dispatcher = new AudioDispatcher(audioStream, bufferSize, overLap);
            beats = new ArrayList<BeatInfo>(500);
            beatsData = new ArrayList<Line>(500);
            waveFormData = new ArrayList<Line>(1000);
            Oscilloscope.OscilloscopeEventHandler handler = new Oscilloscope.OscilloscopeEventHandler() {
                @Override
                public void handleEvent(float[] floats, AudioEvent audioEvent) {
                    float total = 0;
                    for (int i = 0; i < floats.length; i += 2) {
                        total += floats[i + 1];
                    }
                    waveFormData.add(new Line(dispatcher.secondsProcessed(), total / floats.length));
                }
            };
            Oscilloscope oscilloscope = new Oscilloscope(handler);
            dispatcher.addAudioProcessor(oscilloscope);
            dispatcher.run();
            sampleLength = dispatcher.secondsProcessed();
            Log.d("Dispatcher", String.valueOf(sampleLength) + " seconds processed");
            windowStartTime = 0;
            windowEndTime = sampleLength;
            isLoading = false;
            waveFormRender.addAll(waveFormData);
            beatsRender.addAll(beatsData);
            audioStream.close();
            wavStream.close();
        }catch (IOException e){e.printStackTrace();}
        finally {
            try {
                if (wavStream != null) wavStream.close();
            }catch (IOException e){}
        }
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
        data.setWaveData(waveFormData, beatsData, waveFormRender, beatsRender, beats);
        data.setTimes(sampleLength, selectionStartTime, selectionEndTime, windowStartTime, windowEndTime);
        data.setColor(color, backgroundColor, foregroundColor);
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
        beats = data.getBeats();
        color = data.getColor();
        backgroundColor = data.getBackgroundColor();
        foregroundColor = data.getForegroundColor();
    }

    public void setIsLoading(boolean loading){
        isLoading = loading;
        invalidate();
    }
    public boolean getIsLoading(){
        return isLoading;
    }

    public void setShowBeats(boolean showBeats){
        this.showBeats = showBeats;
        invalidate();
    }
    public boolean ShowBeats(){
        return showBeats;
    }

    public void zoomSelection(){
        // Make sure there is a selection
        if (Math.abs(selectEnd - selectStart) > 5) {
            //
            List<Line> temp = new ArrayList<Line>();
            temp.addAll(waveFormRender);
            waveFormRender.clear();
            for (Line l : temp) {
                if (l.getX() >= selectionStartTime && l.getX() <= selectionEndTime) {
                    waveFormRender.add(new Line((l.getX()), l.getY()));
                }
            }
            temp.clear();
            temp.addAll(beatsRender);
            beatsRender.clear();
            for (Line l : temp) {
                if (l.getX() >= selectionStartTime && l.getX() <= selectionEndTime) {
                    beatsRender.add(new Line(l.getX(), l.getY()));
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
        for (Line l : beatsData) {
            if (l.getX() >= windowStartTime && l.getX() <= windowEndTime) {
                beatsRender.add(new Line(l.getX(), l.getY()));
            }
        }
        selectStart = (float)(selectionStartTime * getWidth() / (windowEndTime - windowStartTime));
        selectEnd = (float)(selectionEndTime * getWidth() / (windowEndTime - windowStartTime));
        invalidate();
    }

    public void Play(double startTime, final double endTime){
        InputStream wavStream;
        try {
            wavStream = new BufferedInputStream(new FileInputStream(samplePath));
            UniversalAudioInputStream audioStream = new UniversalAudioInputStream(wavStream, audioFormat);
            bufferSize = 1024 * 4; //32KB buffer = AudioTrack minimum buffer * 2
            overLap = 0;
            dispatcher = new AudioDispatcher(audioStream, bufferSize, overLap);
            AndroidAudioPlayer player = new AndroidAudioPlayer(audioFormat, bufferSize);
            dispatcher.addAudioProcessor(player);
            dispatcher.skip(startTime);
            continutePlaying = true;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    isPlaying = true;
                    while (dispatcher.secondsProcessed() < endTime && continutePlaying) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    dispatcher.stop();
                    isPlaying = false;
                }
            }).start();
            dispatcher.run();
        }catch (FileNotFoundException e){e.printStackTrace();}
    }
    public void Stop(){
        continutePlaying = false;
    }

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
        temp.addAll(beatsData);
        beatsData.clear();
        for (Line l : temp) {
            if (l.getX() >= selectionStartTime && l.getX() <= selectionEndTime) {
                beatsData.add(new Line(l.getX() - (float)selectionStartTime, l.getY()));
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
    public boolean TarsosTrim(double startTime, final double endTime) {
        InputStream wavStream = null; // InputStream to stream the wav to trim
        File trimmedSample = null;  // File to contain the trimmed down sample
        File sampleFile = new File(samplePath); // File pointer to the current wav sample

        // If the sample file exists, try to trim it
        if (sampleFile.isFile()) {
            trimmedSample = new File(CACHE_PATH + "trimmed_wav_cache.wav");
            if (trimmedSample.isFile()) trimmedSample.delete();

            // Trim the sample down and write it to file
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));
                UniversalAudioInputStream audioStream = new UniversalAudioInputStream(wavStream, audioFormat);
                dispatcher = new AudioDispatcher(audioStream, bufferSize, overLap);
                WaveFileWriter writer = new WaveFileWriter(trimmedSample.getAbsolutePath(),
                        44100,
                        (short) 16,
                        (short) 1);
                dispatcher.addAudioProcessor(writer);
                dispatcher.skip(startTime);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (dispatcher.secondsProcessed() < endTime) {
                            try {
                                Thread.sleep(1);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        dispatcher.stop();
                    }
                }).start();
                dispatcher.run();
                //writer.processingFinished();
            } catch (IOException e) {
                e.printStackTrace();
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
            temp.addAll(beatsData);
            beatsData.clear();
            for (Line l : temp) {
                if (l.getX() >= selectionStartTime && l.getX() <= selectionEndTime) {
                    beatsData.add(new Line(l.getX() - (float)selectionStartTime, l.getY()));
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
        return true;
    }
    public void identifyBeats(double beatThreshold){
        dispatcher = getDispatcher(1024);
        beats = new ArrayList<BeatInfo>(500);
        beatsData = new ArrayList<Line>(500);
        ComplexOnsetDetector onsetDetector = new ComplexOnsetDetector(1024, beatThreshold);
        //BeatRootSpectralFluxOnsetDetector onsetDetector = new BeatRootSpectralFluxOnsetDetector(dispatcher, 256, 8);
        onsetDetector.setHandler(this);
        dispatcher.addAudioProcessor(onsetDetector);
        dispatcher.run();
        beatsRender.addAll(beatsData);
    }
    private AudioDispatcher getDispatcher(int bufferSize) {
        InputStream wavStream;
        UniversalAudioInputStream audioStream = null;
        try {
            wavStream = new BufferedInputStream(new FileInputStream(samplePath));
            //Read the sample rate
            byte[] rateInt = new byte[4];
            wavStream.skip(24);
            wavStream.read(rateInt, 0, 4);
            ByteBuffer bb = ByteBuffer.wrap(rateInt).order(ByteOrder.LITTLE_ENDIAN);
            sampleRate = bb.getInt();
            Log.d("Sample Rate", String.valueOf(sampleRate));

            audioFormat = new TarsosDSPAudioFormat(sampleRate, 16, 2, false, false);
            // Small buffer size to allow more accurate rendering of waveform
            overLap = bufferSize / 2;
            //Set up and run Tarsos
            audioStream = new UniversalAudioInputStream(wavStream, audioFormat);
        } catch (IOException e) {e.printStackTrace();}
        if (audioStream != null)
            return new AudioDispatcher(audioStream, bufferSize, overLap);
        else
            return null;
    }

    public String getSamplePath(){
        return samplePath;
    }

    public double getSelectionStartTime(){
        fixSelection();
        if (Math.abs(selectEnd - selectStart) < 5)
        {
            return 0;
        }
        else
            return selectionStartTime;
    }
    public double getSelectionEndTime(){
        fixSelection();
        if (Math.abs(selectEnd - selectStart) < 5)
        {
            return sampleLength;
        }
        else
            return selectionEndTime;
    }
    public void clearSelection(){
        selectStart = 0;
        selectEnd = 0;
        selectionStartTime = 0;
        selectionEndTime = 0;
        invalidate();
    }

    public void updatePlayIndicator(double time){
        playPos.x = (float)(time);
        invalidate();
    }

    public boolean onTouch(View view, MotionEvent event) {
        // If there is already a selection, move whichever is closer - start or end
        if (Math.abs(selectEnd - selectStart) > 5){
            double endDist = Math.abs(selectEnd - event.getX());
            double startDist = Math.abs(selectStart - event.getX());
            double min = Math.min(endDist, startDist);
            if (min == endDist){
                selectEnd = event.getX();
                if (selectEnd > getWidth()) selectEnd = getWidth();
                if (selectEnd < 0) selectEnd = 0;
                selectionEndTime = windowStartTime + (windowEndTime - windowStartTime) * selectEnd / getWidth();
            }
            else {
                selectStart = event.getX();
                if (selectStart < 0) selectStart = 0;
                if (selectStart > getWidth()) selectStart = getWidth();
                selectionStartTime = windowStartTime + (windowEndTime - windowStartTime) * selectStart / getWidth();
            }
        }
        else {
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
        invalidate();
        return false;
    }
    private void fixSelection(){
        // Make sure start of selection is before end of selection
        if (selectStart > selectEnd) {
            float temp = selectEnd;
            selectEnd = selectStart;
            selectStart = temp;
        }
        // Make sure start/end of selection are with bounds
        if (selectStart < 0) selectStart = 0;
        if (selectStart > getWidth()) selectStart = getWidth();
        if (selectEnd > getWidth()) selectEnd = getWidth();
        if (selectEnd < 0) selectEnd = 0;

        // Set start time and end time of selection
        selectionStartTime = windowStartTime + (windowEndTime - windowStartTime) * selectStart / getWidth();
        selectionEndTime = windowStartTime + (windowEndTime - windowStartTime) * selectEnd / getWidth();
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

            if (showBeats) {
                // Draw beat marks
                paintBrush.setColor(Color.DKGRAY);
                for (Line line : beatsRender) {
                    canvas.drawLine((float)(line.x - windowStartTime) * dpPerSec, 0, (float)(line.x - windowStartTime) * dpPerSec, line.y, paintBrush);
                }
            }
            /* If this draw is due to a runtime change, this will be true and selectStart and selectEnd
            need to be set in order for the selection to persist*/
            if (selectStart == 0 && selectionStartTime > 0){
                selectStart = (float)(getWidth() * (selectionStartTime - windowStartTime) / (windowEndTime - windowStartTime));
                selectEnd = (float)(getWidth() * (selectionEndTime - windowStartTime) / (windowEndTime - windowStartTime));
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
                canvas.drawText(String.valueOf((int)Math.floor(selectionStartTime/60)) + ":" + String.format("%.2f", selectionStartTime % 60), selectStart, getHeight() - 10, paintSelect);
                canvas.drawText(String.valueOf((int)Math.floor(selectionEndTime/60)) + ":" + String.format("%.2f", selectionEndTime % 60), selectEnd, getHeight() - 10, paintSelect);
            }
            // Draw play position indicator
            if (isPlaying) {
                paintSelect.setColor(Color.RED);
                canvas.drawLine((float)(playPos.x - windowStartTime) * dpPerSec, 0, (float)(playPos.x - windowStartTime) * dpPerSec, getHeight(), paintSelect);
            }
        }
    }

}
