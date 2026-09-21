package com.example.douyin.feed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.douyin.R;
import com.example.douyin.auth.LoginActivity;
import com.example.douyin.cache.ExoMediaCache;
import com.example.douyin.cache.MediaCacheManager;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.repository.VideoRepository;
import com.example.douyin.util.AppToast;

import java.util.List;

public class FeedFragment extends Fragment {

    private static final int FEED_PAGE_SIZE = 20;

    private ViewPager2 viewPager;
    private ProgressBar progressLoading;
    private TextView tvEmpty;
    private TextView tvError;
    private FeedPagerAdapter pagerAdapter;
    private VideoRepository videoRepository;
    private AuthRepository authRepository;
    private int activePosition;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        videoRepository = new VideoRepository(requireContext());
        authRepository = new AuthRepository(requireContext());

        viewPager = view.findViewById(R.id.view_pager);
        progressLoading = view.findViewById(R.id.progress_loading);
        tvEmpty = view.findViewById(R.id.tv_empty);
        tvError = view.findViewById(R.id.tv_error);

        pagerAdapter = new FeedPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setOffscreenPageLimit(1);

        RecyclerView recyclerView = (RecyclerView) viewPager.getChildAt(0);
        if (recyclerView != null) {
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                activePosition = position;
                prefetchAround(position);
            }
        });

        tvError.setOnClickListener(v -> loadFeed());
        loadFeed();
    }

    public boolean isActivePage(int position) {
        return position == activePosition;
    }

    public boolean isActiveVideo(long videoId) {
        VideoDto current = pagerAdapter.getVideo(activePosition);
        return current != null && current.id == videoId;
    }

    public VideoDto getVideoAt(int position) {
        return pagerAdapter.getVideo(position);
    }

    public VideoDto getVideoById(long videoId) {
        return pagerAdapter.getVideoById(videoId);
    }

    public void onVideoLikeChanged(long videoId, boolean isLiked, int likeCount) {
        pagerAdapter.updateLike(videoId, isLiked, likeCount);
    }

    public void onVideoCommentChanged(long videoId, int commentCount) {
        pagerAdapter.updateCommentCount(videoId, commentCount);
    }

    public void refreshFeed() {
        // View may be null during Activity restore before onViewCreated
        // (e.g. process death while recording → MainActivity setSelectedItemId → reselect).
        if (!isAdded() || getView() == null || viewPager == null) {
            return;
        }
        loadFeed();
    }

    @Override
    public void onDestroyView() {
        viewPager = null;
        progressLoading = null;
        tvEmpty = null;
        tvError = null;
        super.onDestroyView();
    }

    public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
        if (!authRepository.isLoggedIn()) {
            AppToast.show(requireContext(), R.string.login_required);
            startActivity(new Intent(requireContext(), LoginActivity.class));
            callback.onError(401, getString(R.string.login_required));
            return;
        }
        videoRepository.toggleLike(videoId, callback);
    }

    private void loadFeed() {
        showLoading();
        videoRepository.getFeed(0, FEED_PAGE_SIZE, new ApiCallback<FeedPage>() {
            @Override
            public void onSuccess(FeedPage data) {
                if (!isAdded()) {
                    return;
                }
                List<VideoDto> list = data.list;
                if (list == null || list.isEmpty()) {
                    showEmpty();
                    return;
                }
                pagerAdapter.submitList(list);
                viewPager.setVisibility(View.VISIBLE);
                progressLoading.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.GONE);
                tvError.setVisibility(View.GONE);
                activePosition = 0;
                viewPager.setCurrentItem(0, false);
                prefetchAround(0);
            }

            @Override
            public void onError(int code, String message) {
                if (!isAdded()) {
                    return;
                }
                showError(message);
            }
        });
    }

    private void prefetchAround(int position) {
        ExoMediaCache exoCache = ExoMediaCache.get(requireContext());
        MediaCacheManager imageCache = MediaCacheManager.get(requireContext());
        prefetchAt(exoCache, imageCache, position);
        prefetchAt(exoCache, imageCache, position + 1);
        prefetchAt(exoCache, imageCache, position - 1);
    }

    private void prefetchAt(ExoMediaCache exoCache, MediaCacheManager imageCache, int position) {
        VideoDto video = pagerAdapter.getVideo(position);
        if (video == null) {
            return;
        }
        exoCache.prefetch(video.videoUrl);
        if (video.coverUrl != null && !video.coverUrl.isEmpty()) {
            imageCache.prefetchImage(video.coverUrl);
        }
    }

    private void showLoading() {
        viewPager.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        progressLoading.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        viewPager.setVisibility(View.GONE);
        progressLoading.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        viewPager.setVisibility(View.GONE);
        progressLoading.setVisibility(View.GONE);
        tvEmpty.setVisibility(View.GONE);
        tvError.setVisibility(View.VISIBLE);
        tvError.setText(message != null ? message : getString(R.string.feed_load_failed));
    }
}
