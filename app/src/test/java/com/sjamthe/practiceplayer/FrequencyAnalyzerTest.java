package com.sjamthe.practiceplayer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.OptionalDouble;

public class FrequencyAnalyzerTest {

    public static final double SEMITONE_INTERVAL = Math.pow(2.0d, 1.0d/12.0d);
    public static final double SAMPLING_SIZE = 44100.0d;
    private FrequencyAnalyzer analyzer;

    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    @Before
    public void setUp() {
        analyzer = new FrequencyAnalyzer(SAMPLING_SIZE);
    }

    private static short[] generateSineWaveFreq(double frequencyOfSignal, int size) {
        short[] sin = new short[size];
        double samplingInterval = (double) (SAMPLING_SIZE / frequencyOfSignal);
        for (int i = 0; i < size; i++) {
            double angle = (2.0 * Math.PI) * (i / samplingInterval);
            sin[i] = (short) (Math.sin(angle)*Short.MAX_VALUE);
        }
        return sin;
    }

    @Test
    public void testSingleFreqSineWave() {
        //for (int i=7,j=0; i>=1; i--, j++) {
        for (int i=0,j=0; i<=7; i++, j++) {
            FrequencyAnalyzer analyzer = new FrequencyAnalyzer(SAMPLING_SIZE);
            double testPitch = Math.pow(2, i) * analyzer.FREQ_C1;
            System.out.println("Testing Freq: " + testPitch);
            short[] signal = generateSineWaveFreq(testPitch, analyzer.analyzeSize);
            analyzer.addData(signal);
            //printToCSV(analyzer.fftData);
            // printToCSV(analyzer.psData);
            double detectedPitch = analyzer.pitchBuffer[0];
            double errorPct = Math.abs(analyzer.FreqToCent(testPitch)
                    - analyzer.FreqToCent(detectedPitch)) / analyzer.FreqToCent(testPitch)*100;
            System.out.println("Error: " + errorPct + " InputFreq: " + testPitch + " OUTPUT: " + detectedPitch);
        }
    }

    @Test
    public void testMaxFreq() {
        FrequencyAnalyzer analyzer = new FrequencyAnalyzer(SAMPLING_SIZE);
        double testPitch = analyzer.FREQ_MAX+200;
        System.out.println("Testing Freq: " + testPitch);
        short[] signal = generateSineWaveFreq(testPitch, analyzer.analyzeSize);
        analyzer.addData(signal);
        //printToCSV(analyzer.fftData);
        // printToCSV(analyzer.psData);
        double detectedPitch = analyzer.pitchBuffer[0];
        double errorPct = Math.abs(analyzer.FreqToCent(testPitch)
                - analyzer.FreqToCent(detectedPitch)) / analyzer.FreqToCent(testPitch)*100;
        System.out.println("Error: " + errorPct + " InputFreq: " + testPitch + " OUTPUT: " + detectedPitch);

    }

    private void printToCSV(short[] signal) {
        for (int i=0; i<signal.length; i++) {
            String val = String.valueOf(signal[i]);
            System.out.print(val + ",");
        }
        System.out.println();
    }

    private void printToCSV(double[] signal) {
        boolean lowValue = true;
        OptionalDouble maxVal = Arrays.stream(signal).max();
        if(maxVal.getAsDouble() > 10000.0d)  {
            lowValue = false;
        }
        for (int i=0; i<signal.length; i++) {
            if(lowValue)
                System.out.printf("%.4f,", signal[i]);
            else
                System.out.printf("%.0f,", signal[i]);
        }
        System.out.println();
    }
}
