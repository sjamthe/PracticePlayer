package com.sjamthe.practiceplayer;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.TextView;

public class SwarPracticeActivity extends AppCompatActivity {

    private SwarView swarView;
    private TextView avgCentErrorText, startCentErrorText, centErrorSDText, volSDPctText;
    private TextView durationText, swarText;
    private int minCentError, maxCentError, curCentError, avgCentError, centSD, startCentError;
    private int volSDPct;
    int elapsedTimeInSecs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.swar_practice);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Show back button
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        swarView = findViewById(R.id.swar_image_view);
        startCentErrorText = findViewById(R.id.start_cent_error);
        avgCentErrorText = findViewById(R.id.avg_cent_error);
        centErrorSDText = findViewById(R.id.cent_error_sd);
        volSDPctText = findViewById(R.id.vol_sd);
        durationText = findViewById(R.id.duration);
        swarText = findViewById(R.id.swar_text);

    }

    // Enable back button on the action bar
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void updateView() {
        curCentError = 5;
        minCentError = -20;
        maxCentError = 30;
        avgCentError = 4;
        startCentError = -10;
        centSD = 25;
        volSDPct = 10;
        elapsedTimeInSecs = 12;

        swarView.setCentError(minCentError, maxCentError, curCentError);

        startCentErrorText.setText(String.format("%d E", startCentError));
        avgCentErrorText.setText(String.format("%d E", avgCentError));
        centErrorSDText.setText(String.format("%d SD", centSD));
        volSDPctText.setText(String.format("vol %d%% SD", volSDPct));
        durationText.setText(String.format("%d secs", elapsedTimeInSecs));
        swarText.setText("S");
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        updateView();
    }
}