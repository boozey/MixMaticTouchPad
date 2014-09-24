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
        bufferSize = Math.max(bufferSize * 2, 32 * 1024);
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                (int)audioFormat.getSampleRate(),
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize * 2,
                AudioTrack.MODE_STREAM);
        audioTrack.play();
    }
    @Override
    public boolean process(AudioEvent audioEvent){
        short[] shorts = new short[audioEvent.getBufferSize() / 2];
        ByteBuffer.wrap(audioEvent.getByteBuffer()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        audioTrack.write(shorts, 0, shorts.length);
        //byte[] bytes = new byte[audioEvent.getBufferSize()];
        //ByteBuffer.wrap(audioEvent.getByteBuffer()).get(bytes);
        //audioTrack.write(bytes, 0, bytes.length);
        return true;
    }

    @Override
    public void processingFinished(){
        audioTrack.stop();
        audioTrack.release();
    }
}
