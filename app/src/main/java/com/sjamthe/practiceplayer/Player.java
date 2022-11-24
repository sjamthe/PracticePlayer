package com.sjamthe.practiceplayer;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

public class Player {
    private Uri audioUri;
    private Context ctx;
    MediaPlayer mPlayer = null;
    MediaMetadataRetriever retriever;

    public void setAudioFile(Uri uri, Context applicationContext) {
        audioUri = uri;
        ctx = applicationContext;
        Log.v("Player", audioUri.getPath());
    }

    public String getMediaLocation() {
        retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(ctx, audioUri);
        } catch (RuntimeException e) {
            Log.e("TAG", "Cannot retrieve video file", e);
        }
        return retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_ALBUM);
    };

    public boolean play(Context ctx) {
        if (mPlayer == null) {
            mPlayer = MediaPlayer.create(ctx, audioUri);
        }
        try {
            if(mPlayer.isPlaying()) {
                release();
                return false;
            } else {
                mPlayer.start();
                return true;
            }
        } catch (IllegalStateException e) {
            Log.e("TAG", "Exception during play " + e.toString());
            return false;
        }
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
        Log.e( "TAG", "Stopped playing");
    }
}
