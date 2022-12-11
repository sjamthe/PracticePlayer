package com.sjamthe.practiceplayer;

import android.util.Log;

import androidx.annotation.NonNull;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.util.ArrayList;
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
    static final double VOLUME_THRESHOLD = 2.0d;
    public static final double SEMITONE_INTERVAL = Math.pow(2.0d, 1.0d/12.0d);
    // Example:calculate any freq like C3 will be 12 (which is A2) +3 semitones from A1 or 130.8Hz
    public static final double FREQ_C3 = 2*FREQ_A1*Math.pow(SEMITONE_INTERVAL,3.0d); // 130.8Hz
    public static final double FREQ_C1 = FREQ_C3*Math.pow(2.0d, -2.0d); // 32.7Hz
    public static final double FREQ_C8 = FREQ_C3*Math.pow(2.0d, 5.0d); // 4186Hz or (2^5)*C3
    public static final double FREQ_MAX = FREQ_C8; // Max frequency we want to detect
    public static final double FREQ_MIN = FREQ_C1; // Min frequency we want to detect
    public static final double SAMPLING_SIZE = 44100.0d;

    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    public static final int FFT_SIZE = (int) Math.pow(2.0d,
            ((int) log2(SAMPLING_SIZE / (4*FREQ_A1*(SEMITONE_INTERVAL-1)))) + 1);
    public static final int ANALYZE_SIZE = 1470;

    public static final double FFT_FREQ_BAND_SIZE = SAMPLING_SIZE*2/FFT_SIZE;

    double [] haanData;
    double [] inputBuffer = new double[2*(FFT_SIZE)];
    double [] pitchBuffer = new double[(int) (30.0*SAMPLING_SIZE/ANALYZE_SIZE)]; // 10 secs
    private int nPitches = 0;

    int inputPos = 0;
    int analyzePos = 0;
    double samplingSize;
    double threshold = VOLUME_THRESHOLD;
    FFT4g fft;


    public FrequencyAnalyzer(LineChart lineChart) {
        this.samplingSize = SAMPLING_SIZE; // TODO : Support variable sampling_size

        Log.d("FFT", "FFT_SIZE :" + FFT_SIZE + " BAND :" + FFT_FREQ_BAND_SIZE);
        Log.d("FFT", "FREQ_A1 :" + FREQ_A1 + " FREQ_C1 :" + FREQ_C1);
        haanData = haan_window();
        fft = new FFT4g(FFT_SIZE);
        this.mainChart = lineChart;
    }

    private double [] haan_window() {
        double[] dataOut = new double[FFT_SIZE];
        double angleConst = 2*Math.PI/(FFT_SIZE -1);
        for(int i = 0; i < FFT_SIZE; i++) {
            dataOut[i] = 0.5 * (1 - Math.cos(i*angleConst));
        }
        return dataOut;
    }

    public void addData(short[] res) {
        assert (res.length < inputBuffer.length);
        int j;
        for(j=0; j<res.length;j++) {
            inputBuffer[inputPos++] = res[j];
            if(inputPos == inputBuffer.length) {
                // If we reached end of inputBuffer start from beginning
                inputPos = 0;
            }
            // make sure analyzePos doesn't exceed inputData.length
            if(inputPos%ANALYZE_SIZE == 0) {
                analyze();
            }
        }
    }

    void analyze() {
        new Thread(() -> {
            // List<Entry> list = new ArrayList<>();

            // Prepare data to analyze
            double[] fftData = new double[FFT_SIZE];
            for (int i=analyzePos, j=0; j< FFT_SIZE; i++, j++) {
                int pos = i% inputBuffer.length; // to support round robbin.
                fftData[j] = inputBuffer[pos]*haanData[j];
                // list.add(new Entry((float) j, (float) fftData[j])); // haan+input
            }
            // showDataOnChart(list);
            // Get FFT for the data.
            fft.rdft(1, fftData); // Note: rdft does in-place replacement of fftData
            //Get PowerSpectrum
            double [] psData = calcPowerSpectrum(fftData);
            //Calculate Auto Correlation from with inverseFFT
            fft.rdft(-1, psData); // Note: rdft does in-place replacement for psData
            // chartData(psData);
            if (Math.sqrt(psData[0]) >= this.threshold) {
                double pitch = findPitch(psData);
                pitchBuffer[nPitches++] = pitch;
                if(nPitches == pitchBuffer.length)
                    nPitches = 0;
            }
            chartData(pitchBuffer);
            // After analysis set analyze position for the next analyze call
            analyzePos += ANALYZE_SIZE;
            analyzePos = analyzePos%inputBuffer.length; // don't exceed inputBuffer.length
        }).start();
    }

    double [] calcPowerSpectrum(@NonNull double [] data) {
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

    /* Find pitch from ACF */
    private double findPitch(@NonNull double[] acf) {
        double foundPitch = -1.0d;

        int locFound = getMaxPsLoc(acf);
        double psFound = acf[locFound];

        //If we never found the pitch or found it but the power is less than 50% then notFound
        if (locFound == 0 || psFound < acf[0] * 0.5d) { // acf[0] has total power?
            return -1.0d;
        }
        return  locFound;
/*
        double d7 = i4;
        Double.isNaN(d7);
        double d8 = 44100.0d / d7;
        double d9 = get_fft_value_around_f(d8);
        double d10 = d8 / 3.0d;
        double d11 = get_fft_value_around_f(d10 * 2.0d);
        if (d9 < 0.24d || d11 <= 3.15d * d9 || d10 < f_c1) {
            double d12 = get_fft_value_around_f(1.5d * d8);
            if (d9 >= 0.24d && d12 > 1.0d * d9) {
                d10 = d8 / 2.0d;
            }
            d10 = d8 * 2.0d;
            double d13 = get_fft_value_around_f(d10);
            double d14 = 3.0d * d8;
            double d15 = get_fft_value_around_f(d14);
            if (d13 < 0.24d || d13 <= d9 * 1.25d || d15 >= d13 * 0.06d || d10 > f_c8) {
                d10 = (d15 < 0.24d || d15 <= 1.25d * d9 || d13 >= 0.06d * d15 || d14 > f_c8) ? d8 : d14;
                if (Math.sqrt((d9 * d9) + (d13 * d13) + (d15 * d15)) < 0.7d) {
                    return -1.0d;
                }
            }
        }
        double d16 = (FFTSIZE / 2) - 1;
        Double.isNaN(d16);
        int i8 = (int) (d16 / (44100.0d / d10));
        int i9 = 1;
        for (int i10 = 2; i10 <= i8; i10++) {
            double d17 = i10;
            Double.isNaN(d17);
            double d18 = d17 * 44100.0d;
            double d19 = i9;
            Double.isNaN(d19);
            int i11 = (int) (d18 / (d10 / d19));
            int i12 = i11 + 3;
            int i13 = FFTSIZE;
            if (i12 >= i13 / 2) {
                i12 = (i13 / 2) - 1;
            }
            int i14 = i11 - 3;
            double d20 = this.acf_data[i12];
            int i15 = i12;
            for (int i16 = i14; i16 <= i12; i16++) {
                double[] dArr3 = this.acf_data;
                if (dArr3[i16] >= d20) {
                    d20 = dArr3[i16];
                    i15 = i16;
                }
            }
            if (i15 != i14 && i15 != i12) {
                double d21 = i15;
                Double.isNaN(d21);
                d10 += d18 / d21;
                i9++;
            }
        }
        double d22 = i9;
        Double.isNaN(d22);
        return d10 / d22;

 */
    }

    // Find location/index in acf that corresponds to max power between our Min-Max freq range
    private int getMaxPsLoc(@NonNull double[] acf) {
        int scanLimit = 5; // Look up to these many tranches in acf data

        // Initialize max power with MAX freq value we care.
        int loc = ((int) (this.samplingSize / FREQ_MAX)) - 1; // Wavelength location for max FREQ
        double psMaxF = acf[loc];
        // Find PS around MAX Freq
        for (int i = loc + 1; i <= loc + scanLimit; i++) {
            if (acf[i] > psMaxF) {
                psMaxF = acf[i];
            }
        }

        double prevDelta = -1.0d;
        double psFound = 0;
        int locFound = 0;
        // Search for maxPower between MIN & MAX frequencies ranges.
        for (loc = loc + 1; loc <= (this.samplingSize / FREQ_MIN); loc++) {
            double psNow = acf[loc];
            for (int i = loc + 1; i <= loc + scanLimit; i++) {
                if (acf[i] > psNow) {
                    psNow = acf[i];
                }
            }
            // Validate if we found ps better than before
            double delta = psNow - psMaxF;
            if (delta < 0.0d && prevDelta > 0.0d && psMaxF > psFound) {
                // prev ps at loc was the max as we just delta is now negative
                locFound = loc - 1;
                psFound = psMaxF;
            }
            if (delta != 0.0d) { // should we check for +ve delta here?
                psMaxF = psNow;
                prevDelta = delta;
            }
        }
        return locFound;
    }

    private void chartData(double[] psData) {
        List<Entry> list = new ArrayList<>();

        for (int j=0; j<psData.length; j++) {
            list.add(new Entry((float) j, (float) psData[j]));
        }
        showDataOnChart(list);
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
