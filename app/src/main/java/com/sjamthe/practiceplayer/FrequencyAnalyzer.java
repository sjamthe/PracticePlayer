package com.sjamthe.practiceplayer;

import android.util.Log;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

Hanning window is used to smooth out the signal and improve its frequency response.
 */

public class FrequencyAnalyzer {
    LineChart mainChart;
    public static final double FREQ_A1 = 55.0d;
    public static final double SEMITONE_INTERVAL = Math.pow(2.0d, 1.0d/12.0d);
    // Example:calculate any freq like C3 will be 12 (which is A2) +3 semitones from A1 or 130.8Hz
    // public static final double FREQ_C3 = FREQ_A1*Math.pow(SEMITONE_INTERVAL,(12.0d+3.0d));
    public static final double SAMPLING_SIZE = 44100.0d;
    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    public static final int FFT_SIZE = (int) Math.pow(2.0d,
            ((int) log2(SAMPLING_SIZE / (4*FREQ_A1*(SEMITONE_INTERVAL-1)))) + 1);

    public static final double FFT_FREQ_BAND_SIZE = SAMPLING_SIZE*2/FFT_SIZE;

    double [] haanData;
    double [] inputData = new double[2*(FFT_SIZE)];
    int inputPos = 0;
    int analyzePos = 0;
    FFT4g fft;

    public FrequencyAnalyzer(LineChart lineChart) {
        Log.d("FFT", "FFT_SIZE :" + FFT_SIZE + " BAND :" + FFT_FREQ_BAND_SIZE);
        haanData = haan_window();
        fft = new FFT4g(FFT_SIZE);
        this.mainChart = lineChart;
    }

    private double [] haan_window() {
        double[] dataOut = new double[FrequencyAnalyzer.FFT_SIZE];
        double angleConst = 2*Math.PI/(FrequencyAnalyzer.FFT_SIZE -1);
        for(int i = 0; i < FrequencyAnalyzer.FFT_SIZE; i++) {
            dataOut[i] = 0.5 * (1 - Math.cos(i*angleConst));
        }
        return dataOut;
    }

    public void addData(short[] res) {
        assert (res.length < inputData.length);
        int j;
        for(j=0; j<res.length;j++) {
            inputData[inputPos++] = res[j];
            if(inputPos == inputData.length) {
                // If we reached end of inputData start from beginning
                inputPos = 0;
            }
        }

        if(inputPos > FFT_SIZE) {
            analyze();
        }
    }

    void analyze() {
        new Thread(() -> {
            List<Entry> list = new ArrayList<>();

            // Prepare data to analyze
            double[] fftData = new double[FFT_SIZE];
            for (int i=analyzePos, j=0; j< FFT_SIZE; i++, j++) {
                fftData[j] = inputData[i]*haanData[j];
                // list.add(new Entry((float) j, (float) fftData[j])); // haan+input
            }
            // showDataOnChart(list);
            // Get FFT for the data.
            fft.rdft(1, fftData); // FFT
            //Get PowerSpectrum
            double [] psData = calcPowerSpectrum(fftData);
            //Calculate Auto Correlation from with inverseFFT
            fft.rdft(-1, psData);

            for (int j=0; j<psData.length/2; j++) {
                list.add(new Entry((float) j, (float) psData[j]));
            }
            showDataOnChart(list);

            // After analysis
            analyzePos += FFT_SIZE;
            if (analyzePos > FFT_SIZE)
                analyzePos = 0; // reset to beginning
        }).start();
    }

    double [] calcPowerSpectrum(double [] data) {
        double[] psData = new double[data.length];
        // FFT Data is returned with real part and imaginary part alternately in same array
        // even numbers are real, odd are imaginary
        // We return an array with only real part (power/magnitude no phase)
        psData[0] = data[0]*data[0];
        psData[1] = data[1]*data[1];
        for (int i = 1; i < data.length / 2; i++) {
            int realIndex = i * 2;
            int imagIndex = realIndex + 1;
            psData[realIndex] = sumOfSquares(data[realIndex], data[imagIndex]);
            psData[imagIndex] = 0.0d;
        }
        return psData;
    }

    double sumOfSquares(double a, double b) {
        return a*a + b*b;
    }
    
    void showDataOnChart(List<Entry> list) {
        ArrayList<ILineDataSet> dataSets = new ArrayList<>();
        LineDataSet lineDataSet = new LineDataSet(list, "FFT data");
        lineDataSet.setDrawCircles(false); // don't draw points
        LineData data = new LineData(lineDataSet);
        mainChart.setData(data);
        mainChart.getAxisRight().setEnabled(false); // disable right axis, we only need left
        YAxis yAxis = mainChart.getAxisLeft();
        //yAxis.setAxisMaximum(Short.MAX_VALUE);
        yAxis.setAxisMinimum(0);
        XAxis xAxis = mainChart.getXAxis();
        // xAxis.setAxisMaximum(100);
        Legend l = mainChart.getLegend();
        l.setEnabled(false);
        mainChart.invalidate();
    }
} // FrequencyAnalyzer class
