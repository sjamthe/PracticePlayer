package com.sjamthe.practiceplayer;

import static java.lang.Integer.parseInt;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.graphics.Color;
import android.media.AudioTrack;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;
import com.google.android.material.button.MaterialButton;
import com.sjamthe.practiceplayer.databinding.ActivityFullscreenBinding;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {

    // Initializing all variables..
    private static FullscreenActivity instance;
    public Handler fullScreenHandler;
    private SeekBar songSeekBar;
    private LineChart lineChart;
    private TextView fullscreenContent;
    private TextView songSeekBarPosition;
    private TextView markerStartPosition;
    private TextView markerStopPosition;
    private TextView lastCents;
    private MaterialButton micButton;
    private MaterialButton fileButton;
    private MaterialButton playButton;
    private MaterialButton markerButton;
    private MaterialButton replayButton;
    private Player player;
    private Recorder recorder;
    private long seekToInUs = 0;
    int durationInSecs;
    long markerStartInUs = 0;
    long markerStopInUs = 0;

    boolean replayState;
    boolean markerButtonAtStart; // Tracks if marker button is set to start or end

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler(Looper.myLooper());
    private View mContentView;

    Uri selectedUri = null;

    ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    if (uri != null) {
                        if (selectedUri != null) {
                            // stop old play
                            player.setPlayerState(AudioTrack.PLAYSTATE_STOPPED);
                            resetMarkerPositions();
                        }
                        selectedUri = uri;

                        // Get title and duration to show on UI and reset seekbar.
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        retriever.setDataSource(getApplicationContext(), uri);

                        String keyDuration = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION);
                        String title = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_TITLE);
                        retriever.release();

                        songSeekBar.setMax(parseInt(keyDuration));
                        songSeekBar.setProgress(0);
                        songSeekBar.setVisibility(View.VISIBLE);
                        songSeekBarPosition.setVisibility(View.VISIBLE);
                        durationInSecs = 0;
                        if (keyDuration != null)
                            durationInSecs = Math.round(parseInt(keyDuration) / 1000f);

                        if (title == null)
                            title = getFileName(uri);

                        fullscreenContent.setText(title + "\n" + durationInSecs + " (secs)");
                        fullscreenContent.setVisibility(View.GONE); // FOR TESTING Chart

                        // Start playing
                        player.play(getApplicationContext(), selectedUri);
                    }
                }
            });

    @SuppressLint("Range")
    String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null,
                    null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    String secsToTime(int secs) {
        String text = "";
        if(secs < 60) {
            text += secs;
        } else {
            int hours = secs / 3600;
            if(hours > 0) {
                text = String.format("%d:",hours);
            }
            secs = secs - hours*3600;
            int mins = secs/60;
            secs = secs - mins*60;
            text += String.format("%02d:%02d",mins, secs);
        }
        return text;
    }

    // This now runs in main thread with the help of fullScreenHandler
    void updateSeekBar(long presentationTimeUs) {
        songSeekBar.setProgress((int) (presentationTimeUs / 1000f));

        int positionInSecs = Math.round(presentationTimeUs/1000000f);
        songSeekBarPosition.setText(secsToTime(positionInSecs) + "/" + secsToTime(durationInSecs));
    }

    void createChart() {
        lineChart.setTouchEnabled(true);
        lineChart.getDescription().setEnabled(false); // disable description
        // enable scaling and dragging
        lineChart.setDragEnabled(true);
        lineChart.setDragYEnabled(true); // don't seem to work.
        lineChart.setScaleEnabled(true);
        lineChart.setPinchZoom(true); // force pinch zoom along both axis
        lineChart.setDrawGridBackground(false);
        // setting this made lastCent label disappear?
        // lineChart.setBackgroundColor(getResources().getColor(R.color.light_blue_600));

        lineChart.getAxisRight().setEnabled(true); // disable right axis, we only need left
        lineChart.getAxisLeft().setEnabled(false);
        lineChart.setVisibleYRange((float) FrequencyAnalyzer.FREQ_MIN,
                (float) FrequencyAnalyzer.FREQ_MAX, YAxis.AxisDependency.RIGHT);
        YAxis yAxis = lineChart.getAxisRight();
        yAxis.setValueFormatter(new FrequencyFormatter()); //worked

        yAxis.setAxisMaximum(FrequencyAnalyzer.freqToCent(FrequencyAnalyzer.FREQ_C6));
        yAxis.setAxisMinimum(FrequencyAnalyzer.freqToCent(FrequencyAnalyzer.FREQ_C1));
        //yAxis.setLabelCount(4, true); // Show only C labels
        //yAxis.setGridColor(Color.LTGRAY);
        yAxis.setDrawLabels(false);

        yAxis.setTextColor(Color.WHITE);
        yAxis.setTextSize(14);
        yAxis.setDrawGridLines(false);
        //yAxis.setGridLineWidth(1.5f);
        drawNotesLines(yAxis);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setAvoidFirstLastClipping(true);

        Legend l = lineChart.getLegend();
        l.setEnabled(false);

        LineData lineData = new LineData();
        // lineData.setValueTextColor(Color.WHITE);
        // add empty data
        lineChart.setData(lineData);
    }

    void drawNotesLines(YAxis yAxis) {
        float min = yAxis.getAxisMinimum();
        float max = yAxis.getAxisMaximum();
        float cent = min;
        int[] thaat = new int[]{200, 200, 200, 100, 200, 200, 100}; // Kalyan
        do {
            for (int gap: thaat) {
                cent += gap;
                int octave = FrequencyAnalyzer.centToOctave(cent);
                int note = FrequencyAnalyzer.centToNote(cent);
                String noteString = FrequencyAnalyzer.NOTES[note];
                String label = noteString + Integer.toString(octave);
                LimitLine ll = new LimitLine(cent, label); // set where the line should be drawn
                ll.setLineColor(Color.GRAY);
                ll.setTextSize(12);
                ll.setLabelPosition(LimitLine.LimitLabelPosition.RIGHT_BOTTOM);
                ll.setTextColor(Color.WHITE);
                if(note == 0) { // C note - but can be key if preset
                    ll.setLineWidth(1.5f);
                    ll.disableDashedLine();
                } else {
                    ll.setLineWidth(1f);
                    ll.enableDashedLine(10f, 10f, 0f);
                }
                yAxis.addLimitLine(ll);
            }
        } while (cent < max);

    }

    private LineDataSet createFrequencySet() {
        LineDataSet set = new LineDataSet(null, "Frequency Data");
        set.setAxisDependency(YAxis.AxisDependency.RIGHT);
        //Set set.setColor to same as lineChart.setBackgroundColor to hide jump lines.
        set.setColor(getResources().getColor(R.color.light_blue_600));
        // set.setFillColor(R.color.light_blue_A200);
        set.setCircleColor(Color.WHITE);
        set.setLineWidth(.2f); // .2f is almost invisible
        set.setCircleRadius(1.3f);
        set.setFillAlpha(65); // doesn't make any change ?
        // set.setHighLightColor(Color.rgb(244, 117, 117)); // not sure what this is for
        set.setValueTextColor(Color.WHITE);
        // set.setValueTextSize(9f);
        set.setDrawValues(false);
        //set.setDrawCircles(false); // added to not draw points
        return set;
    }

    void updateChart(float cent, float songCent) {
        LineData lineData = lineChart.getData();
        if(lineData == null) {
            createChart();
            lineData = lineChart.getData();
        }
        ILineDataSet set = lineData.getDataSetByIndex(0);
        if (set == null) {
            set = createFrequencySet();
            lineData.addDataSet(set);
            lineChart.zoom(1f, 2f, 0, 0, YAxis.AxisDependency.RIGHT);
        }
        set.addEntry(new Entry(set.getEntryCount(), cent));

        // move to the latest entry
        //lineChart.moveViewToX(lineData.getEntryCount()); // no lines if disabled
        lineChart.moveViewTo(lineData.getEntryCount(),
                FrequencyAnalyzer.freqToCent(FrequencyAnalyzer.FREQ_C3),
                YAxis.AxisDependency.RIGHT);

        // it may not ber necessary to refresh at every point
        lineData.notifyDataChanged();
        // let the chart know it's data has changed
        lineChart.notifyDataSetChanged();
        // limit the number of visible entries
        lineChart.setVisibleXRangeMaximum(120); // has to be here not in createChart

        lastCents.setVisibility(View.VISIBLE);

        if(cent > 0 && songCent >= 0) {
            lastCents.setText(String.format("%s%s key:%s%s",
                    FrequencyAnalyzer.NOTES[FrequencyAnalyzer.centToNote(cent)],
                    FrequencyAnalyzer.centToOctave(cent),
                    FrequencyAnalyzer.NOTES[FrequencyAnalyzer.centToNote(songCent)],
                    FrequencyAnalyzer.centToOctave(songCent)));
        } else if (songCent >= 0){
            lastCents.setText(String.format("-- key:%s%s",
                    FrequencyAnalyzer.NOTES[FrequencyAnalyzer.centToNote(songCent)],
                    FrequencyAnalyzer.centToOctave(songCent)));
        } else if(cent > 0) {
            lastCents.setText(String.format("%s%s key:--",
                    FrequencyAnalyzer.NOTES[FrequencyAnalyzer.centToNote(cent)],
                    FrequencyAnalyzer.centToOctave(cent)));
        }
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar
            if (Build.VERSION.SDK_INT >= 30) {
                mContentView.getWindowInsetsController().hide(
                        WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            } else {
                // Note that some of these constants are new as of API 16 (Jelly Bean)
                // and API 19 (KitKat). It is safe to use them, as they are inlined
                // at compile-time and do nothing on earlier devices.
                mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
            }
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            // hide(); // This is startup state of buttons. we want them to show
            show();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (AUTO_HIDE) {
                        delayedHide(AUTO_HIDE_DELAY_MILLIS);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    view.performClick();
                    break;
                default:
                    break;
            }
            return false;
        }
    };
    private ActivityFullscreenBinding binding;

    public static FullscreenActivity getInstance() {
        return instance;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        fullScreenHandler = new Handler(Looper.myLooper());
        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fullscreenContent = findViewById(R.id.fullscreen_content);
        songSeekBarPosition = findViewById(R.id.seek_position);
        songSeekBarPosition.setVisibility(View.GONE);

        lineChart = findViewById(R.id.activity_main_linechart);
        createChart();
        // lineChart.setOnChartValueSelectedListener(this); // can be used to set listener
        // to listen to events of data selection on chart
        player = new Player(instance);
        recorder = new Recorder(instance, getApplicationContext());
        if(!recorder.checkPermissions()) {
            recorder.requestPermissions();
        }

        markerStartPosition = findViewById(R.id.start_position);
        markerStartPosition.setVisibility(View.GONE);
        markerStopPosition = findViewById(R.id.stop_position);
        markerStopPosition.setVisibility(View.GONE);

        lastCents = findViewById(R.id.last_cents);
        lastCents.setVisibility(View.GONE);

        songSeekBar = findViewById(R.id.seek_bar);
        songSeekBar.setVisibility(View.GONE);


        mVisible = true;
        mControlsView = binding.fullscreenContentControls;

        mContentView = binding.fullscreenContent;
        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        // binding.dummyButton.setOnTouchListener(mDelayHideTouchListener);
        // binding.fileButton.setOnTouchListener(mDelayHideTouchListener);
        // binding.playButton.setOnTouchListener(mDelayHideTouchListener);

        // initialize all variables with their layout items.
        micButton = findViewById(R.id.mic_button);
        micButton.setCheckable(true);
        micButton.setToggleCheckedStateOnClick(true);
        micButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMic();
            }
        });

        fileButton = findViewById(R.id.file_button);
        fileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //Set opening file dialog to select audio file
                // Pass in the mime type you'd like to allow the user to select
                // as the input
                mGetContent.launch("audio/*");
            }
        });

        playButton = findViewById(R.id.play_button);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });

        markerButton = findViewById(R.id.marker_button);
        markerButtonAtStart = true; // Default state
        markerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setMarkerPosition();
            }
        });

        replayButton = findViewById(R.id.replay_button);
        replayState = false; // default state of the button
        replayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                replayState = !replayState;
                if (replayState) {
                    replayButton.setIconTintResource(com.google.android.material.R.color.
                            design_default_color_primary);
                    replayButton.setBackgroundColor(getResources().getColor(
                            com.google.android.material.R.color.design_default_color_on_primary));
                } else {
                    replayButton.setIconTintResource(com.google.android.material.R.color.
                            design_default_color_on_primary);
                    replayButton.setBackgroundColor(getResources().getColor(
                            com.google.android.material.R.color.design_default_color_primary));
                }
            }
        });

        songSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progressInMs, boolean fromUser) {
                if (fromUser && seekBar == songSeekBar) {
                    int positionInSecs = Math.round(progressInMs/1000f);
                    // display seek position in sec from msec.
                    songSeekBarPosition.setText(secsToTime(positionInSecs) + "/"
                            + secsToTime(durationInSecs));
                    seekToInUs = progressInMs * 1000; // convert back to Us from msec
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (seekBar == songSeekBar) {
                    if(player.getPlayerState() == AudioTrack.PLAYSTATE_PLAYING)
                        player.setPlayerState(AudioTrack.PLAYSTATE_PAUSED);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (seekBar == songSeekBar) {
                    // If markers are set use this signal to reset markers
                    // The marker closet to the current play position is set.
                    if((markerStartInUs > 0 & markerStopInUs > 0) // both markers are set
                            & Math.abs(seekToInUs-markerStartInUs) // and seek is closer to start
                                < Math.abs(seekToInUs-markerStopInUs)) {
                        setMarkerStartPosition(seekToInUs);
                    } else if((markerStartInUs > 0 & markerStopInUs > 0) // both markers are set
                            & Math.abs(seekToInUs-markerStartInUs) // and seek is closer to end
                            > Math.abs(seekToInUs-markerStopInUs)) {
                        setMarkerStopPosition(seekToInUs);
                    } else {
                        player.playFrom(seekToInUs);
                        player.setPlayerState(AudioTrack.PLAYSTATE_PLAYING);
                    }
                }
            }
        });
    }

    private void resetMarkerPositions() {
        player.resetMarkers();
        markerButtonAtStart = true;
        markerButton.setIconResource(R.drawable.ic_baseline_begin_marker_24);
        markerStartInUs = 0;
        markerStartPosition.setVisibility(View.GONE);
        markerStopInUs = 0;
        markerStopPosition.setVisibility(View.GONE);

        markerButton.setIconTintResource(com.google.android.material.R.color.
                design_default_color_on_primary);
        markerButton.setBackgroundColor(getResources().getColor(
                com.google.android.material.R.color.design_default_color_primary));
    }

    private void setMarkerStartPosition(long markerPositionInUs) {
        markerStartInUs = markerPositionInUs;
        markerStartPosition.setText("|< " + secsToTime(Math.round(markerStartInUs/1000000f)));
        markerStartPosition.setVisibility(View.VISIBLE);
        markerButtonAtStart = false;
        markerButton.setIconResource(R.drawable.ic_baseline_end_marker_24);
        player.setMarkerStart(markerStartInUs);
    }

    private void setMarkerStopPosition(long markerPositionInUs){
        markerStopInUs = markerPositionInUs;
        markerStopPosition.setText(secsToTime(Math.round(markerStopInUs/1000000f)) + " >|");
        markerStopPosition.setVisibility(View.VISIBLE);
        markerButtonAtStart = true;
        markerButton.setIconResource(R.drawable.ic_baseline_begin_marker_24);

        markerButton.setIconTintResource(com.google.android.material.R.color.
                design_default_color_primary);
        markerButton.setBackgroundColor(getResources().getColor(
                com.google.android.material.R.color.design_default_color_on_primary));

        player.setMarkerStop(markerStopInUs);
    }

    private void setMarkerPosition() {
        if(markerButtonAtStart & markerStartInUs == 0) {
            setMarkerStartPosition(player.presentationTimeUs);
        } else if (!markerButtonAtStart & markerStopInUs == 0) {
            setMarkerStopPosition(player.presentationTimeUs);
        } else {
            resetMarkerPositions();
        }
    }

    private void toggleMic() {
        if(micButton.isToggleCheckedStateOnClick()) {
            if(micButton.isChecked()) {
                fullscreenContent.setVisibility(View.GONE);
                recorder.startRecording();
                micButton.setIconResource(R.drawable.ic_baseline_mic_off_24);
            } else {
                fullscreenContent.setVisibility(View.VISIBLE);
                recorder.stopRecording();
                micButton.setIconResource(R.drawable.ic_baseline_mic_on_24);
            }
        }
    }

    private void togglePlay() {
        if (selectedUri == null)
            return; // Not initialized

        int playerState = player.getPlayerState();
        if (playerState == AudioTrack.PLAYSTATE_PLAYING) {
            player.setPlayerState(AudioTrack.PLAYSTATE_PAUSED);
        } else if (playerState == AudioTrack.PLAYSTATE_PAUSED) {
            player.setPlayerState(AudioTrack.PLAYSTATE_PLAYING);
        } else {
            player.play(getApplicationContext(), selectedUri);
        }
    }

    void setPlayButton() {
        int playerState = player.getPlayerState();
        if (playerState == AudioTrack.PLAYSTATE_PLAYING) {
            playButton.setIconResource(R.drawable.ic_baseline_pause_24);
        } else {
            playButton.setIconResource(R.drawable.ic_baseline_play_arrow_24);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private void show() {
        // Show the system bar
        if (Build.VERSION.SDK_INT >= 30) {
            mContentView.getWindowInsetsController().show(
                    WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
        } else {
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}