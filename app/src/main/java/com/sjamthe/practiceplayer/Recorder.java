package com.sjamthe.practiceplayer;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class Recorder {
    private int RECORD_BUFFER_SIZE;
    Context applicationContext;
    FullscreenActivity fullscreenActivity;
    // constant for storing audio permission
    public static final int REQUEST_AUDIO_PERMISSION_CODE = 1;
    private Notification notification = new Notification();
    private static final int SAMPLE_RATE = 44100;
    private static final int AUDIO_BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    private static final int FRAME_BUFFER_SIZE = AUDIO_BUFFER_SIZE / 2;
    private short[] data = new short[FRAME_BUFFER_SIZE];
    private AudioRecord record = null;
    public FrequencyAnalyzer frequencyAnalyzer;

    public Recorder(FullscreenActivity instance, Context applicationContext) {
        this.fullscreenActivity = instance;
        this.applicationContext = applicationContext;
        this.frequencyAnalyzer = new FrequencyAnalyzer(SAMPLE_RATE);
        this.frequencyAnalyzer.fullscreenActivity = this.fullscreenActivity;
    }

    public boolean checkPermissions() {
        // this method is used to check permission
        int result = ContextCompat.checkSelfPermission(applicationContext, RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    public void requestPermissions() {
        // this method is used to request the permission for audio recording.
        ActivityCompat.requestPermissions(this.fullscreenActivity, new String[]{
                RECORD_AUDIO}, REQUEST_AUDIO_PERMISSION_CODE);
    }

    @SuppressLint("MissingPermission")
    public void startRecording() {
        this.RECORD_BUFFER_SIZE = FRAME_BUFFER_SIZE*10000;
        if (!checkPermissions()) {
            requestPermissions();
        }
        this.record = new AudioRecord(MediaRecorder.AudioSource.VOICE_PERFORMANCE,
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                AUDIO_BUFFER_SIZE);
        this.record.setRecordPositionUpdateListener(this.notification);
        this.record.setPositionNotificationPeriod(FRAME_BUFFER_SIZE);
        this.record.startRecording();
        this.record.read(this.data, 0, FRAME_BUFFER_SIZE);
    }

    public void stopRecording() {
        if(this.record != null) {
            this.record.stop();
            this.record.release();
            this.record = null;
        }
    }

    public class Notification implements AudioRecord.OnRecordPositionUpdateListener {
        @Override // android.media.AudioRecord.OnRecordPositionUpdateListener
        public void onMarkerReached(AudioRecord audioRecord) {
        }

        public Notification() {
        }

        @Override // android.media.AudioRecord.OnRecordPositionUpdateListener
        public void onPeriodicNotification(AudioRecord audioRecord) {
            int read = audioRecord.read(Recorder.this.data, 0,
                    Recorder.FRAME_BUFFER_SIZE);
            if(read > 0)
                Recorder.this.frequencyAnalyzer.addData(Recorder.this.data);
        }
    }
}


