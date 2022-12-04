package com.sjamthe.practiceplayer;

import android.util.Log;

public class FrequencyAnalyzer {
    private static final double FREQ_A3 = Math.pow(2.0d, 0.0d) * 220.0d;
    private static final double FREQ_A3_SHARP = Math.pow(2.0d, 0.08333333333333333d) * 220.0d;
    public static final int FFT_SIZE = (int) Math.pow(2.0d,
            ((int) log2(44100.0d / (FREQ_A3_SHARP - FREQ_A3))) + 1);

    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    public FrequencyAnalyzer() {
        Log.d("FFT", "FFT_SIZE :" + FFT_SIZE);
        new FFT4g(FFT_SIZE);
    }
}
