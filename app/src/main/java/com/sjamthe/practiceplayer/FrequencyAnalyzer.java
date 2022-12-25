package com.sjamthe.practiceplayer;

import android.util.Log;

import androidx.annotation.NonNull;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.OptionalDouble;

import be.tarsos.dsp.pitch.McLeodPitchMethod;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchDetector;

/*
Find Pitch using ACF (Auto Correlation Function)

1. Make sure window size for analysis contains at least 3 full cycles of lowest freq to detect.
for MIN_C1 of 32.5Hz = 44100/(32.5/3)=4070.
Another way to look at it is, if ANALYZE_SAMPLES_PER_SECOND = 15 we have 44100/(15) = 2940 size
 so we can analyze 15*4 or 45Hz or more well. which is ok as A1 = 44Hz.
2. Apply Haan window analyzedata
3. Calculate FFT
4. Find the amplitude for cepstrum
5. take IFFT to get Cepstrum

Detecting Pitch
1. Use Cepstrum data to find frequency using ACF method. The 1st peak (in our interested range) is
the location of F0. F0 = Sampling-size/loc - loc of the peak
2. Look at the power of harmonic frequencies to get the right F0
3. Step 3 - Take a mean +=3 of the freq found.

Ref:
https://interactiveaudiolab.github.io/teaching/eecs352stuff/CS352-Single-Pitch-Detection.pdf
https://ccrma.stanford.edu/~pdelac/PitchDetection/icmc01-pitch.pdf
https://www.ijert.org/research/robust-pitch-detection-algorithm-of-pathological-speech-based-on-acf-and-amdf-IJERTV4IS050380.pdf
https://www.dafx.de/paper-archive/2019/DAFx2019_paper_54.pdf
https://otexts.com/fpp2/autocorrelation.html -1
https://www.ymec.com/hp/signal2/acf.htm

Hanning window is used to smooth out the signal and improve its frequency response.

Ref:finding  tonal pitch / Sa
https://github.com/cuthbertLab/music21/blob/master/music21/analysis/discrete.py
 */

public class FrequencyAnalyzer {
    private final PitchDetector detector;
    FullscreenActivity fullscreenActivity;

    public static  String[] NOTES =
            new String[] {"C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B"};
    public static final double FREQ_A1 = 55.0d;
    static final double VOLUME_THRESHOLD = 5.0d; // 5.0 is default.
    static final int ANALYZE_SAMPLES_PER_SECOND = 15; // can be a variable
    public static final double SEMITONE_INTERVAL = Math.pow(2.0d, 1.0d/12.0d); // 1.05946
    // Example:calculate any freq like C3 will be 12 (which is A2) +3 semitones from A1 or 130.8Hz
    public static final double FREQ_C3 = 2*FREQ_A1*Math.pow(SEMITONE_INTERVAL,3.0d); // 130.8Hz
    public static final double FREQ_C2 = FREQ_C3*Math.pow(2.0d, -1.0d);
    public static final double FREQ_C1 = FREQ_C3*Math.pow(2.0d, -2.0d);
    public static final double FREQ_C4 = FREQ_C3*Math.pow(2.0d, 1.0d);
    public static final double FREQ_C5 = FREQ_C3*Math.pow(2.0d, 2.0d);
    public static final double FREQ_C6 = FREQ_C3*Math.pow(2.0d, 3.0d);
    public static final double FREQ_C7 = FREQ_C3*Math.pow(2.0d, 4.0d);
    public static final double FREQ_C8 = FREQ_C3*Math.pow(2.0d, 5.0d); // 4186Hz or (2^5)*C3
    public static final double FREQ_MAX = FREQ_C8; // Max frequency we want to detect
    public static final double FREQ_MIN = FREQ_C1 - 5; // Min frequency we want to detect

    static double log2C1 = log2(FREQ_C1);

    public static double log2(double d) {
        return Math.log(d) / Math.log(2.0d);
    }

    double [] haanData;
    double [] inputBuffer;
    double [] pitchBuffer; // how many pitches do we store? one pitch per analyze call.
    float [] centBuffer; // Stores Pitch converted to log2 scale and relative to FREQ_MIN
    float lastCent;
    double averageCent = 0;
    double nCents = 0;
    // Hashmap to store perfectCent as key and # of occurrence as value.
    HashMap<Integer, Integer> notesCounter = new HashMap<Integer, Integer>();
    private int nPitches = 0;

    int inputPos = 0;
    int analyzePos = 0;
    double samplingSize;
    double threshold = VOLUME_THRESHOLD;
    FFT4g fft;
    int fftSize;
    int analyzeSize;
    double[] signal; // Stores input to FFT after applying haan window to analyze data.
    double[] fftData; // Stores output of FFT
    double[] psData; // Stores power Spectrum of FFT (amplitude)
    double[] acfData; // Stores IFFT or Power Spectrum (Cespstrum)

    // for debugging
    private void printToCSV(short[] data) {
        for (int i=0; i<data.length; i++) {
            String val = String.valueOf(data[i]);
            System.out.print(val + ",");
        }
        System.out.println();
    }

    private void printToCSV(double[] data) {
        boolean lowValue = true;
        OptionalDouble maxVal = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            maxVal = Arrays.stream(data).max();
            if(maxVal.getAsDouble() > 10000.0d)  {
                lowValue = false;
            }
            for (int i=0; i<data.length; i++) {
                if(lowValue)
                    System.out.printf("%4.3e\n", data[i]);
                else
                    System.out.printf("%.0f\n", data[i]);
            }
        }
        System.out.println();
    }

    // Conversion Functions
    public static float freqToCent(double freq) {
        if (freq < FREQ_C1) {
            return -1.0f;
        }
        return (float) ((log2(freq) - log2C1) * 1200.0d); // Each octave = 1200 cents
    }

    public static double centToFreq(float cent) {
        if (cent < 0) {
            return -1.0f;
        }
        return (double)  FREQ_C1*Math.pow(2, cent/1200.0d) ;
    }

    // Round up cent to perfect note in every octave
    public static Integer centToPerfectCent(float cent) {
        return Math.round(cent/100)*100;
    }

    public static int centToNote(float cent) {
        if(cent < 0)
            return -1;
        return Math.round(cent/100)%12;
        // String strNote = notes[note] + octave;
    }

    public static int centToOctave(float cent) {
        if(cent < 0)
            return -1;
        return Math.round(cent/1200) + 1; // Each Octave has 1200 cents;
    }

    void addNoteToNotesCounter(double freq) {
        if(freq < 0)
            return;

        Integer perfectNote = centToPerfectCent(freqToCent(freq));
        Integer count = notesCounter.get(perfectNote);
        if(count == null) {
            notesCounter.put(perfectNote, 0);
            count = 0;
        }
        notesCounter.put(perfectNote, count+1);
    }

    // Cumulative amplitude - this comes very close to acfdata[0]
    private double sumSignal(double[] data) {
        double sum = 0;
        if(data.length < 1) {
            return sum;
        }

        for (int i=0; i< data.length; i++) {
            sum += Math.abs(data[i]);
        }
        return sum;
    }

    public String getSongKey() {
        String songKey = "";
        Integer largestKey = null;
        int largestValue = Integer.MIN_VALUE;
        for (Integer key : notesCounter.keySet()) {
            if (notesCounter.get(key) > largestValue) {
                largestKey = key;
                largestValue = notesCounter.get(key);
            }
        }
        if(largestValue > 0) {
            songKey = FrequencyAnalyzer.NOTES[FrequencyAnalyzer.centToNote(largestKey)] +
                    String.valueOf(FrequencyAnalyzer.centToOctave(largestKey));
        }
        return songKey;
    }

    public FrequencyAnalyzer(double samplingSize) {
        this.samplingSize = samplingSize;
        this.fftSize = (int) Math.pow(2.0d,
                       ((int) log2(samplingSize / (4*FREQ_A1*(SEMITONE_INTERVAL-1)))) + 1);
        this.analyzeSize = (int) (this.samplingSize/ANALYZE_SAMPLES_PER_SECOND);
       // Log.d("FFT", "FFT_SIZE :" + this.fftSize + " SAMPLING_SIZE :" + samplingSize);
       // Log.d("FFT", "FREQ_A1 :" + FREQ_A1 + " FREQ_C1 :" + FREQ_C1);
        haanData = haanWindow();
        this.inputBuffer = new double[2*(this.fftSize)];
        this.pitchBuffer = new double[(int) (30.0*this.samplingSize/this.analyzeSize)];
        this.centBuffer = new float[this.pitchBuffer.length];
        fft = new FFT4g(this.fftSize);

        detector = new McLeodPitchMethod((float) this.samplingSize, this.fftSize);// better than Yin
    }

    private final Runnable runUpdateChart = new Runnable() {
        @Override
        public void run() {
            fullscreenActivity.updateChart(lastCent);
        }
    };

    private double [] haanWindow() {
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
        double pitch = -1;
        signal = new double[fftSize];
        if(sumSignal(inputBuffer) >= this.threshold) {
            for (int i = analyzePos, j = 0; j < fftSize; i++, j++) {
                int pos = i % inputBuffer.length; // to support round robbin.
                signal[j] = inputBuffer[pos] * haanData[j] / Short.MAX_VALUE;
            }

            fftData = signal.clone();
            // Get FFT for the data.
            fft.rdft(1, fftData); // Note: rdft does in-place replacement of fftData
            //Get PowerSpectrum
            psData = calcPowerSpectrum(fftData);
            acfData = psData.clone();
            //Calculate Auto Correlation from with inverseFFT
            fft.rdft(-1, acfData); // Note: rdft does in-place replacement for acfData
            normalizeACF(); //Normalize ACF data
            pitch = findPitch();
        }
        pitchBuffer[nPitches%pitchBuffer.length] = pitch;
        addNoteToNotesCounter(pitch); // Add Note to the notesCounter;

        float yinPitch = -1;
        float yinProb = -1;
        PitchDetectionResult yinResult = null;
        float[] sigCopy = new float[fftSize];
        for (int i=0; i< sigCopy.length; i++)
            sigCopy[i] = (float) signal[i];
/*
        yinResult = detector.getPitch(sigCopy);
        if(yinResult != null) {
            yinPitch = yinResult.getPitch();
            yinProb = yinResult.getProbability();
            Log.d("YIN", "YinPitch:" + yinPitch + " prob:" + yinProb
            + " myPitch:" + pitch);
        }
*/
        lastCent = freqToCent(pitch);
        if (pitch > 0) {
            averageCent = (averageCent*nCents + lastCent)/(nCents + 1);
            nCents++;
        }
        centBuffer[nPitches%pitchBuffer.length] = lastCent;
        nPitches++;

        if(nPitches%500 == 0) {
            Log.i("KEY", "SongKey:" + getSongKey());
        }

        if(fullscreenActivity != null && fullscreenActivity.fullScreenHandler != null)
            fullscreenActivity.fullScreenHandler.post(runUpdateChart);
        // After analysis set analyze position for the next analyze call
        analyzePos += analyzeSize;
        analyzePos = analyzePos % inputBuffer.length; // don't exceed inputBuffer.length
    }

    private void normalizeACF() {
        for (int i=1; i< acfData.length; i++) {
            acfData[i] = acfData[i] / acfData[0];
        }
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
            //Cepstrum should do a log, why are we not taking a log?
            psData[realIndex] = sumOfSquares(data[realIndex], data[imagIndex]);
            // TODO: Try SQRT of the power - gives more errors for sinusoidal freq itself
            // psData[realIndex] = Math.sqrt(psData[realIndex]);
            psData[imagIndex] = 0.0d;
        }
        return psData;
    }

    double sumOfSquares(double a, double b) {
        return a*a + b*b;
    }

    /* Go through all stages of pitch detection update array with current pitch and prev pitches */
    private double findPitch() {
        double[] freqs = new double[] {-1, -1, -1, -1, -1};
        double selectedFreq = -1;
        double finalFreq = -1;
        double signalPower = Math.sqrt(acfData[0]);

        if (signalPower >= this.threshold) {
            // Step 1: Find freq from ACF
            freqs = getTop5FreqFromAcf();
            // Step 2: Select pitch based on last 2 pitches, also adjust last 2 pitches based on current
            selectedFreq = selectCorrectPitch(freqs);
            // Step 3: Fine tune step2 freq using ACF
            finalFreq = fineTuneStep3(selectedFreq);
        }
        /* Logging only
        DecimalFormat df = new DecimalFormat("###.##");
        String freqsString = "";
        for (int i=0; i<freqs.length; i++) {
            freqsString = freqsString + ":freq" + i + ":" + df.format(freqs[i]) ;
        }
        double[] acfs = getAcfFromFreq(freqs);
        String acfsString = "";
        for (int i=0; i<acfs.length; i++) {
            acfsString = acfsString + ":acfs" + i + ":" + df.format(acfs[i]) ;
        }
        Log.d("FINAL", nPitches +  ":power:" + df.format(signalPower) +
                ":Note:" + CentToNote(FreqToCent(finalFreq)) +
                ":Octave:" + CentToOctave(FreqToCent(finalFreq)) +
                ":selectedFreq:" + df.format(selectedFreq) +
                ":finalFreq:"+ df.format(finalFreq) +
                freqsString + acfsString);
        */
        return finalFreq;
    }

    private double selectCorrectPitch(double[] freqs) {
        double MIN_ACF = 0.5;
        double[] acfs = getAcfFromFreq(freqs);
        double[] cents = new double[freqs.length]; // Easier to work with cents than freqs
        for (int i=0; i<freqs.length; i++) {
            cents[i] = freqToCent(freqs[i]);
        }
        // Get last 5 cents - remember we round robbin.
        double[] prevCents = new double[5];
        for (int i=1; i<=5; i++) {
            if(nPitches -i >= 0) {
                prevCents[i - 1] = this.centBuffer[nPitches - i];
            } else {
                prevCents[i - 1] = this.centBuffer[i - nPitches];
            }
        }
        // 1. If any harmonic from freqs matches any harmonics from last 5 freqs AND the harmonics
        // ACF > MIN_ACF; then select the matching harmonic.
        // 2. If there is no match of exact freq from last 5 then take one that is closest to the
        // averageCent and ACF > MIN_ACF.
        double selectedFreq = freqs[0];
        boolean found = false;
        for (int i=0; i<freqs.length; i++) {
            if(freqs[i] < -1 || acfs[i] < MIN_ACF) {
                break;
            }
            for (int j=0; j<prevCents.length; j++) {
                if(prevCents[j] < 0) {
                    continue;
                }
                if(Math.abs(cents[i] - prevCents[j]) <= 100) {
                    selectedFreq = freqs[i];
                    found = true;
                    break;
                }
            }
            if(found)
                break;
        }

        double minGap = 2400;
        if(!found && lastCent > 0) {
            // STEP 2 - compare with averageCent
            for (int i=0; i<freqs.length; i++) {
                if(acfs[i] >= MIN_ACF) {
                    double gap = Math.abs(cents[i] - lastCent);
                    if(gap <= minGap) {
                        selectedFreq = freqs[i];
                    }
                        minGap = gap;
                    }
                }
        }

        String freqStr = LogStr("freqs", freqs);
        String acfsStr = LogStr("acfs", acfs);
        DecimalFormat df = new DecimalFormat("###.##");
        Log.d("HPS", nPitches +  ":power:" + df.format(Math.sqrt(acfData[0])) +
                ":Note:" + centToNote(freqToCent(selectedFreq)) +
                ":Octave:" + centToOctave(freqToCent(selectedFreq)) +
                ":selectedFreq:" + df.format(selectedFreq) +
                freqStr + acfsStr);

        return selectedFreq;
/*
        // Else compare with past two pitches in pitchBuffer
        // If the last two frequencies are almost same (in same semitone) we prefer current
        // frequency also closest of all the harmonics.
        double prevPitch = pitchBuffer[(nPitches -1) % pitchBuffer.length];
        double prevPrevPitch =  pitchBuffer[(nPitches -2) % pitchBuffer.length];

        // We have some history to compare with. - NOTE: too much reliance on history with no
        // preference to current.
        if (prevPitch > 0 || prevPrevPitch > 0) {
            // If both prev pitches are same
            if (Math.abs(prevPrevPitch - prevPitch) <
                    (SEMITONE_INTERVAL - 1) * prevPitch) {
                for (int i = 0; i < freqs.length; i++) {
                    if (Math.abs(prevPitch - freqs[i]) <
                            (SEMITONE_INTERVAL - 1) * prevPitch) {
                        return freqs[i];
                    }
                }
            }
            double minRatio = 5;
            double foundPitch = -1;
            if (prevPitch < 0 && prevPrevPitch > 0) {
                // Select harmonic that is closest to last to last pitch
                for (int i = 0; i < freqs.length; i++) {
                    double ratio = prevPrevPitch / freqs[i];
                    if (ratio > 1 && ratio < minRatio) {
                        minRatio = ratio;
                        foundPitch = freqs[i];
                    } else if (ratio < 1 && 1/ratio < minRatio) {
                        minRatio = 1/ratio;
                        foundPitch = freqs[i];
                    }
                }
            } else {
                // Select harmonic that is closest to last pitch
                for (int i = 0; i < freqs.length; i++) {
                    double ratio = prevPitch/freqs[i];
                    if (ratio > 1 && ratio < minRatio) {
                        minRatio = ratio;
                        foundPitch = freqs[i];
                    } else if (ratio < 1 && 1/ratio < minRatio) {
                        minRatio = 1/ratio;
                        foundPitch = freqs[i];
                    }
                }
            }
            if (foundPitch > 0) {
                return foundPitch;
            }
        }
 */
        // We have no history to depend on so use HPS
    }

    // Look at harmonics of freqs[0]. Add all up with HPS and select one with max amp
    // Always boosting freqs[1]
    private double getPitchFromHPS(double[] freqs, double[] acfs) {
        // Calculate new ACFs based on dot product of harmonica
        /*
        int i, j, maxSearchIndex, maxBin;
        int harmonics = 5;
        double[] spectrum = fftData.clone();
        maxSearchIndex = spectrum.length/harmonics;

        maxBin = 1;
        for (j=1; j<=maxSearchIndex; j++) {
            for (i=1; i<=harmonics; i++) {
                spectrum[j] *= spectrum[j*i];
            }
            if (spectrum[j] > spectrum[maxBin]) {
                maxBin = j;
            }
        }

        // Fixing octave too high errors
        int correctMaxBin = 1;
        int maxsearch = maxBin * 3 / 4;
        for (i=2; i<maxsearch; i++) {
            if (spectrum[i] > spectrum[correctMaxBin]) {
                correctMaxBin = i;
            }
        }*/
        double selectedFreq = freqs[0];
        // Octave errors are a common problem in pitch measurements from HPS. Almost always in
        // these error cases, the pitch is detected one octave too high. To correct for this error,
        // postprocessing should be done with the following rule: IF the second peak amplitude
        // below initially chosen pitch is approximately 1/2 of the chosen pitch AND the ratio of
        // amplitudes is above a threshold (e.g., 0.2 for 5 harmonics), THEN select the lower
        // octave peak as the pitch
        /*if(acfs[0]/acfs[1] > 0.2) {
            selectedFreq = freqs[0];
        }*/
        /*
        if (Math.abs(correctMaxBin * 2 - maxBin) < 4) {
            if (spectrum[correctMaxBin]/spectrum[maxBin] > 0.2) {
                maxBin = correctMaxBin;
            }
        }*/

        String freqStr = LogStr("freqs", freqs);
        String acfsStr = LogStr("acfs", acfs);
        DecimalFormat df = new DecimalFormat("###.##");
        Log.d("HPS", nPitches +  ":power:" + df.format(Math.sqrt(acfData[0])) +
                ":Note:" + centToNote(freqToCent(selectedFreq)) +
                ":Octave:" + centToOctave(freqToCent(selectedFreq)) +
                ":selectedFreq:" + df.format(selectedFreq) +
                freqStr + acfsStr);

        return selectedFreq;
    }

    private String LogStr(String prefix, double[] vals) {
        String str = "";
        DecimalFormat df = new DecimalFormat("###.##");
        for (int i=0; i<vals.length; i++) {
            str = str + ":" + prefix + i + ":" + df.format(vals[i]) ;
        }
        return str;
    }

    // Step 3: Fine tune freq using ACF
    private double fineTuneStep3(double freq) {
        double inputFreq = freq;
        double loc = (samplingSize / freq);
        double midLoc = (fftData.length / 2) - 1;
        int maxFactor = (int) (midLoc / loc);
        int factor = 1;
        for (int i=2; i<= maxFactor; i++) {
            double acfLoc = i * samplingSize;
            int iLoc = (int) ( acfLoc / (freq / factor));
            int upperBound = iLoc + 3;
            if (upperBound >= midLoc + 1) {
                upperBound = (int) midLoc;
            }
            int lowerBound = iLoc - 3;
            double psMax = acfData[upperBound];
            int maxPsLoc = upperBound;
            // find maxPs between the bounds
            for (int j=lowerBound; j<= upperBound; j++) {
                if(acfData[j] >= psMax) {
                    psMax = acfData[j];
                    maxPsLoc = j;
                }
            }
            if (maxPsLoc != lowerBound && maxPsLoc != upperBound) {
                freq += acfLoc / (double) maxPsLoc; // Adjust frequency
                factor++;
            }
        }
        // Log.d("STEP3", "InFreq:" + inputFreq + " outFreq:" + freq/factor + " factor:"
        //        + factor);
        return freq / factor;
    }

    // Return square-root of the average of amplitudes (from fftdata) for
    // frequency given frequency and its nearest neighbours +-1
    private double fftNearFreq(double freq) {
        // why have min & max based on Freq? why noy just +-1 ? this seems biased to higher freq
         int minLoc = (int) ((0.9791666666666666d * freq) / (samplingSize / fftData.length));
         int maxLoc = (int) ((1.0208333333333333d * freq) / (samplingSize / fftData.length));
        // int minLoc = (int) (freq / (samplingSize / fftData.length)) -1;
        // int maxLoc = (int) (freq / (samplingSize / fftData.length)) +1;

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
        // We take an average with immediate neighbours.
        double preMaxAmp = sumOfSquares(fftData[2*(loc-1)], fftData[2*(loc-1) + 1]);
        double postMaxAmp = sumOfSquares(fftData[2*(loc+1)], fftData[2*(loc+1) + 1]);
        double avgAmp = (maxAmp + preMaxAmp + postMaxAmp)/3.0d;
        // return squareroot of the value found
        double nearFftFreq = Math.sqrt(avgAmp);
       /* Log.d("ANALYZE", "avgAmp = " + avgAmp + " maxAmp = " + maxAmp +
                " preMaxAmp = " + preMaxAmp + " postMaxAmp = " + postMaxAmp);
        Log.d("ANALYZE", "freq in = " + freq + " loc " + loc + " nearFftFreq = "
                + nearFftFreq + " maxAmp " + maxAmp);*/
        return nearFftFreq;
    }

    // Step 2: Look at FFT Amplitudes to see if a harmonic should be the right freq. TODO: why?
    double fineTuneFreqWithFft(double freq) {
        double fftVal15 = -1; double fftVal20 = -1; double fftVal30 = -1;
        double fftVal = fftNearFreq(freq);
        double newFreq = freq/3.0d; // Why /3.0?
        double fftVal023 = fftNearFreq(newFreq*2.0d); // Why *2.0?
        // where are these constants coming from?
        if (fftVal < 0.24d || fftVal023 <= 3.15d * fftVal || newFreq < FREQ_MIN) {
            fftVal15 = fftNearFreq(1.5d * freq); // seems unnecessary
            if(fftVal >= 0.24d && fftVal15 > 1.0d * fftVal) {
                newFreq = freq / 2.0; // shift up from freq/3.0
                // this step appears unnecessary as newFreq is over written after this
            } else {// maybe all this should be in else.
                newFreq = freq * 2.0; // not really lower any more
                fftVal20 = fftNearFreq(newFreq);
                double upperFreq = 3.0d * freq;
                fftVal30 = fftNearFreq(upperFreq);
                if (fftVal20 < 0.24d || fftVal20 <= fftVal * 1.25d
                        || fftVal30 >= fftVal20 * 0.06d || newFreq > FREQ_MAX) {
                    if (fftVal30 < 0.24d || fftVal30 <= 1.25d * fftVal ||
                            fftVal20 >= 0.06d * fftVal30 || upperFreq > FREQ_MAX) {
                        newFreq = freq;
                    } else {
                        newFreq = upperFreq;
                    }
                    if (Math.sqrt((fftVal * fftVal) + (fftVal20 * fftVal20)
                            + (fftVal30 * fftVal30)) < 0.7d) {
                        newFreq = -1.0d;
                    }
                }
            }
        }
       /* if(freq != newFreq) {
            Log.d ("ANALYZE", "in freq:" + freq + " newFreq:" + newFreq +
                    " fftVal:" + fftVal + " fftVal023:" + fftVal023 + " fftVal15:" + fftVal15 +
                    " fftVal20:" + fftVal20 + " fftVal30:" + fftVal30);

        }*/
        return newFreq;
    }

    double myfineTuneFreqWithFft(double freq) {
        double fftVal = fftNearFreq(freq);
        double newFreq = freq;
        double maxFftVal = fftVal;

        double testFreq = 1.5d * freq;
        double testFFtVal15 = fftNearFreq(testFreq);
        if(testFFtVal15 > maxFftVal) {
            maxFftVal = testFFtVal15;
            newFreq = testFreq;
        }
        testFreq = 2.0d * freq;
        double testFFtVal20 = fftNearFreq(testFreq);
        if(testFFtVal20 > maxFftVal) {
            maxFftVal = testFFtVal20;
            newFreq = testFreq;
        }
        testFreq = 3.0d * freq;
        double testFFtVal30 = fftNearFreq(testFreq);
        if(testFFtVal30 > maxFftVal) {
            maxFftVal = testFFtVal30;
            newFreq = testFreq;
        }
        testFreq = 2.0d/3.0d * freq;
        double testFFtVal067 = fftNearFreq(testFreq);
        if(testFFtVal067 > maxFftVal) {
            maxFftVal = testFFtVal067;
            newFreq = testFreq;
        }
        testFreq = 0.5d * freq;
        double testFFtVal05 = fftNearFreq(testFreq);
        if(testFFtVal05 > maxFftVal) {
            maxFftVal = testFFtVal05;
            newFreq = testFreq;
        }

        //if(freq != newFreq) {
            Log.d ("MYANALYZE", nPitches + ": in freq:" + freq + " newFreq:" + newFreq +
                    " fftVal:" + fftVal + " fFtVal05:" + testFFtVal05 + " fFtVal067:"
                    + testFFtVal067 + " fftVal15:" + testFFtVal15 + " fftVal20:" + testFFtVal20 +
                    " fftVal30:" + testFFtVal30);

        Log.d ("ACF", "freq:" + freq + " acf:" + acfData[(int)(samplingSize/freq)] +
                " freq:" + 0.5d * freq + " acf:" + acfData[(int)(samplingSize/(0.5d * freq))] +
                " freq:" + 2.0d/3.0d * freq + " acf:" + acfData[(int)(samplingSize/(2.0d/3.0d * freq))] +
                " freq:" + 1.5d * freq + " acf:" + acfData[(int)(samplingSize/(1.5d * freq))] +
                " freq:" + 2.0d * freq + " acf:" + acfData[(int)(samplingSize/(2.0d * freq))] +
                " freq:" + 3.0d * freq + " acf:" + acfData[(int)(samplingSize/(3.0d * freq))]
        );

       // }
        return newFreq;
    }

    // Step 2: Find the right harmonics
    // The harmonics of F are 0.5F, 1.5F, 2F, 2.5F, 3F, 3.5F
    // based on HPS http://musicweb.ucsd.edu/~trsmyth/analysis/Harmonic_Product_Spectrum.html
    // above logic FAILED : got lot more false positives for Harmonium swar,
   /* double getRightHarmonic(double freq) {
        // double[]  harmonics = new double[] {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 3.5};
        // Check upper and lower harmonics till MIN/MAX range
        double harmonicFreq = freq*0.5d;
        double acf = getAcfFromFreq(freq);
        double newAcf = getAcfFromFreq(harmonicFreq);
        double selectedFreq = freq;
        Log.d("HARMONIC", " freq:" + freq + " acf:" + acf + " harmonicFreq:"
                + harmonicFreq + " newAcf:" + newAcf + " harmonicFreq:" + freq*2 +
                " newAcf:" + getAcfFromFreq(freq*2)
        );
       // if(newAcf/acf > 0.9) {
       //     selectedFreq = harmonicFreq;
       // } else {
        //    selectedFreq = freq;
       // }
        return selectedFreq;
    }*/

    /*
    * Find the TOP 5 peak ACF in our interest range, return freq in desc order of ACF
     */
    private double[] getTop5FreqFromAcf() {
        double[] top5Acfs = new double[]{-1, -1, -1, -1, -1};
        double[] top5freqs = new double[]{-1, -1, -1, -1, -1};
        ArrayList<Double> peakFreqs = new ArrayList<Double>();
        ArrayList<Double> peakAcfs =new ArrayList<Double>();
        int scanLimit = 5; // Look up to these many tranches in acf data
        // Initialize max power with MAX freq value we care.
        // Goal is to find a start location in our interested range and avoid harmonics.
        int loc = ((int) (this.samplingSize / FREQ_MAX)) - 1; // Wavelength location for max FREQ
        double acfMax = acfData[loc];
        for (int i = loc + 1; i <= loc + scanLimit; i++) {
            if (acfData[i] > acfMax) {
                acfMax = acfData[i];
            }
        }

        double prevDelta = -1.0d;
        double psFound = 0;
        int locFound = 0;
        // Search for peaks above acfMaxFreq and between MAX & MIN frequencies ranges.
        for (loc = loc + 1; loc <= (this.samplingSize / FREQ_MIN); loc++) {
            double acfNow = acfData[loc];
            // we always look for 5 points (this is like doing max(of 5))
            for (int i = loc + 1; i <= loc + scanLimit; i++) {
                if (acfData[i] > acfNow) {
                    acfNow = acfData[i];
                }
            }

            // Alternate through peaks and valleys and store all of them.
            double delta = acfNow - acfMax;
            // delta negative means we are going downhill
            // prevDelta +ve means we were going uphill and we just peaked at loc -1
            // acfMax > 0.2 to ignore really small peaks
            if (delta < 0.0d && prevDelta >= 0.0d && acfMax > 0.2) {
                peakFreqs.add(samplingSize/ (loc - 1));
                peakAcfs.add(acfMax);
            }
            acfMax = acfNow;
            prevDelta = delta;
        }
        ArrayList<Double> sortedAcfs = (ArrayList<Double>) peakAcfs.clone();
        Collections.sort(sortedAcfs, Collections.reverseOrder());
        // Find top 5 harmonics.
        int counter=0;
        double top1Freq = peakFreqs.get(peakAcfs.indexOf(sortedAcfs.get(0)));
        // sometimes top2freq is not a harmonic of top1, in that case we get harmonics for top2freq
        double top2Freq = -1;
        if(sortedAcfs.size() > 1) {
            peakFreqs.get(peakAcfs.indexOf(sortedAcfs.get(1)));
        }
        boolean freq1IsHarmonic = false;
        boolean freq2IsHarmonic = false;
        for(int i=0; i < sortedAcfs.size(); i++) {
            int index = peakAcfs.indexOf(sortedAcfs.get(i));
            double freq = peakFreqs.get(index);
            freq1IsHarmonic = isHarmonic(top1Freq, freq);
            freq2IsHarmonic = isHarmonic(top2Freq, freq);
            if (i == 0 || freq1IsHarmonic || freq2IsHarmonic) {
                top5freqs[counter] = freq;
                top5Acfs[counter++] = sortedAcfs.get(i);

                if(counter >= 5) {
                    break;
                }
            }
        }
        return top5freqs;
    }

    // Return true is freq is a harmonic of f0
    private boolean isHarmonic(double f0, double freq) {
        double ratio = freq/f0;
        if (ratio < 1) {
            ratio = 1 / ratio;
        }

        if (ratio < 2.15 && ratio > 1.85)
            return true;
        if (ratio < 3.15 && ratio > 2.85)
            return true;
        if (ratio < 4.15 && ratio > 3.85)
            return true;
        if (ratio < 5.15 && ratio > 4.85)
            return true;

        // Log.d("HARMONICS", nPitches + ": ratio:" + ratio + " f0:" + f0 + " freq:" + freq
        // + " f0 power:" + Math.sqrt(acfData[0]));
        return false;
    }

    /*
     * Find the peak amplitude (ACF) near a given frequency.
     */
    private double[] getAcfFromFreq(double freqs[]) {
        int scanLimit = 2; // Look up to these many neighbours on both sides in acf data

        double[] acfs = new double[freqs.length];
        for (int i=0; i<freqs.length; i++) {
            if (freqs[i] < 0) {
                acfs[i] = Double.MIN_VALUE;
            } else {
                int loc = ((int) (this.samplingSize / freqs[i])); // Wavelength location for FREQ
                double maxAcf = acfData[loc];
                // Find PS around MAX Freq
                for (int j = loc - scanLimit; j <= loc + scanLimit; j++) {
                    if (acfData[j] > maxAcf) {
                        maxAcf = acfData[j];
                    }
                }
                acfs[i] = maxAcf;
            }
        }
        return acfs;
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
