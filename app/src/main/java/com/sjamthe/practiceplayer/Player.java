package com.sjamthe.practiceplayer;

import static android.media.AudioManager.AUDIO_SESSION_ID_GENERATE;
import static android.media.AudioTrack.MODE_STREAM;
import static android.media.AudioTrack.PLAYSTATE_PAUSED;
import static android.media.AudioTrack.PLAYSTATE_PLAYING;
import static android.media.AudioTrack.PLAYSTATE_STOPPED;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.Callable;

public class Player {
    public final String LOG_TAG = "Player";
    private Uri audioUri;
    private Context ctx;
    MediaPlayer mPlayer = null;
    MediaExtractor extractor;
    MediaCodec codec;
    AudioTrack audioTrack = null;
    final long kTimeoutUs = 5000;

    public void play(Context applicationContext, Uri uri, Callable<Void> audioTrackDone) {
        audioUri = uri;
        ctx = applicationContext;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (audioTrack == null || audioTrack.getPlayState() == PLAYSTATE_STOPPED) {
                        createAudioTrack();
                        extractAudio();
                    } else if (audioTrack.getPlayState() == PLAYSTATE_PAUSED) {
                        audioTrack.play();
                    }
                } catch (IllegalStateException | IOException e) {
                    Log.e(LOG_TAG," Thread stopped :" + e.toString());
                }
                try {
                    audioTrackDone.call(); //toggle works
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Can't call audiTrackDone"+ e.toString());
                }
            }
        }).start();
    }

    private void createAudioTrack() throws IOException {
        extractor = new MediaExtractor();
        extractor.setDataSource(ctx, audioUri, null);

        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        try {
            codec = MediaCodec.createDecoderByType(mime);
        } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
        }
        codec.configure(format, null, null, 0);
        codec.start();
        extractor.selectTrack(0);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        Log.i(LOG_TAG, "AudioTrack.getMinBufferSize : " + minBufferSize);
        audioTrack = new AudioTrack(audioAttributes, audioFormat, minBufferSize, MODE_STREAM,
                AUDIO_SESSION_ID_GENERATE);

        audioTrack.play();
    }

    void extractAudio() {
        // Loop through the extractor extracting sampleData
        // use codec to convert each data
        // Add data to AudioRecord
        // Play audio record data
        boolean sawInputEOS = false;
        long presentationTimeUs;
        boolean writeOnce = false;

        while (!sawInputEOS) {
            int inputBufferId = codec.dequeueInputBuffer(kTimeoutUs);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
                // fill inputBuffer with valid data
                int inputBufSize = extractor.readSampleData(inputBuffer, 0);
                if (inputBufSize < 0) {
                    Log.d(LOG_TAG, "End of input reached.");
                    // sawInputEOS = true;
                    codec.queueInputBuffer(inputBufferId, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                   // Log.d(LOG_TAG, "before queueInputBuffer inputBufferId : " + inputBufferId);
                    codec.queueInputBuffer(inputBufferId, 0, inputBufSize,
                            presentationTimeUs, 0);
                   // Log.d(LOG_TAG, "inputBufferId : " + inputBufferId);
                    if (!extractor.advance()) {
                        Log.d(LOG_TAG, "Can't advance, end of input reached.");
                        sawInputEOS = true;
                    }
                }
            }
            if(audioTrack.getPlayState() == PLAYSTATE_PLAYING)
                writeToTrack();
        }
        writeToTrack(); // last write after EOS
        audioTrack.stop();
        Log.v(LOG_TAG, "Track finished");
        // release();
    }

    private void writeToTrack() {

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int noOutputCounter = 0;
        short[] res;
        int record_data_play_pos = 0;

        int outputBufferId = codec.dequeueOutputBuffer(info, kTimeoutUs);
        if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            Log.d(LOG_TAG, "Received MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
            MediaFormat format = codec.getOutputFormat();
            audioTrack.setPlaybackRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        }
        else if (outputBufferId >= 0) {
            noOutputCounter++;
            res = getSamplesForChannel(outputBufferId, 0);
            int ret = audioTrack.write(res, info.offset, info.offset + res.length);
            codec.releaseOutputBuffer(outputBufferId, false);
            // Log.d(LOG_TAG,  "output buffer id " + outputBufferId);
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            Log.d(LOG_TAG, "saw output EOS.");
        }
    }

  // Assumes the buffer PCM encoding is 16 bit.
    short[] getSamplesForChannel(int bufferId, int channelIx) {
        ByteBuffer outputBuffer = codec.getOutputBuffer(bufferId);
        MediaFormat format = codec.getOutputFormat(bufferId);
        ShortBuffer samples = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();
        outputBuffer.clear(); //NEW
        int numChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (channelIx < 0 || channelIx >= numChannels) {
            return null;
        }
        short[] res = new short[samples.remaining() / numChannels];
        for (int i = 0; i < res.length; ++i) {
            res[i] = samples.get(i * numChannels + channelIx);
        }
        outputBuffer.clear();
        return res;
    }

    public int getPlayState() {
        if (audioTrack == null) {
            return AudioTrack.ERROR_INVALID_OPERATION;
        } else {
            return audioTrack.getPlayState();
        }
    }
/*
    public void release() {
        if(audioTrack == null || audioTrack.getState() == PLAYSTATE_STOPPED)
            return;

        codec.stop();
        codec.release();
        extractor.release();
        audioTrack.stop();
        audioTrack.release();
        Log.e(LOG_TAG, "Stopped playing");
    }
*/
    public void pauseOrResume(int state) {
        if (audioTrack != null) {
            Log.d(LOG_TAG, "state = " + state);
            if(state == AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.pause();
                Log.d(LOG_TAG, "AudioTrack paused");
            } else if(state == AudioTrack.PLAYSTATE_PAUSED) {
                audioTrack.play();
                Log.d(LOG_TAG, "AudioTrack resumed play");
            }
        }
    }
}
