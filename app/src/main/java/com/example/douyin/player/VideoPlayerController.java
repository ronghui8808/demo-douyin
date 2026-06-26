package com.example.douyin.player;

import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;

import java.io.IOException;

public class VideoPlayerController implements TextureView.SurfaceTextureListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnVideoSizeChangedListener {

    private final TextureView textureView;
    private MediaPlayer mediaPlayer;
    private String videoUrl;
    private boolean playWhenReady;
    private boolean isPrepared;
    private int videoWidth;
    private int videoHeight;

    public VideoPlayerController(TextureView textureView) {
        this.textureView = textureView;
        textureView.setSurfaceTextureListener(this);
        textureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyVideoTransform();
            }
        });
    }

    public void setVideoUrl(String url) {
        this.videoUrl = url;
        if (textureView.isAvailable()) {
            preparePlayer(textureView.getSurfaceTexture());
        }
    }

    public void play() {
        playWhenReady = true;
        if (isPrepared && mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }
    }

    public void pause() {
        playWhenReady = false;
        if (isPrepared && mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    public boolean isPlaying() {
        return isPrepared && mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public void togglePlayPause() {
        if (isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public void release() {
        playWhenReady = false;
        isPrepared = false;
        videoWidth = 0;
        videoHeight = 0;
        textureView.setSurfaceTextureListener(null);
        releaseInternal();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (videoUrl != null) {
            preparePlayer(surface);
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        applyVideoTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        releaseInternal();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    private void preparePlayer(SurfaceTexture surfaceTexture) {
        if (videoUrl == null) {
            return;
        }
        releaseInternal();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setSurface(new Surface(surfaceTexture));
        mediaPlayer.setLooping(true);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnVideoSizeChangedListener(this);
        try {
            mediaPlayer.setDataSource(textureView.getContext(), Uri.parse(videoUrl));
            mediaPlayer.prepareAsync();
        } catch (IOException e) {
            releaseInternal();
        }
    }

    private void releaseInternal() {
        isPrepared = false;
        if (mediaPlayer != null) {
            mediaPlayer.setOnPreparedListener(null);
            mediaPlayer.setOnErrorListener(null);
            mediaPlayer.setOnVideoSizeChangedListener(null);
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        isPrepared = true;
        videoWidth = mp.getVideoWidth();
        videoHeight = mp.getVideoHeight();
        applyVideoTransform();
        if (playWhenReady) {
            mp.start();
        }
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        videoWidth = width;
        videoHeight = height;
        applyVideoTransform();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        isPrepared = false;
        return true;
    }

    private void applyVideoTransform() {
        VideoTransformHelper.applyFitWidth(textureView, videoWidth, videoHeight);
    }
}
