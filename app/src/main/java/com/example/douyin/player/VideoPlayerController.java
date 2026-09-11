package com.example.douyin.player;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;

public class VideoPlayerController implements TextureView.SurfaceTextureListener,
        DefaultLifecycleObserver {

    private final Context appContext;
    private final TextureView textureView;
    @Nullable
    private ExoPlayer player;
    @Nullable
    private String videoUrl;
    private boolean playWhenReady;
    private boolean lifecyclePaused;
    private int videoWidth;
    private int videoHeight;

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onVideoSizeChanged(VideoSize videoSize) {
            videoWidth = videoSize.width;
            videoHeight = videoSize.height;
            applyVideoTransform();
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            // 保持与旧实现一致：吞掉错误，由上层决定是否换源
        }
    };

    public VideoPlayerController(TextureView textureView) {
        this.textureView = textureView;
        this.appContext = textureView.getContext().getApplicationContext();
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
        if (lifecyclePaused) {
            return;
        }
        ensurePlayer();
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    public void pause() {
        playWhenReady = false;
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public boolean isPlaying() {
        return player != null
                && player.getPlayWhenReady()
                && player.getPlaybackState() == Player.STATE_READY;
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
        videoWidth = 0;
        videoHeight = 0;
        textureView.setSurfaceTextureListener(null);
        releaseInternal();
    }

    @Override
    public void onPause(@NonNull LifecycleOwner owner) {
        lifecyclePaused = true;
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    public void onResume(@NonNull LifecycleOwner owner) {
        lifecyclePaused = false;
        if (playWhenReady && player != null) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    public void onDestroy(@NonNull LifecycleOwner owner) {
        release();
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
        if (player != null) {
            player.clearVideoSurface();
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
    }

    private void preparePlayer(@Nullable SurfaceTexture surfaceTexture) {
        if (videoUrl == null || surfaceTexture == null) {
            return;
        }
        ensurePlayer();
        if (player == null) {
            return;
        }
        player.setVideoSurface(new Surface(surfaceTexture));
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.prepare();
        player.setPlayWhenReady(playWhenReady && !lifecyclePaused);
    }

    private void ensurePlayer() {
        if (player != null) {
            return;
        }
        player = new ExoPlayer.Builder(appContext).build();
        player.addListener(playerListener);
    }

    private void releaseInternal() {
        if (player != null) {
            player.removeListener(playerListener);
            player.release();
            player = null;
        }
    }

    private void applyVideoTransform() {
        VideoTransformHelper.applyFitWidth(textureView, videoWidth, videoHeight);
    }
}
