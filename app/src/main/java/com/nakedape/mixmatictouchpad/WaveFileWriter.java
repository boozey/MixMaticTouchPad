package com.nakedape.mixmatictouchpad;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import javazoom.jl.converter.WaveFile;

/**
 * Created by Nathan on 9/16/2014.
 */
public class WaveFileWriter implements AudioProcessor {
    private WaveFile waveFile;

    public WaveFileWriter(String fileName, int samplingRate, short bitsPerSample, short numChannels){
        waveFile = new WaveFile();
        waveFile.OpenForWrite(fileName, samplingRate, bitsPerSample, numChannels);
    }

    @Override
    public boolean process(AudioEvent audioEvent){
        ByteBuffer bb = ByteBuffer.wrap(audioEvent.getByteBuffer().clone());
        byte[] audioBuffer = new byte[audioEvent.getBufferSize()];//[(audioEvent.getBufferSize() - audioEvent.getOverlap())];
        bb.get(audioBuffer, 0, audioBuffer.length);
        short[] shorts = new short[audioBuffer.length / 2];
        ByteBuffer.wrap(audioBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);

        //ByteBuffer.wrap(audioEvent.getByteBuffer()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        waveFile.WriteData(shorts, shorts.length);
        return true;
    }

    @Override
    public void processingFinished(){
        waveFile.Close();
        Log.d("Processing Finished", "");
    }
}
