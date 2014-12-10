package com.nakedape.mixmaticlooppad;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

/**
 * Created by Nathan on 12/7/2014.
 */
public class AudioProcessor {

    String LOG_TAG = "AudioProcessor";
    String wavPath;

    //Constructors
    public AudioProcessor(){

    }
    public AudioProcessor(String wavPath){
        this.wavPath = wavPath;
    }

    //Beat detection methods
    public ArrayList<BeatInfo> detectBeats() {
        //Arrays to hold detected beats
        ArrayList<BeatInfo> beats = new ArrayList<BeatInfo>();
        ArrayList<BeatInfo> finalBeatList = new ArrayList<BeatInfo>();
        InputStream wavStream = null;
        File sampleFile = new File(wavPath); // File pointer to the current wav sample
        // If the sample file exists, try to generate the waveform
        if (sampleFile.isFile()) {// Trim the sample down and write it to file
            try {
                wavStream = new BufferedInputStream(new FileInputStream(sampleFile));

                // Determine length of wav file
                long length;
                byte[] lenInt = new byte[4];
                wavStream.skip(40);
                wavStream.read(lenInt, 0, 4);
                ByteBuffer bb = ByteBuffer.wrap(lenInt).order(ByteOrder.LITTLE_ENDIAN);
                length = bb.getInt();
                //Initial beats size is large enough to hold beats for 240 bpm
                beats = new ArrayList<BeatInfo>((int)(length / 44100 / 4) * 4 * 3);

                float[] E = new float[43]; // Energy averages for last second
                float e; // Instant sound energy
                float avgE; // Average sound energy over the interval
                double V; // Variance
                double C = 1.4; // Beat detection constant
                int count = 0; // Number of calculations done;
                int bufferSize = 2048;
                byte[] bytesBuffer = new byte[bufferSize * 2];
                //Beat detection loop
                int position = wavStream.read(bytesBuffer);
                while (position < length) {
                    short[] shortsBuffer = new short[bytesBuffer.length / 2];
                    ByteBuffer.wrap(bytesBuffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shortsBuffer);
                    short[] a = new short[shortsBuffer.length / 2]; // Buffer for right channel energy calculation
                    short[] b = new short[shortsBuffer.length / 2]; // Buffer for left channel energy calculation
                    // Load the left and right channel buffers
                    int index = 0;
                    for (int i = 0; i < shortsBuffer.length; i += 2){
                        a[index] = shortsBuffer[i];
                        b[index] = shortsBuffer[i + 1];
                        index++;
                    }
                    // Calculate instant sound energy
                    e = 0;
                    for (int i = 0; i < a.length; i++){
                        e += a[i] * a[i] + b[i] * b[i];
                    }
                    count++;
                    if (count == 43) {
                        // Once 43 samples have been obtained, check the data for beats
                        avgE = 0;
                        for (int i = 0; i < E.length; i++) {
                            avgE += E[i];
                        }
                        avgE /= E.length;
                        // Calculate C value
                        /*
                        V = 0;
                        for (int i = 0; i < E.length; i++){
                            V += E[i] - avgE;
                        }
                        V /= E.length;
                        C = -0.0025714 * V + 1.5142875;
                        */
                        for (int i = 0; i < E.length; i++){
                            // Compare instant and average energy to detect a beat
                            if (E[i] > C * avgE) {
                                beats.add(new BeatInfo((double)(bufferSize * 2 * i) / 44100 / 4, 1));
                                Log.d(LOG_TAG, "Beat detected at " + String.valueOf((double)position / 44100 / 4));
                            }
                        }
                    }
                    else if (count > 43) {
                        // Normal calculation once the first 43 samples have been obtained
                        avgE = 0;
                        for (int i = 0; i < E.length; i++) {
                            avgE += E[i];
                        }
                        avgE /= E.length;
                        // Calculate C value
                        /*
                        V = 0;
                        for (int i = 0; i < E.length; i++){
                            V += E[i] - avgE;
                        }
                        V /= E.length;
                        C = -0.0025714 * V + 1.5142875;
                        */
                        // Compare instant and average energy to detect a beat
                        if (e > C * avgE) {
                            beats.add(new BeatInfo((double)position / 44100 / 4, 1));
                            Log.d(LOG_TAG, "Beat detected at " + String.valueOf((double)position / 44100 / 4));
                        }
                    }
                    // Add instant sound energy to the beginning of E
                    float[] oldE = E.clone();
                    E[0] = e;
                    System.arraycopy(oldE, 0, E, 1, oldE.length - 1);
                    bytesBuffer = new byte[bufferSize * 2];
                    position += wavStream.read(bytesBuffer);
                }
                // Clean up beat data
                finalBeatList = new ArrayList<BeatInfo>(beats.size() / 3);
                for (int i = 0; i < beats.size(); i++){
                    if (i == 0)
                        finalBeatList.add(beats.get(i));
                    else {
                        BeatInfo nextBeat = beats.get(i);
                        BeatInfo lastBeat = finalBeatList.get(finalBeatList.size() - 1);
                        if (Math.abs(lastBeat.getTime() - nextBeat.getTime()) < 0.2 * lastBeat.getTime()) {
                            nextBeat = new BeatInfo((nextBeat.getTime() + lastBeat.getTime()) / 2, (nextBeat.getSalience() + lastBeat.getSalience()) / 2);
                            finalBeatList.set(finalBeatList.size() - 1, nextBeat);
                        }
                        else
                            finalBeatList.add(nextBeat);

                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return finalBeatList;
    }

}
