package com.sjamthe.practiceplayer;

import static android.media.AudioManager.AUDIO_SESSION_ID_GENERATE;
import static android.media.AudioTrack.MODE_STREAM;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.Set;

public class Player {
    public final String LOG_TAG = "Player";
    private Uri audioUri;
    private Context ctx;
    MediaPlayer mPlayer = null;
    MediaMetadataRetriever retriever;
    MediaExtractor extractor;
    MediaCodec codec;
    AudioTrack audioTrack = null;

    public void setAudioFile(Uri uri, Context applicationContext) {
        audioUri = uri;
        ctx = applicationContext;
        extractor = new MediaExtractor();

        try {
            extractor.setDataSource(applicationContext, uri, null);
        } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
        }
        int tracks = extractor.getTrackCount();
        MediaFormat format = extractor.getTrackFormat(0);
        String mime = format.getString(MediaFormat.KEY_MIME);
        int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        Log.i(LOG_TAG, "===========================");
        Log.i(LOG_TAG, "url " + audioUri.getPath());
        Log.i(LOG_TAG, "mime type : " + mime);
        Log.i(LOG_TAG, "sample rate : " + sampleRate);
        Log.i(LOG_TAG, "tracks : " + tracks);
        Log.i(LOG_TAG, "===========================");

        try {
            codec = MediaCodec.createDecoderByType(mime);
        } catch (IOException e) {
            Log.e(LOG_TAG, e.toString());
        }
        codec.configure(format, null, null, 0);
        codec.start();
        extractor.selectTrack(0);

        final int minBufferSize = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        AudioFormat aformat = new AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();

        audioTrack = new AudioTrack(audioAttributes, aformat, minBufferSize, MODE_STREAM,
                                AUDIO_SESSION_ID_GENERATE);
    }

    void extractToAudioRecord() {
        // Loop through the extractor extracting sampleData
        // use codec to convert each data
        // Add data to AudioRecord
        // Play audio record data
        final long kTimeoutUs = 5000;
        boolean sawInputEOS = false;
        boolean sawOutputEOS = false;
        long presentationTimeUs;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int noOutputCounter = 0;
        short[] res;
        int record_data_play_pos = 0;

        audioTrack.play();
        while (!sawOutputEOS) {
            int inputBufferId = codec.dequeueInputBuffer(kTimeoutUs);
            if (inputBufferId >= 0) {
                ByteBuffer inputBuffer = codec.getInputBuffer(inputBufferId);
                // fill inputBuffer with valid data
                int inputBufSize = extractor.readSampleData(inputBuffer, 0);
                if (inputBufSize < 0) {
                    Log.d(LOG_TAG, "End of input steam reached.");
                    sawInputEOS = true;
                    break;
                } else {
                    presentationTimeUs = extractor.getSampleTime();
                    codec.queueInputBuffer(inputBufferId, 0, inputBufSize, presentationTimeUs,
                        sawInputEOS ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                }
                if (!sawInputEOS) {
                    extractor.advance();
                }
            }
            int outputBufferId = codec.dequeueOutputBuffer(info, kTimeoutUs);
            if (outputBufferId >= 0) {
                noOutputCounter++;
                res = getSamplesForChannel(outputBufferId, 0);
                int ret = audioTrack.write(res, 0, res.length);
                if(ret >= 0)
                    record_data_play_pos += ret;
                Log.i(LOG_TAG, "Total shorts written : " + record_data_play_pos);
                codec.releaseOutputBuffer(outputBufferId, false);
            }
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.d(LOG_TAG, "saw output EOS.");
                sawOutputEOS = true;
            }
        }
        audioTrack.stop();
        audioTrack.release();
        audioTrack = null;
        codec.stop();
        codec.release();
    }

    // Assumes the buffer PCM encoding is 16 bit.
    short[] getSamplesForChannel(int bufferId, int channelIx) {
        ByteBuffer outputBuffer = codec.getOutputBuffer(bufferId);
        MediaFormat format = codec.getOutputFormat(bufferId);
        ShortBuffer samples = outputBuffer.order(ByteOrder.nativeOrder()).asShortBuffer();
        int numChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (channelIx < 0 || channelIx >= numChannels) {
            return null;
        }
        short[] res = new short[samples.remaining() / numChannels];
        for (int i = 0; i < res.length; ++i) {
            res[i] = samples.get(i * numChannels + channelIx);
        }
        return res;
    }

    public String getMediaLocation() {
        retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(ctx, audioUri);
        } catch (RuntimeException e) {
            Log.e(LOG_TAG, "Cannot retrieve video file", e);
        }
        Log.v(LOG_TAG, "METADATA_KEY_DURATION = " + retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION)
        );
        return retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_TITLE);
    }

    public boolean play(Context ctx) {
        extractToAudioRecord();
        /*
        if (mPlayer == null) {
            mPlayer = MediaPlayer.create(ctx, audioUri);
        }
        try {
            if (mPlayer.isPlaying()) {
                release();
                return false;
            } else {
                mPlayer.start();
                return true;
            }
        } catch (IllegalStateException e) {
            Log.e(LOG_TAG, "Exception during play " + e.toString());
            return false;
        }
         */
        return true;
    }

    public boolean isPlaying() {
        if (mPlayer == null) {
            return false;
        } else {
            return mPlayer.isPlaying();
        }
    }

    private void release() {
        mPlayer.release();
        mPlayer = null;
        Log.e(LOG_TAG, "Stopped playing");
    }
}
