package com.sjamthe.practiceplayer;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.button.MaterialButton;

public class SwarPracticeActivity extends AppCompatActivity {

    private SwarView swarView;
    private TextView scaleText, avgCentErrorText, startCentErrorText, centErrorSDText, volSDPctText;
    private TextView durationText, swarText;
    private int minCentError, maxCentError, curCentError, avgCentError, centErrorSD, startCentError;
    private int volSDPct, totalVol, nPitches, totalCentError, swarCounter, swar, perfectCent;
    private int prevSwar = -1;
    float elapsedTimeInSecs;
    private MaterialButton micButton;
    private Recorder recorder;
    private LineChart lineChart;
    private FrequencyAnalyzer frequencyAnalyzer;
    protected Handler fullScreenHandler;
    // Values stored in preference
    private String rootNote;
    private String rootOctave;
    private String thaat;
    private Boolean showSoundLevel;
    private String minSoundLevel;
    private float curSoundLevel = 0;
    private float curCent;

    private boolean settingsChanged = false;
    private class Record {
        int position;
        float cent;
        int swar;
        float soundLevel;
    };
    private Record[] records;
    // boolean startSwar = false;
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

        fullScreenHandler = new Handler(Looper.myLooper());
        recorder = new Recorder(this, getApplicationContext());
        if(!recorder.checkPermissions()) {
            recorder.requestPermissions();
        }
        micButton = findViewById(R.id.swar_mic_button);
        micButton.setCheckable(true);
        micButton.setToggleCheckedStateOnClick(true);
        micButton.setOnClickListener(v -> toggleMic());

        scaleText = findViewById(R.id.scale_text);
        swarView = findViewById(R.id.swar_image_view);
        startCentErrorText = findViewById(R.id.start_cent_error);
        avgCentErrorText = findViewById(R.id.avg_cent_error);
        centErrorSDText = findViewById(R.id.cent_error_sd);
        volSDPctText = findViewById(R.id.vol_sd);
        durationText = findViewById(R.id.duration);
        swarText = findViewById(R.id.swar_text);
        lineChart = findViewById(R.id.swar_practice_linechart);
        lineChart.setVisibility(View.VISIBLE);
        readSettings();
        createChart();
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
        centErrorSD = 0;
        volSDPct = 0;
        elapsedTimeInSecs = 0;
        totalCentError = 0;
        totalVol = 0;
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
                frequencyAnalyzer.setPreferences(rootNote, rootOctave, thaat, minSoundLevel);
                micButton.setIconResource(R.drawable.ic_baseline_mic_off_24);
                micButton.setBackgroundColor(getResources().getColor(R.color.red));
                records = new Record[150];
                for (int i=0; i<records.length; i++) {
                    records[i] = new Record();
                    records[i].swar = -1;
                    records[i].cent = -1;
                }
                lineChart.getLineData().clearValues();
            } else {
                recorder.stopRecording();
                micButton.setIconResource(R.drawable.ic_baseline_mic_on_24);
                micButton.setBackgroundColor(getResources().getColor(
                        com.google.android.material.R.color.design_default_color_primary));
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
        }
        return change;
    }

    public void updateChart(float cent, float soundLevel) {
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
        curSoundLevel = soundLevel;
        curCent = cent;

        if(cent > 0) {
            swar = (FrequencyAnalyzer.centToNote(cent)
                    - Integer.parseInt(this.rootNote) + 12) % 12;
        } else {
            swar = -1;
        }
        records[pos].swar = swar;
        if(swar != prevSwar) {
            endSwar = true;
            // startSwar = false;
        } else {
            endSwar = false;
            /* See if we are starting a new swar (only 3 last swar match)
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
            }*/
        }
        if(endSwar) {
            if(swarCounter % 5 != 0)
                calcSD(); // for final results in case we skipped
            //Store data

            init(); // initialize all counters;
            swarCounter = 1;
        } else {
            swarCounter++;
            if(swarCounter % 5 == 0) // reduce load
                calcSD();
        }
        //Calculate error
        curCentError = (int) (cent - FrequencyAnalyzer.centToPerfectCent(cent));
        if (curCentError < minCentError || swarCounter == 1)
            minCentError = curCentError;
        if (curCentError > maxCentError || swarCounter == 1)
            maxCentError = curCentError;
        if (swarCounter == 1) {
            startCentError = curCentError;
            perfectCent = FrequencyAnalyzer.centToPerfectCent(cent);
        }
        totalCentError += curCentError; // Not absolute value
        avgCentError = totalCentError / swarCounter;
        totalVol += curSoundLevel;
        elapsedTimeInSecs = swarCounter * 1F / FrequencyAnalyzer.ANALYZE_SAMPLES_PER_SECOND;

        if(swarCounter >= 2) { // reduce gitter by not drawing every swar
            updateView();
        }

        Log.i("SWAR", pos + ":swarCounter:" + swarCounter + ", swar:" + swar
                + ",prevSwar:" + prevSwar + ",curCentError:" + curCentError +
                ",startCentError:" + startCentError + ",:maxCentError:" + maxCentError);
        nPitches++;
    }

    private void calcSD() {
        float totalCentErrorDiff = 0;
        float totalVolDiff = 0;
        int pos = (nPitches) % records.length;
        for (int i=0; (i<swarCounter && i < records.length); i++) {
            int loc = pos - i;
            if (loc < 0)
                loc = records.length + loc;
            int err = (int) (records[loc].cent
                    - Math.round(records[loc].cent / 100) * 100);
            totalCentErrorDiff += Math.pow(err - avgCentError, 2);
            // Log.i("SD", "err:" + err + " totalCentErrorDiff:" + totalCentErrorDiff);

            // Calculate volume error
            err = (int) (records[loc].soundLevel - totalVol/swarCounter);
            totalVolDiff += Math.pow(err, 2);
        }
        centErrorSD = (int) Math.sqrt(totalCentErrorDiff/(swarCounter-1));
        volSDPct = (int) (100*Math.sqrt(totalVolDiff/(swarCounter-1))/(totalVol/swarCounter));
    }

    // update view only if swarCounter >= 3 so we don't lose old data on screen
    private void updateView() {
        String noteString = "-";
        if(rootNote != null)
            noteString = FrequencyAnalyzer.NOTES[Integer.parseInt(rootNote)];

        float avgVol = 0;
        if(swarCounter > 0)
            avgVol = totalVol/swarCounter;

        scaleText.setText(String.format("scale: %s vol:%.0f", noteString, avgVol));

        swarView.setCentError(minCentError, maxCentError, curCentError, avgCentError, centErrorSD);
        swarView.invalidate(); // to call draw again
        startCentErrorText.setText(String.format("%d E", startCentError));
        // avgCentErrorText.setText(String.format("%d E", avgCentError));
        avgCentErrorText.setText(String.format("%d E", avgCentError));
        centErrorSDText.setText(String.format("%d SD", centErrorSD));
        volSDPctText.setText(String.format("vol %d%% SD", volSDPct));
        durationText.setText(String.format("%.1f secs", elapsedTimeInSecs));
        if(swar >= 0)
            swarText.setText(FrequencyAnalyzer.SWARAS[swar]);
        else
            swarText.setText("-");

        updateChart();
    }

    private void updateChart() {
        LineData lineData = lineChart.getData();
        if(lineData == null) {
            lineData = lineChart.getData();
        }

        ILineDataSet set = lineData.getDataSetByIndex(0);
        if (set == null) {
            set = createCentSet();
            lineData.addDataSet(set);
            //lineChart.zoom(1f, 1.5f, 0, 0, YAxis.AxisDependency.RIGHT);
        }
        set.addEntry(new Entry(set.getEntryCount(), swar));

        // move to the latest entry
        lineChart.moveViewToX(lineData.getEntryCount());
        lineData.notifyDataChanged();
        // let the chart know it's data has changed
        lineChart.notifyDataSetChanged();
        // limit the number of visible entries
        lineChart.setVisibleXRangeMaximum(60); // has to be here not in createChart
    }

    private void createChart() {
        lineChart.setTouchEnabled(true);
        lineChart.getDescription().setEnabled(false); // disable description
        // disable scaling and dragging
        lineChart.setDragEnabled(true);
        lineChart.setDragYEnabled(false);
        lineChart.setScaleEnabled(false);
        lineChart.setPinchZoom(true); // force pinch zoom along both axis
        lineChart.setDrawGridBackground(true);
        lineChart.setBackgroundColor(Color.WHITE);
        lineChart.setGridBackgroundColor(Color.WHITE);


        setNotesAxis(lineChart.getAxisRight());
        lineChart.getAxisLeft().setEnabled(false);
        // setVolumeAxis();

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setAvoidFirstLastClipping(true);
        xAxis.setDrawGridLines(false);
        xAxis.setDrawAxisLine(false);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);

        Legend l = lineChart.getLegend();
        l.setEnabled(false);

        LineData lineData = new LineData();
        // lineData.setValueTextColor(Color.WHITE);
        // add empty data
        lineChart.setData(lineData);
    }

    private void setNotesAxis(YAxis yAxis) {
        yAxis.setValueFormatter(new SwarFormatter(rootNote, thaat)); //Used to write Note characters
        yAxis.setLabelCount(14, true); //14 number to properly align gridlines with swar
        yAxis.setEnabled(true);
        yAxis.setDrawLabels(true);
        //yAxis.setGridColor(Color.BLACK);
        yAxis.setTextColor(Color.BLACK);
        yAxis.setTextSize(14);
        yAxis.setDrawGridLines(true);
        yAxis.setDrawZeroLine(true);
        yAxis.setGridLineWidth(0.5f);
        yAxis.setGridColor(Color.LTGRAY);
        yAxis.setAxisMaximum(12);
        yAxis.setAxisMinimum(-1);
        lineChart.notifyDataSetChanged();
    }

    private LineDataSet createCentSet() {
        LineDataSet set = new LineDataSet(null, "Frequency Data");
        set.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        //Set set.setColor to same as lineChart.setBackgroundColor to hide jump lines.
        set.setColor(getResources().getColor(R.color.light_blue_600));
        //set.setFillColor(Color.WHITE);
        set.setCircleColor(Color.BLACK);
        set.setLineWidth(1.2f); // .2f is almost invisible
        //set.setFillAlpha(65); // doesn't make any change ?
        // set.setHighLightColor(Color.rgb(244, 117, 117)); // not sure what this is for
        set.setValueTextColor(Color.BLACK);
        // set.setValueTextSize(9f);
        set.setDrawValues(false);
        set.setCircleRadius(1f);
        set.setDrawCircles(false); // added to not draw points
        return set;
    }
}