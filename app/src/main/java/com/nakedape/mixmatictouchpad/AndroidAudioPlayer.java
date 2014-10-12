package com.nakedape.mixmatictouchpad;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

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
    AndroidAudioPlayer(TarsosDSPAudioFormat audioFormat, int bufferSize){
        bufferSize = Math.max(bufferSize, AudioTrack.getMinBufferSize((int)audioFormat.getSampleRate(), AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_8BIT));
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                (int)audioFormat.getSampleRate(),
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_8BIT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM);
    }
    @Override
    public boolean process(AudioEvent audioEvent){
        if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
            audioTrack.play();
        short[] shorts = new short[audioEvent.getBufferSize() / 2];
        ByteBuffer.wrap(audioEvent.getByteBuffer()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        audioTrack.write(shorts, 0, shorts.length);
        return true;
    }

    @Override
    public void processingFinished(){
        audioTrack.stop();
        audioTrack.release();
    }
}
