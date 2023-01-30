package com.sjamthe.practiceplayer;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;

import java.util.concurrent.locks.ReentrantLock;

public class SwarPracticeActivity extends AppCompatActivity {

    private SwarView swarView;
    private TextView scaleText, avgCentErrorText, startCentErrorText, centErrorSDText, volSDPctText;
    private TextView durationText, swarText;
    private int minCentError, maxCentError, curCentError, avgCentError, centSD, startCentError;
    private int volSDPct, nPitches, totalCentError, swarCounter, swar;
    private int prevSwar = -1;
    float elapsedTimeInSecs;
    private MaterialButton micButton;
    private Recorder recorder;
    private FrequencyAnalyzer frequencyAnalyzer;
    protected Handler fullScreenHandler;
    // Values stored in preference
    private String rootNote;
    private String rootOctave;
    private String thaat;
    private Boolean showSoundLevel;
    private String minSoundLevel;
    private boolean settingsChanged = false;
    private class Record {
        int position;
        float cent;
        int swar;
        float soundLevel;
    };
    private Record[] records;
    boolean startSwar = false;
    boolean endSwar = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.swar_practice);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show back button
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        scaleText = findViewById(R.id.scale_text);
        swarView = findViewById(R.id.swar_image_view);
        startCentErrorText = findViewById(R.id.start_cent_error);
        avgCentErrorText = findViewById(R.id.avg_cent_error);
        centErrorSDText = findViewById(R.id.cent_error_sd);
        volSDPctText = findViewById(R.id.vol_sd);
        durationText = findViewById(R.id.duration);
        swarText = findViewById(R.id.swar_text);

        fullScreenHandler = new Handler(Looper.myLooper());
        recorder = new Recorder(this, getApplicationContext());
        if(!recorder.checkPermissions()) {
            recorder.requestPermissions();
        }
        micButton = findViewById(R.id.swar_mic_button);
        micButton.setCheckable(true);
        micButton.setToggleCheckedStateOnClick(true);
        micButton.setOnClickListener(v -> toggleMic());
    }

    // Enable back button on the action bar
    @Override
    public boolean onSupportNavigateUp() {
        recorder.stopRecording();
        micButton.setIconResource(R.drawable.ic_baseline_mic_on_24);
        finish();
        return true;
    }

    private void init() {
        curCentError = 0;
        minCentError = 0;
        maxCentError = 0;
        avgCentError = 0;
        startCentError = 0;
        centSD = 0;
        volSDPct = 0;
        elapsedTimeInSecs = 0;
        totalCentError = 0;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        init();
        updateView();
    }

    void toggleMic() {
        if(micButton.isToggleCheckedStateOnClick()) {
            if(micButton.isChecked()) {
                // store frequencyAnalyzer so we can set rootKey etc
                nPitches = 0; // start total counter;
                recorder.startRecording();
                frequencyAnalyzer = recorder.frequencyAnalyzer;
                frequencyAnalyzer.swarPracticeActivity = this;
                readSettings();
                micButton.setIconResource(R.drawable.ic_baseline_mic_off_24);
                records = new Record[10];
                for (int i=0; i<records.length; i++) {
                    records[i] = new Record();
                    records[i].swar = -1;
                    records[i].cent = -1;
                }
            } else {
                recorder.stopRecording();
                micButton.setIconResource(R.drawable.ic_baseline_mic_on_24);
                init();
                updateView();
            }
        }
    }

    // Reads Note settings from preferences, returns true if settings have changed
    boolean readSettings() {
        boolean change = false;
        final SharedPreferences mSharedPreference=
                PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String newRootNote = mSharedPreference.getString("root_note", "0");
        String newRootOctave = mSharedPreference.getString("root_octave", "3");
        String newThaat = mSharedPreference.getString("thaat", "bilawal");
        String minSoundLevel = mSharedPreference.getString("min_sound_level",
                "0");
        Boolean showSoundLevel = mSharedPreference.getBoolean("show_sound_level",
                false);

        if(!newThaat.equals(thaat) || !newRootOctave.equals(rootOctave)
                || !newRootNote.equals(rootNote) || showSoundLevel != this.showSoundLevel
                || minSoundLevel != this.minSoundLevel) {
            change = true;
            rootNote = newRootNote;
            rootOctave = newRootOctave;
            thaat = newThaat;
            this.minSoundLevel = minSoundLevel;
            this.showSoundLevel = showSoundLevel;
            String noteString = FrequencyAnalyzer.NOTES[Integer.parseInt(rootNote)];
            scaleText.setText(String.format("scale: %s", noteString));
        }
        frequencyAnalyzer.setPreferences(rootNote, rootOctave, thaat, minSoundLevel);
        return change;
    }

    public void updateChart(float cent, float songCent, float soundLevel, float avgSoundLevel) {
        // If last 3 swaras are same we have a new swar (and some check on volume)
        // if swar is different from prev swar look for end
        if(nPitches >= 1) {
            prevSwar = records[(nPitches - 1) % records.length].swar;
        }
        if (prevSwar == -1 && cent == -1) {
            return; // We have not got any good sound yet.
        }
        int pos = (nPitches) % records.length;
        records[pos].position = nPitches;
        records[pos].cent = cent;
        records[pos].soundLevel = soundLevel;

        if(cent > 0) {
            swar = (FrequencyAnalyzer.centToNote(cent)
                    - Integer.parseInt(this.rootNote) + 12) % 12;
        } else {
            swar = -1;
        }
        records[pos].swar = swar;
        if(swar != prevSwar) {
            endSwar = true;
            startSwar = false;
        } else {
            endSwar = false;
            swarCounter++;
            // See if we are starting a new swar (only 3 last swar match)
            int prevSwar4 = -1, prevSwar3 = -1, prevSwar2 = -1;
            if(nPitches >= 2)
                prevSwar2 = records[(nPitches - 2) % records.length].swar;
            if(nPitches >= 3)
                prevSwar3 = records[(nPitches - 3) % records.length].swar;
            if(nPitches >= 4)
                prevSwar4 = records[(nPitches - 4) % records.length].swar;

            if(swar == prevSwar && swar == prevSwar2 && swar == prevSwar3) {
                if(swar != prevSwar4)
                    startSwar = true; // we found a new start
                else
                    startSwar = false;
            }
        }
        if(endSwar) {
            init(); // initialize all counters;
            swarCounter = 1;
        }
        //Calculate error
        curCentError = (int) (cent - Math.round(cent / 100) * 100);
        if (curCentError < minCentError || swarCounter == 1)
            minCentError = curCentError;
        if (curCentError > maxCentError || swarCounter == 1)
            maxCentError = curCentError;
        totalCentError += curCentError; // Not absolute value
        avgCentError = totalCentError / swarCounter;

        elapsedTimeInSecs = swarCounter * 1F / FrequencyAnalyzer.ANALYZE_SAMPLES_PER_SECOND;
        if(swarCounter >= 2) {
            updateView();
        }

        Log.i("SWAR", pos + ":swarCounter:" + swarCounter + ", swar:" + swar
                + ",prevSwar:" + prevSwar + ",curCentError:" + curCentError +
                ",minCentError:" + minCentError + ",:maxCentError:" + maxCentError);
        nPitches++;
    }

    // update view only if swarCounter >= 3 so we don't lose old data on screen
    private void updateView() {
        swarView.setCentError(minCentError, maxCentError, curCentError);
        swarView.invalidate(); // to call draw again
        startCentErrorText.setText(String.format("%d E", startCentError));
        avgCentErrorText.setText(String.format("%d E", avgCentError));
        centErrorSDText.setText(String.format("%d SD", centSD));
        volSDPctText.setText(String.format("vol %d%% SD", volSDPct));
        durationText.setText(String.format("%.1f secs", elapsedTimeInSecs));
        if(swar >= 0)
            swarText.setText(FrequencyAnalyzer.SWARAS[swar]);
        else
            swarText.setText("-");
    }
}