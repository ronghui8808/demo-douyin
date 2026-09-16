package com.example.douyin.feed;

/**
 * 视频页播放状态判定。只依赖播放意图（playWhenReady），不依赖 MediaPlayer 的瞬时状态，
 * 避免 prepareAsync 期间把"正在准备播放"误判成"已暂停"。
 */
final class PlaybackUiPolicy {

    private PlaybackUiPolicy() {
    }

    static boolean shouldShowPauseIndicator(boolean playbackReady,
                                            boolean playWhenReady,
                                            boolean showingVideo,
                                            boolean pageActive,
                                            boolean fragmentResumed) {
        return playbackReady && !playWhenReady && showingVideo && pageActive && fragmentResumed;
    }

    static boolean shouldAutoPlay(boolean playbackReady,
                                  boolean showingVideo,
                                  boolean pageActive,
                                  boolean fragmentResumed,
                                  boolean userPaused) {
        return playbackReady && showingVideo && pageActive && fragmentResumed && !userPaused;
    }
}
