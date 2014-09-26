package com.nakedape.mixmatictouchpad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.musicg.wave.Wave;
import com.musicg.wave.WaveFileManager;

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
import be.tarsos.dsp.onsets.ComplexOnsetDetector;
import be.tarsos.dsp.onsets.OnsetHandler;
import javazoom.jl.converter.WaveFile;

/**
 * Created by Nathan on 8/31/2014.
 */
public class AudioSample extends View implements View.OnTouchListener, OnsetHandler {
    @Override
    public void handleOnset(double time, double salience){
        beats.add(new BeatInfo(time, salience));
        beatsData.add(new Line((float)time, (float)getHeight()));
    }

    private List<BeatInfo> beats;
    public double sampleLength;
    private double selectionStartTime = -1, selectionEndTime = -1, windowStartTime, windowEndTime;
    private double beatThreshold = 0.3;
    private TarsosDSPAudioFormat audioFormat;
    private int bufferSize = 1024 * 64, overLap = bufferSize / 2, sampleRate = 44100;
    private Paint paintBrush = new Paint(), paintSelect = new Paint();
    private float selectStart = -1, selectEnd = -1;
    private List<Line> waveFormData = new ArrayList<Line>();
    private List<Line> beatsData = new ArrayList<Line>();
    private List<Line> waveFormRender = new ArrayList<Line>();
    private List<Line> beatsRender = new ArrayList<Line>();
    private Line playPos = new Line(0, 0);
    public AudioDispatcher dispatcher;
    public boolean isPlaying = false;
    private boolean showBeats = false;
    public boolean isLoading = false;

    public AudioSample(Context context) {
        super(context);

    }
    public AudioSample(Context context, AttributeSet attrs){
        super(context, attrs);
    }
    public AudioSample(Context context, AttributeSet attrs, int defStyle){
        super (context, attrs, defStyle);
        setFocusable(true);
        setFocusableInTouchMode(true);
        this.setOnTouchListener(this);
        setDrawingCacheEnabled(true);
    }

    public void LoadAudio(String source){
        try {
            InputStream wavStream = new BufferedInputStream(new FileInputStream(source));
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
            ComplexOnsetDetector onsetDetector = new ComplexOnsetDetector(bufferSize, beatThreshold);
            onsetDetector.setHandler(this);
            dispatcher.addAudioProcessor(onsetDetector);
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
        }catch (IOException e){e.printStackTrace();}
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
            float zoomFactor = getWidth() / (selectEnd - selectStart);
            for (Line l : temp) {
                if (l.getX() >= selectionStartTime && l.getX() <= selectionEndTime) {
                    waveFormRender.add(new Line((l.getX()), l.getY()));
                }
            }
            temp.clear();
            temp.addAll(beatsRender);
            beatsRender.clear();
            for (Line l : temp) {
                if (l.getX() >= selectStart && l.getX() <= selectEnd) {
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

    public void Play(String source, double startTime, final double endTime){
        InputStream wavStream;
        try {
            wavStream = new BufferedInputStream(new FileInputStream(source));
            UniversalAudioInputStream audioStream = new UniversalAudioInputStream(wavStream, audioFormat);
            bufferSize = 1024 * 64; //32KB buffer = AudioTrack minimum buffer * 2
            overLap = 0;
            dispatcher = new AudioDispatcher(audioStream, bufferSize, overLap);
            AndroidAudioPlayer player = new AndroidAudioPlayer(audioFormat, bufferSize);
            dispatcher.addAudioProcessor(player);
            dispatcher.skip(startTime);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (dispatcher.secondsProcessed() < endTime) {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    dispatcher.stop();
                }
            }).start();
            dispatcher.run();
        }catch (FileNotFoundException e){e.printStackTrace();}
    }

    public boolean WriteSelectionToFile(InputStream wavStream, String writePath, double startTime, final double endTime) {
        UniversalAudioInputStream audioStream = new UniversalAudioInputStream(wavStream, audioFormat);
        dispatcher = new AudioDispatcher(audioStream, bufferSize, overLap);
        WaveFileWriter writer = new WaveFileWriter(writePath,
                (int) dispatcher.getFormat().getSampleRate(),
                (short)16,
                (short) dispatcher.getFormat().getChannels());
        dispatcher.addAudioProcessor(writer);
        dispatcher.skip(startTime);
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (dispatcher.secondsProcessed() < endTime){
                        try{
                            Thread.sleep(10);
                        } catch (InterruptedException e){e.printStackTrace();}
                    }
                    dispatcher.stop();
                }
            }).start();
        dispatcher.run();
        //writer.processingFinished();
        try {
            audioStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }
    public boolean WriteSelectionToFile(String source, String writePath){
        InputStream wavStream;
        try {
            File f = new File(writePath);
            if (f.isFile())
                f.delete();
            wavStream = new BufferedInputStream(new FileInputStream(source));
            WaveFile waveFile = new WaveFile();
            waveFile.OpenForWrite(writePath, (int)audioFormat.getSampleRate(), (short)audioFormat.getSampleSizeInBits(), (short)audioFormat.getChannels());
            wavStream.skip(44);
            long startOffset = (long)(selectionStartTime * audioFormat.getSampleSizeInBits() * audioFormat.getSampleRate() / 8);
            long length = (long)(selectionEndTime * audioFormat.getSampleSizeInBits() * audioFormat.getSampleRate() / 8) - startOffset;
            wavStream.skip(startOffset);
            byte[] buffer = new byte[4096];
            int bufferLength;
            for (long i = startOffset; i < length + startOffset; i += buffer.length){
                bufferLength = wavStream.read(buffer);
                short[] shorts = new short[buffer.length / 2];
                ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
                waveFile.WriteData(shorts, shorts.length);
            }
            waveFile.Close();


        } catch (IOException e) {e.printStackTrace();}
        return true;
    }

    public double getSelectionStart(){
        return selectionStartTime;
    }
    public double getSelectionEnd(){
        return selectionEndTime;
    }

    public void updatePlayIndicator(double time){
        playPos.x = (float)(time);
        invalidate();
    }

    public boolean onTouch(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            selectStart = event.getX();
            selectionStartTime = windowStartTime + (windowEndTime - windowStartTime) * selectStart / getWidth();
            selectEnd = event.getX();
            selectionEndTime = windowStartTime + (windowEndTime - windowStartTime) * selectEnd / getWidth();
        }
        else if (event.getAction() == MotionEvent.ACTION_UP) {
            selectEnd = event.getX();

            // Make sure there is a selection
            if (Math.abs(selectEnd - selectStart) > 5) {
                // Make sure start of selection is before end of selection
                if (selectStart > selectEnd) {
                    float temp = selectEnd;
                    selectEnd = selectStart;
                    selectStart = temp;
                }
                // Make sure start/end of selection are with bounds
                if (selectStart < 0)
                    selectStart = 0;
                if (selectEnd > getWidth())
                    selectEnd = getWidth();

                // Set start time and end time of selection
                selectionStartTime = windowStartTime + (windowEndTime - windowStartTime) * selectStart / getWidth();
                selectionEndTime = windowStartTime + (windowEndTime - windowStartTime) * selectEnd / getWidth();
            }
        }
        else {
            selectEnd = event.getX();
            selectionEndTime = windowStartTime + (windowEndTime - windowStartTime) * selectEnd / getWidth();
        }
        invalidate();
        return true;
    }

    public void updateView(){
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (waveFormRender.size() > 0 ) {
            // Draw background
            paintBrush.setColor(Color.BLACK);
            paintBrush.setStyle(Paint.Style.FILL);
            canvas.drawPaint(paintBrush);
            // Draw waveform
            float axis = getHeight() / 2;
            float dpPerSec = getWidth() / (float) (windowEndTime - windowStartTime);
            float dpPerSample = getWidth() / waveFormRender.size();
            int increment = 1;
            if (dpPerSample < 0.1){
                increment = Math.max(1, Math.round(waveFormRender.size() / getWidth()) / 5);
            }
            Log.d("Increment: ", String.valueOf(increment));
            Log.d("Window Length = ", String.valueOf(windowEndTime - windowStartTime));
            paintBrush.setColor(Color.BLUE);
            for (int i = 0; i < waveFormRender.size(); i += increment){
                canvas.drawLine((float)(waveFormRender.get(i).x - windowStartTime) * dpPerSec, axis,
                        (float)(waveFormRender.get(i).x - windowStartTime) * dpPerSec, axis - waveFormRender.get(i).y * getHeight(), paintBrush);
            }

            if (showBeats) {
                // Draw beat marks
                paintBrush.setColor(Color.GREEN);
                for (Line line : beatsRender) {
                    canvas.drawLine((float)(line.x - windowStartTime) * dpPerSec, 0, (float)(line.x - windowStartTime) * dpPerSec, line.y, paintBrush);
                }
            }

            // Draw selection region
            if (Math.abs(selectEnd - selectStart) > 5) {
                paintSelect.setColor(Color.argb(127, 65, 65, 65));
                canvas.drawRect(selectStart, 0, selectEnd, getHeight(), paintSelect);
                paintSelect.setColor(Color.LTGRAY);
                canvas.drawLine(selectStart, 0, selectStart, getHeight(), paintSelect);
                canvas.drawLine(selectEnd, 0, selectEnd, getHeight(), paintSelect);
                paintSelect.setColor(Color.YELLOW);
                canvas.drawCircle(selectStart, 0, 2, paintSelect);
                canvas.drawCircle(selectStart, getHeight(), 2, paintSelect);
                canvas.drawCircle(selectEnd, 0, 2, paintSelect);
                canvas.drawCircle(selectEnd, getHeight(), 2, paintSelect);
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

    private class Line{
        public float x, y;
        Line(float x, float y){
            this.x = x;
            this.y = y;
        }
        public float getX(){
            return x;
        }
        public float getY(){
            return y;
        }
    }
}
