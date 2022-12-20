package com.sjamthe.practiceplayer;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.OptionalDouble;

public class FrequencyAnalyzerTest {

    public static final double SAMPLING_SIZE = 44100.0d;

    private static short[] generateSineWaveFreq(double frequencyOfSignal, int size) {
        short[] sin = new short[size];
        double samplingInterval =  SAMPLING_SIZE / frequencyOfSignal;
        for (int i = 0; i < size; i++) {
            double angle = (2.0 * Math.PI) * (i / samplingInterval);
            sin[i] = (short) (Math.sin(angle)*Short.MAX_VALUE);
        }
        return sin;
    }

    @Test
    public void testAllCSineWaves() {
        double totalErrors = 0;
        int count=0;
        for (int i=1; i<7; i++) {
            FrequencyAnalyzer analyzer = new FrequencyAnalyzer(SAMPLING_SIZE);
            double testPitch = Math.pow(2, i) * analyzer.FREQ_C1;
            short[] signal = generateSineWaveFreq(testPitch, analyzer.analyzeSize);
            analyzer.addData(signal);
            double detectedPitch = analyzer.pitchBuffer[0];
            double errorPct = Math.abs(analyzer.FreqToCent(testPitch)
                    - analyzer.FreqToCent(detectedPitch)) / analyzer.FreqToCent(testPitch)*100;
            System.out.printf("Error: %.2f, InputFreq: %.1f, testPitch %.1f\n",
                    errorPct, testPitch,detectedPitch);
            totalErrors += errorPct;
            count++;
        }
        Assert.assertTrue(totalErrors/count <= 1);
    }

    @Test
    public void testAllPitchesInSine() {
        double totalErrors = 0;
        int count=0;
        double testPitch = FrequencyAnalyzer.FREQ_C2;
        while(testPitch <= FrequencyAnalyzer.FREQ_C7) {
            FrequencyAnalyzer analyzer = new FrequencyAnalyzer(SAMPLING_SIZE);
            short[] signal = generateSineWaveFreq(testPitch, analyzer.analyzeSize);
            analyzer.addData(signal);
            //printToCSV(analyzer.fftData);
            // printToCSV(analyzer.psData);
            double detectedPitch = analyzer.pitchBuffer[0];
            // double errorPct = Math.abs(testPitch - detectedPitch)/(testPitch+1)*100;
            double errorPct = Math.abs(analyzer.FreqToCent(testPitch)
                    - analyzer.FreqToCent(detectedPitch)) / analyzer.FreqToCent(testPitch)*100;
            totalErrors += errorPct;
            System.out.printf("Error: %.2f, InputFreq: %.1f, testPitch %.1f\n",
                    errorPct, testPitch,detectedPitch);

            testPitch *= FrequencyAnalyzer.SEMITONE_INTERVAL;
            count++;
        }
        Assert.assertTrue(totalErrors/count <= 1);
    }

    @Test
    public void testOneFreq() {
        FrequencyAnalyzer analyzer = new FrequencyAnalyzer(SAMPLING_SIZE);
        double testPitch = FrequencyAnalyzer.FREQ_C3;
        System.out.println("Testing Freq: " + testPitch);
        short[] signal = generateSineWaveFreq(testPitch, analyzer.analyzeSize);
        analyzer.addData(signal);
        //printToCSV(analyzer.fftData);
        // printToCSV(analyzer.psData);
        double detectedPitch = analyzer.pitchBuffer[0];
        double errorPct = Math.abs(analyzer.FreqToCent(testPitch)
                - analyzer.FreqToCent(detectedPitch)) / analyzer.FreqToCent(testPitch)*100;
        System.out.printf("Error: %.2f, InputFreq: %.1f, testPitch %.1f\n",
                errorPct, testPitch,detectedPitch);

        Assert.assertTrue(errorPct < 1);
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
