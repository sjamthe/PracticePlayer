package com.sjamthe.practiceplayer;

import static com.sjamthe.practiceplayer.FrequencyAnalyzer.FFT_SIZE;

import junit.framework.TestCase;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FFT4gTest {

    double[] a = new double[FFT_SIZE];
    double[] signal = new double[FFT_SIZE];
    FFT4g fft;

    @Before
    public void setUp() throws Exception {

        int seed = 0;
        for (int i=0; i < a.length; i++) {
            a[i] = (seed * (1.0 / 259200.0));
            signal[i] = a[i]; // keep backup to compare output
            seed = ((seed * 7141 + 54773) % 259200);
        }

        fft = new FFT4g(FFT_SIZE);

    }

    @Test
    public void setup_isCorrect() {
        Assert.assertEquals(FFT_SIZE, a.length);
    }

    @Test
    public void rdft_works() {
        double max_err = 0;
        fft.rdft(1, a);
        fft.rdft(-1, a);
        Assert.assertEquals(FFT_SIZE, a.length);
        double scale = 2.0/FFT_SIZE;

        for (int j=0; j<a.length; j++) {
            double err = signal[j] - a[j] * scale;
            err = Math.abs(err);
            max_err = ((max_err) > (err) ? (max_err) : (err));
            if(max_err > 0.000000001) {
                System.out.printf("Error At %d", j);
            }
        }
        System.out.printf("Max Error %g", max_err);
        Assert.assertTrue(max_err < 0.000000001);
    }
}