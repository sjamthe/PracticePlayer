package com.sjamthe.practiceplayer;

import android.util.Log;

/*
Find Pitch using ACF (Auto Correlation Function
Ref:
https://interactiveaudiolab.github.io/teaching/eecs352stuff/CS352-Single-Pitch-Detection.pdf
https://ccrma.stanford.edu/~pdelac/PitchDetection/icmc01-pitch.pdf
https://www.ijert.org/research/robust-pitch-detection-algorithm-of-pathological-speech-based-on-acf-and-amdf-IJERTV4IS050380.pdf
https://www.dafx.de/paper-archive/2019/DAFx2019_paper_54.pdf
https://gist.github.com/ajkluber/f293eefba2f946f47bfa
https://otexts.com/fpp2/autocorrelation.html
https://www.ymec.com/hp/signal2/acf.htm
 */

public class FrequencyAnalyzer {
    public static final double FREQ_A1 = 55.0d;
    public static final double SEMITONE_INTERVAL = Math.pow(2.0d, 1.0d/12.0d);
    // Example: to calculate any freq like C3 will be 12+3 semitones from A1 or 130.8Hz
    public static final double FREQ_C3 = FREQ_A1*Math.pow(SEMITONE_INTERVAL,(12.0d+3.0d));
    public static final double SAMPLING_SIZE = 44100.0d;
    public static final int FFT_SIZE = (int) Math.pow(2.0d,
            ((int) log2(SAMPLING_SIZE / (FREQ_A1*(SEMITONE_INTERVAL-1)))) + 1);

    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    public FrequencyAnalyzer() {
        Log.d("FFT", "FFT_SIZE :" + FFT_SIZE);
        new FFT4g(FFT_SIZE);
    }
}
