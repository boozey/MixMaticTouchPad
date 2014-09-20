package com.nakedape.mixmatictouchpad;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.nio.ByteBuffer;

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
                44100,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                2048,
                AudioTrack.MODE_STREAM);
    }
    @Override
    public boolean process(AudioEvent audioEvent){
        ByteBuffer bb = ByteBuffer.wrap(audioEvent.getByteBuffer().clone());
        byte[] audioBuffer = new byte[(audioEvent.getBufferSize() - audioEvent.getOverlap())];
        bb.get(audioBuffer, 0, audioBuffer.length);
        bb.get(audioBuffer, 0, audioBuffer.length);
        audioTrack.write(audioBuffer, 0, audioBuffer.length);
        audioTrack.play();
        return true;
    }

    @Override
    public void processingFinished(){}
}
