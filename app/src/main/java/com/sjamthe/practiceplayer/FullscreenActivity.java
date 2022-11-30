package com.sjamthe.practiceplayer;

import static java.lang.Integer.parseInt;

import android.annotation.SuppressLint;
import android.database.Cursor;
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
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

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
    private TextView fullscreenContent;
    private ImageButton fileButton;
    private ImageButton playButton;
    private Player player;
    private long seekTo = 0;

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
                   // fullscreenContent = findViewById(R.id.fullscreen_content); /moved to onCreate
                    if(uri != null) {
                        if(selectedUri != null) {
                            // stop old play
                            player.setPlayerState(AudioTrack.PLAYSTATE_STOPPED);
                        }
                        selectedUri = uri;
                        MediaMetadataRetriever retriever =  new MediaMetadataRetriever();
                        retriever.setDataSource(getApplicationContext(), uri);

                        String keyDuration = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_DURATION);
                        String title = retriever.extractMetadata(
                                MediaMetadataRetriever.METADATA_KEY_TITLE);
                        retriever.release();

                        songSeekBar.setMax(parseInt(keyDuration));
                        songSeekBar.setProgress(0);
                        songSeekBar.setVisibility(View.VISIBLE);
                        int durationInSecs = 0;
                        if (keyDuration != null)
                            durationInSecs = Math.round(parseInt(keyDuration)/1000f);

                        if(title == null)
                            title = getFileName(uri);

                        fullscreenContent.setText(title + "\n" + durationInSecs + " (secs)");

                        // Start playing
                       // playButton.setImageResource(R.drawable.ic_baseline_pause_24);
                        player.play(getApplicationContext(), selectedUri);
                    }
                }
            });

    @SuppressLint("Range")
    String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            Cursor cursor = getContentResolver().query(uri, null, null,
                    null, null);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            } finally {
                cursor.close();
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

    // Ideally this should run in main thread and not called from another thread
    void updateSeekBar(long presentationTimeUs) {
        songSeekBar.setProgress((int) (presentationTimeUs/1000));
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
            hide();
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
        player = new Player(instance);
        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        fullscreenContent = findViewById(R.id.fullscreen_content);
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

        songSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser && seekBar == songSeekBar) {
                    seekTo = progress*1000; // convert back to Us from msec
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(seekBar == songSeekBar) {
                    player.setPlayerState(AudioTrack.PLAYSTATE_PAUSED);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(seekBar == songSeekBar) {
                    player.playFrom(seekTo);
                }
            }
        });
    }

    private void togglePlay() {
        if (selectedUri == null)
            return; // Not initialized

        int playerState = player.getPlayerState();
        if(playerState == AudioTrack.PLAYSTATE_PLAYING) {
            player.setPlayerState(AudioTrack.PLAYSTATE_PAUSED);
            // playButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
        } else if (playerState == AudioTrack.PLAYSTATE_PAUSED) {
            player.setPlayerState(AudioTrack.PLAYSTATE_PLAYING);
           //  playButton.setImageResource(R.drawable.ic_baseline_pause_24);
        }
        else {
           //  playButton.setImageResource(R.drawable.ic_baseline_pause_24);
            player.play(getApplicationContext(), selectedUri);
        }
    }

    void setPlayButton() {
        int playerState = player.getPlayerState();
        if(playerState == AudioTrack.PLAYSTATE_PLAYING) {
            playButton.setImageResource(R.drawable.ic_baseline_pause_24);
        } else  {
            playButton.setImageResource(R.drawable.ic_baseline_play_arrow_24);
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