package com.nakedape.mixmatictouchpad;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;

/**
 * Created by Nathan on 9/19/2014.
 */
public class AndroidAudioPlayer implements AudioProcessor {
    private AudioTrack audioTrack;
    AndroidAudioPlayer(TarsosDSPAudioFormat audioFormat){
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                (int)audioFormat.getSampleRate(),
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                2048,
                AudioTrack.MODE_STREAM);
    }
    @Override
    public boolean process(AudioEvent audioEvent){
        short[] shorts = new short[audioEvent.getBufferSize() / 2];
        ByteBuffer.wrap(audioEvent.getByteBuffer()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        audioTrack.write(shorts, 0, shorts.length);
        audioTrack.play();
        return true;
    }

    @Override
    public void processingFinished(){}
}
