package com.example.douyin.player;

import android.content.Context;
import android.view.TextureView;

import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.example.douyin.cache.ExoMediaCache;

public class VideoPlayerController {

    private static final int MIN_BUFFER_MS = 2_000;
    private static final int MAX_BUFFER_MS = 10_000;
    private static final int BUFFER_FOR_PLAYBACK_MS = 500;
    private static final int BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 1_000;

    private final TextureView textureView;
    private final Context appContext;
    @Nullable private ExoPlayer player;
    @Nullable private String videoUrl;
    private boolean playWhenReady;
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
        public void onPlaybackStateChanged(int playbackState) {
            if (playbackState == Player.STATE_READY && playWhenReady && player != null) {
                player.play();
            }
        }

        @Override
        public void onPlayerError(PlaybackException error) {
            // 保持与旧 MediaPlayer.onError 类似：吞掉错误，避免崩溃
        }
    };

    public VideoPlayerController(TextureView textureView) {
        this.textureView = textureView;
        this.appContext = textureView.getContext().getApplicationContext();
        textureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                applyVideoTransform();
            }
        });
    }

    public void setVideoUrl(String url) {
        this.videoUrl = url;
        preparePlayer();
    }

    public void play() {
        playWhenReady = true;
        if (player != null) {
            player.play();
        }
    }

    public void pause() {
        playWhenReady = false;
        if (player != null) {
            player.pause();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
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
        releaseInternal();
    }

    private void preparePlayer() {
        if (videoUrl == null) {
            return;
        }
        releaseInternal();

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        MIN_BUFFER_MS,
                        MAX_BUFFER_MS,
                        BUFFER_FOR_PLAYBACK_MS,
                        BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
                )
                .build();
        player = new ExoPlayer.Builder(appContext)
                .setLoadControl(loadControl)
                .build();
        player.addListener(playerListener);
        player.setVideoTextureView(textureView);
        player.setRepeatMode(Player.REPEAT_MODE_ONE);
        player.setMediaSource(buildMediaSource(videoUrl));
        player.prepare();
        player.setPlayWhenReady(playWhenReady);
    }

    private ProgressiveMediaSource buildMediaSource(String url) {
        MediaItem mediaItem = MediaItem.fromUri(url);
        if (MediaUrlHelper.isRemoteHttpUrl(url)) {
            return new ProgressiveMediaSource.Factory(
                    ExoMediaCache.get(appContext).getCacheDataSourceFactory()
            ).createMediaSource(mediaItem);
        }
        return new ProgressiveMediaSource.Factory(
                new DefaultDataSource.Factory(appContext)
        ).createMediaSource(mediaItem);
    }

    private void releaseInternal() {
        if (player != null) {
            player.removeListener(playerListener);
            player.clearVideoTextureView(textureView);
            player.release();
            player = null;
        }
    }

    private void applyVideoTransform() {
        VideoTransformHelper.applyFitWidth(textureView, videoWidth, videoHeight);
    }
}
