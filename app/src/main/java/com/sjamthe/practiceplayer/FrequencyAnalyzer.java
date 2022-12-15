package com.sjamthe.practiceplayer;

import android.util.Log;

import androidx.annotation.NonNull;

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

Ref:finding  tonal pitch / Sa
https://github.com/cuthbertLab/music21/blob/master/music21/analysis/discrete.py
 */

public class FrequencyAnalyzer {
    FullscreenActivity fullscreenActivity;

    public static final double FREQ_A1 = 55.0d;
    static final double VOLUME_THRESHOLD = 5.0d;
    static final int ANALYZE_SAMPLES_PER_SECOND = 15; // can be a variable
    public static final double SEMITONE_INTERVAL = Math.pow(2.0d, 1.0d/12.0d);
    // Example:calculate any freq like C3 will be 12 (which is A2) +3 semitones from A1 or 130.8Hz
    public static final double FREQ_C3 = 2*FREQ_A1*Math.pow(SEMITONE_INTERVAL,3.0d); // 130.8Hz
    public static final double FREQ_C1 = FREQ_C3*Math.pow(2.0d, -2.0d); // 32.7Hz
    public static final double FREQ_C8 = FREQ_C3*Math.pow(2.0d, 5.0d); // 4186Hz or (2^5)*C3
    public static final double FREQ_MAX = FREQ_C8; // Max frequency we want to detect
    public static final double FREQ_MIN = FREQ_C1; // Min frequency we want to detect

    // public static final double SAMPLING_SIZE = 44100.0d; -- moved to a variable.

    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    double [] haanData;
    double [] inputBuffer;
    double [] pitchBuffer; // how many pitches do we store? one pitch per analyze call.
    float [] centBuffer; // Stores Pitch converted to log2 scale and relative to FREQ_MIN
    private int nPitches = 0;

    int inputPos = 0;
    int analyzePos = 0;
    double samplingSize;
    double threshold = VOLUME_THRESHOLD;
    FFT4g fft;
    int fftSize;
    int analyzeSize;
    double[] fftData; // Stores FFT and iFFT
    double[] psData; // Stores power Spectrum
    static double log2Min;

    public FrequencyAnalyzer(double samplingSize) {
        this.samplingSize = samplingSize;
        this.fftSize = (int) Math.pow(2.0d,
                       ((int) log2(samplingSize / (4*FREQ_A1*(SEMITONE_INTERVAL-1)))) + 1);
        this.analyzeSize = (int) (this.samplingSize/ANALYZE_SAMPLES_PER_SECOND);
        Log.d("FFT", "FFT_SIZE :" + this.fftSize + " SAMPLING_SIZE :" + samplingSize);
        Log.d("FFT", "FREQ_A1 :" + FREQ_A1 + " FREQ_C1 :" + FREQ_C1);
        haanData = haan_window();
        this.inputBuffer = new double[2*(this.fftSize)];
        this.pitchBuffer = new double[(int) (30.0*this.samplingSize/this.analyzeSize)];
        this.centBuffer = new float[this.pitchBuffer.length];
        log2Min = log2(FREQ_MIN);
        fft = new FFT4g(this.fftSize);
        // this.mainChart = lineChart;
    }

    private final Runnable runUpdateChart = new Runnable() {
        @Override
        public void run() {
            fullscreenActivity.updateChart(centBuffer);
        }
    };

    private double [] haan_window() {
        double[] dataOut = new double[this.fftSize];
        double angleConst = 2*Math.PI/(this.fftSize -1);
        for(int i = 0; i < this.fftSize; i++) {
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
            if(inputPos%analyzeSize == 0) {
                analyze();
            }
        }
    }

    void analyze() {
        // Prepare data to analyze
        fftData = new double[fftSize];
        for (int i = analyzePos, j = 0; j < fftSize; i++, j++) {
            int pos = i % inputBuffer.length; // to support round robbin.
            fftData[j] = inputBuffer[pos] * haanData[j] / Short.MAX_VALUE;
        }
        // Get FFT for the data.
        fft.rdft(1, fftData); // Note: rdft does in-place replacement of fftData
        //Get PowerSpectrum
        psData = calcPowerSpectrum(fftData);
        //Calculate Auto Correlation from with inverseFFT
        fft.rdft(-1, psData); // Note: rdft does in-place replacement for psData
        // chartData(psData)
        double pitch = -1.0d;
        Log.d("ANALYZE", "power measure " + Math.sqrt(psData[0]));
        if (Math.sqrt(psData[0]) >= this.threshold) {
            pitch = findPitch(psData);
        }
        pitchBuffer[nPitches] = pitch;
        float cent = FreqToCent(pitch);
        centBuffer[nPitches] = cent;
        nPitches++;
        Log.d("ANALYZE", "pitch = " + pitch + " cent: " + cent);
        if (nPitches == pitchBuffer.length)
            nPitches = 0;
        // chartData(pitchBuffer);
        fullscreenActivity.fullScreenHandler.post(runUpdateChart);
        // After analysis set analyze position for the next analyze call
        analyzePos += analyzeSize;
        analyzePos = analyzePos % inputBuffer.length; // don't exceed inputBuffer.length
    }

    public static float FreqToCent(double freq) {
        if (freq < 0.0d) {
            return -1.0f;
        }
        return (float) ((log2(freq) - log2Min) * 12.0d * 100.0d);
    }

    double [] calcPowerSpectrum(@NonNull double [] data) {
        double[] psData = new double[data.length];
        // FFT Data is returned with real part and imaginary part alternately in same array
        // even numbers are real, odd are imaginary
        // We return an array with only real part (power/magnitude no phase)
        psData[0] = sumOfSquares(data[0], 0.0d);
        psData[1] = sumOfSquares(data[1], 0.0d);
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
        /*
        int locFound = getMaxPsLoc(acf);
        double psFound = acf[locFound];
        //If we never found the pitch
        if (locFound == 0) {
            return -1.0d;
        }
        //found pitch but the power is less than 50%
        // TODO: commenting below as it fails for pure sine wave where psFound is ~ 20%
        * if (psFound < acf[0] * 0.5d) {
            return -1.0d;
        }*
        double freq = samplingSize/locFound; */
        // Step 1: Find freq from ACF
        double freq = getFreqFromAcf(acf);
        double myfreq = getFreqFromAcf(acf);
        if(freq == -1.0d)
            return -1.0d;

        // Step 2: fine tuning freq using FFT
        double mynewFreq = getRightHarmonic(freq);
        double newFreq = fineTuneFreqWithFft(freq);
        //if((newFreq - freq) != 0.0)
        //    Log.d("ANALYZE", "freq :" + freq + " newFreq : " + newFreq + " diff " + (newFreq/freq));

        // Step 3: Fine tune step2 freq using ACF
        double finalFreq = fineTuneStep3(acf, newFreq);
        double myfinalFreq = fineTuneStep3(acf, mynewFreq);
        // if((finalFreq - newFreq) != 0.0)
           // Log.d("ANALYZE", "newFreq :" + newFreq + " finalFreq : " + finalFreq + " diff " + (finalFreq/newFreq));

        Log.d("TEST", "ACF freq:" + freq + " Harmonic Freq: " + newFreq +
                " Fine Tune freq: " + finalFreq);
        Log.d("TEST", "MY ACF freq:" + myfreq + " my Harmonic Freq: " + mynewFreq +
                " my Fine Tune freq: " + myfinalFreq);
        return finalFreq;
    }

    //Step 3: Fine tune step2 freq using ACF
    private double fineTuneStep3(double[] acf, double freq) {
        double midAcfPoint = (fftData.length / 2) - 1;
        int maxFactor = (int) (midAcfPoint / (samplingSize / freq));
        int factor = 1;
        for (int i=2; i<= maxFactor; i++) {
            double acfLoc = i * samplingSize;
            int iLoc = (int) ( acfLoc / (freq / factor));
            int upperBound = iLoc + 3;
            if (upperBound >= midAcfPoint + 1) {
                upperBound = (int) midAcfPoint;
            }
            int lowerBound = iLoc - 3;
            double psMax = acf[upperBound];
            int maxPsLoc = upperBound;
            // find maxPs between the bounds
            for (int j=lowerBound; j<= upperBound; j++) {
                if(acf[j] >= psMax) {
                    psMax = acf[j];
                    maxPsLoc = j;
                }
            }
            if (maxPsLoc != lowerBound && maxPsLoc != upperBound) {
                freq += acfLoc / (double) maxPsLoc; // Adjust frequency
                factor++;
            }
        }
        return freq / factor;
    }

    // Return the FFt value near this frequency.
    private double fftNearFreq(double freq) {
        // TODO: try min/max Loc as (SEMITONE_INTERVAL-1)/2 apart from 1
        int minLoc = (int) ((0.9791666666666666d * freq) / (samplingSize / fftData.length));
        int maxLoc = (int) ((1.0208333333333333d * freq) / (samplingSize / fftData.length));

        double maxAmp = 0;
        int loc = 0; // location/tranch that has max amplitude near our freq
        for (int i=minLoc; i<= maxLoc; i++) {
            // 2x as FFT is double the freq (complex number);
            double amp = sumOfSquares(fftData[2*i], fftData[2*i + 1]); // real & imaginary parts
            if(amp > maxAmp) {
                maxAmp = amp;
                loc = i;
            }
        }
        // We take an average by looking around two other tranches around loc.
        double preMaxAmp = sumOfSquares(fftData[2*(loc-1)], fftData[2*(loc-1) + 1]);
        double postMaxAmp = sumOfSquares(fftData[2*(loc+1)], fftData[2*(loc+1) + 1]);
        double avgAmp = (maxAmp + preMaxAmp + postMaxAmp)/3.0d;
       //  Log.d("ANALYZE", "avgAmp = " + avgAmp + " maxAmp = " + maxAmp +
          //              " preMaxAmp = " + preMaxAmp + " postMaxAmp = " + postMaxAmp);
                // return frequency as squareroot of the value found
        double nearFftFreq = Math.sqrt(avgAmp);
        // Log.d("ANALYZE", "freq = " + freq + " loc = " + loc);
        return nearFftFreq;
    }

    // Step 2: fine tuning freq by remove resonance using FFT
    double fineTuneFreqWithFft(double freq) {
        double fftVal = fftNearFreq(freq);
        double newFreq = freq/3.0d; // Why /3.0?
        double fftVal023 = fftNearFreq(newFreq*2.0d); // Why *2.0?
        // where are these constants coming from?
        if (fftVal < 0.24d || fftVal023 <= 3.15d * fftVal || newFreq < FREQ_MIN) {
            double fftVal15 = fftNearFreq(1.5d * freq); // seems unnecessary
            if(fftVal >= 0.24d && fftVal15 > 1.0d * fftVal) {
                newFreq = freq / 2.0; // shift up from freq/3.0
                // this step appears unnecessary as newFreq is over written after this
            } else {// maybe all this should be in else.
                newFreq = freq * 2.0; // not really lower any more
                double fftVal20 = fftNearFreq(newFreq);
                double upperFreq = 3.0d * freq;
                double fftVal30 = fftNearFreq(upperFreq);
                if (fftVal20 < 0.24d || fftVal20 <= fftVal * 1.25d || fftVal30 >= fftVal20 * 0.06d || newFreq > FREQ_MAX) {
                    if (fftVal30 < 0.24d || fftVal30 <= 1.25d * fftVal || fftVal20 >= 0.06d * fftVal30 || upperFreq > FREQ_MAX) {
                        newFreq = freq;
                    } else {
                        newFreq = upperFreq;
                    }
                    if (Math.sqrt((fftVal * fftVal) + (fftVal20 * fftVal20) + (fftVal30 * fftVal30)) < 0.7d) {
                        return -1.0d;
                    }
                }
            }
        }
        return newFreq;
    }

    // Step 2: Find the right harmonics
    // TODO: This function gives 2x values for real harmonium sounds but the other one works
    double getRightHarmonic(double freq) {
        // Check upper and lower harmonics till MIN/MAX range
        double maxVal = -1;
        double selectedFreq = -1;
        double fftVal = 0;
        //for (double testFreq=freq/8; testFreq <= freq*8; testFreq=testFreq + freq/8) {
        for (int i=1; i<= 8; i++) {
            double testFreq = i*freq;
            if (testFreq >= FREQ_MIN && testFreq <= FREQ_MAX) {
                fftVal = fftNearFreq(testFreq);
            }
            if(fftVal > maxVal) {
                maxVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        for (int i=8; i<= 2; i++) {
            double testFreq = freq/i;
            if (testFreq >= FREQ_MIN && testFreq <= FREQ_MAX) {
                fftVal = fftNearFreq(testFreq);
            }
            if(fftVal > maxVal) {
                maxVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        return selectedFreq;
    }

    /*
    double getRightHarmonic(double freq) {
        double testFreq = freq;
        double fftVal = fftNearFreq(testFreq);
        Log.d("TEST", "orig testFreq: " + testFreq + " fftVal " + fftVal);

        double selectedFreq = freq; // default selection
        double selectedVal = fftVal; // default selection
        testFreq = freq/2.0d;
        if (testFreq >= FREQ_MIN) {
            fftVal = fftNearFreq(testFreq);
            Log.d("TEST", "1/2 testFreq: " + testFreq + " fftVal " + fftVal);
            if(fftVal > selectedVal) {
                selectedVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        testFreq = freq/3.0d;
        if (testFreq >= FREQ_MIN) {
            fftVal = fftNearFreq(testFreq);
            Log.d("TEST", "1/3 testFreq: " + testFreq + " fftVal " + fftVal);
            if(fftVal > selectedVal) {
                selectedVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        testFreq = freq/4.0d;
        if (testFreq >= FREQ_MIN) {
            fftVal = fftNearFreq(testFreq);
            Log.d("TEST", "1/4 testFreq: " + testFreq + " fftVal " + fftVal);
            if(fftVal > selectedVal) {
                selectedVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        testFreq = freq*2.0d;
        if (testFreq <= FREQ_MAX) {
            fftVal = fftNearFreq(testFreq);
            Log.d("TEST", "2x testFreq: " + testFreq + " fftVal " + fftVal);
            if(fftVal > selectedVal) {
                selectedVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        testFreq = freq*3.0d;
        if (testFreq <= FREQ_MAX) {
            fftVal = fftNearFreq(testFreq);
            Log.d("TEST", "3x testFreq: " + testFreq + " fftVal " + fftVal);
            if(fftVal > selectedVal) {
                selectedVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        testFreq = freq*4.0d;
        if (testFreq <= FREQ_MAX) {
            fftVal = fftNearFreq(testFreq);
            Log.d("TEST", "4x testFreq: " + testFreq + " fftVal " + fftVal);
            if(fftVal > selectedVal) {
                selectedVal = fftVal;
                selectedFreq = testFreq;
            }
        }
        Log.d("TEST", "Selected Harmonic Freq: " + selectedFreq);
        return selectedFreq;
    }
     */

    // Find location/index in acf that corresponds to max power between our Min-Max freq range
   private double mygetFreqFromAcf(@NonNull double[] acf) {
       // int startLoc = ((int) (this.samplingSize / FREQ_MAX)) - 5;
       int startLoc = 1;
       int stopLoc = ((int) (this.samplingSize / FREQ_MIN)) + 10;
       // int stopLoc = (int) (this.samplingSize/2);
       double psMaxF = -1;
       int locFound = 0;
       for (int loc = startLoc; loc <= stopLoc; loc++) {
           if (acf[loc] > psMaxF) {
               psMaxF = acf[loc];
               locFound = loc;
           }
       }
       //If we never found the pitch
       if (locFound == 0) {
           return -1.0d;
       }
       //found pitch but the power is less than 50%
       // TODO: commenting below as it fails for pure sine wave where psFound is ~ 20%
        /* if (psFound < acf[0] * 0.5d) {
            return -1.0d;
        }*/
       double freq = samplingSize / locFound;
       return freq;
    }

    private double getFreqFromAcf(@NonNull double[] acf) {
        int scanLimit = 5; // Look up to these many tranches in acf data

        // Initialize max power with MAX freq value we care.
        // int loc = ((int) (this.samplingSize / FREQ_MAX)) - 1; // Wavelength location for max FREQ
        int loc = 0; //TEST: see if we can detect MAX_FREQ
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
                // prev ps at loc was the max as delta is now negative
                locFound = loc - 1;
                psFound = psMaxF;
            }
            if (delta != 0.0d) { // should we check for +ve delta here?
                psMaxF = psNow;
                prevDelta = delta;
            }
        }
        //If we never found the pitch
        if (locFound == 0) {
            return -1.0d;
        }
        //found pitch but the power is less than 50%
        // TODO: commenting below as it fails for pure sine wave where psFound is ~ 20%
        /* if (psFound < acf[0] * 0.5d) {
            return -1.0d;
        }*/
        double freq = samplingSize/locFound;
        return freq;
    }
/*
    private void chartData(float[] psData) {
        List<Entry> list = new ArrayList<>();

        for (int j=0; j<psData.length; j++) {
            list.add(new Entry((float) j, psData[j]));
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
        // yAxis.setAxisMaximum((float) FREQ_C3*4);
        // yAxis.setAxisMinimum((float) FREQ_C3/2);
        XAxis xAxis = mainChart.getXAxis();
        // xAxis.setAxisMaximum(100);
        Legend l = mainChart.getLegend();
        l.setEnabled(false);
        mainChart.invalidate();
    }
 */
} // FrequencyAnalyzer class
