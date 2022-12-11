package com.sjamthe.practiceplayer;

import static com.sjamthe.practiceplayer.FrequencyAnalyzer.FFT_SIZE;
import static com.sjamthe.practiceplayer.FrequencyAnalyzer.SAMPLING_SIZE;
import static com.sjamthe.practiceplayer.FrequencyAnalyzer.FREQ_A1;
import static com.sjamthe.practiceplayer.FrequencyAnalyzer.SEMITONE_INTERVAL;

import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FFT4gTest {

    double[] signal = new double[FFT_SIZE];
    FFT4g fft;

    @Before
    public void setUp() {
        fft = new FFT4g(FFT_SIZE);
    }

    private double[] generateRandomSample() {
        double[] a = new double[FFT_SIZE];
        int seed = 0;
        for (int i=0; i < a.length; i++) {
            a[i] = (seed * (1.0 / 259200.0));
            seed = ((seed * 7141 + 54773) % 259200);
        }
        return a;
    }

    private static double[] generateSineWaveFreq(double frequencyOfSignal) {
        double[] sin = new double[FFT_SIZE];
        double samplingInterval = (double) (SAMPLING_SIZE / frequencyOfSignal);
        for (int i = 0; i < sin.length; i++) {
            double angle = (2.0 * Math.PI * i) / samplingInterval;
            sin[i] = Math.sin(angle);
        }
        return sin;
    }

    private double compareArrays(double []a, double []b) {
        double max_err = 0;
        // Compare IFFT results with input
        double scale = 2.0/a.length;
        for (int j=0; j<a.length; j++) {
            double err = b[j] - a[j] * scale;
            err = Math.abs(err);
            max_err = Math.max(max_err, err);
            if(max_err > 0.000000001) {
                System.out.printf("Error At %d", j);
            }
        }
        return max_err;
    }

    @Test
    public void setup_isCorrect() {
        double[] a = generateRandomSample();
        Assert.assertEquals(FFT_SIZE, a.length);
    }

    private int largestIndex(double[] arr)
    {
        int i;

        // Initialize maximum element
        double max = arr[0];
        int maxItem = -1;

        for (i = 0; i < arr.length/2; i++) {
            double val = Math.sqrt(arr[2 * i] * arr[2 * i] + arr[2 * i + 1] * arr[2 * i + 1]);
            if (val > max) {
                max = val;
                maxItem = i;
            }
        }
        return maxItem;
    }

    @Test
    public void rdft_RandomSample() {
        double[] a = generateRandomSample();
        signal = a.clone(); // keep backup to compare with IFFT output
        fft.rdft(1, a); // FFT
        fft.rdft(-1, a); // IFFT
        Assert.assertEquals(FFT_SIZE, a.length);

        double max_err = compareArrays(a, signal);
        System.out.printf("Max Error %g\n", max_err);
        Assert.assertTrue(max_err < 0.000000001);
    }

    private static double Index2Freq(int i, double samples, int nFFT) {
        return (double) i * (samples / nFFT);
    }

    private static int Freq2Index(double freq, double samples, int nFFT) {
        return (int) (freq / (samples / nFFT));
    }

    @Test
    public void rdft_sineWave() {

        double fftBandSize = SAMPLING_SIZE/FFT_SIZE;

        for (int i=0; i<12*8; i++) {
            double testFreq = FREQ_A1/2 * Math.pow(SEMITONE_INTERVAL, i);
            double[] a = generateSineWaveFreq(testFreq);
            signal = a.clone(); // keep backup to compare with IFFT output
            fft.rdft(1, a); // FFT
            int maxIndex = largestIndex(a);
            double freq = Index2Freq(maxIndex, SAMPLING_SIZE, FFT_SIZE);
            System.out.printf("testFreq = %.1f, outputFreq = %.1f\n", testFreq, freq);

            //Validate that the predicted freq is within FFT band size.
            Assert.assertTrue(Math.abs(freq - testFreq) < fftBandSize);

            fft.rdft(-1, a); // IFFT
            double max_err = compareArrays(a, signal);
            Assert.assertTrue(max_err < 0.000000001);
        }
    }
}