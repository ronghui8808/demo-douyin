package com.example.douyin.feed;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.douyin.R;
import com.example.douyin.auth.LoginActivity;
import com.example.douyin.cache.MediaCacheManager;
import com.example.douyin.databinding.FragmentFeedBinding;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.repository.VideoRepository;

import java.util.List;

public class FeedFragment extends Fragment {

    private static final int FEED_PAGE_SIZE = 20;

    private FragmentFeedBinding binding;
    private FeedViewModel viewModel;
    private FeedPagerAdapter pagerAdapter;
    private VideoRepository videoRepository;
    private AuthRepository authRepository;
    private int activePosition;
    private boolean contentBound;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentFeedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        videoRepository = new VideoRepository(requireContext());
        authRepository = new AuthRepository(requireContext());
        viewModel = new ViewModelProvider(this, new FeedViewModel.Factory(videoRepository, FEED_PAGE_SIZE))
                .get(FeedViewModel.class);

        pagerAdapter = new FeedPagerAdapter(this);
        binding.viewPager.setAdapter(pagerAdapter);
        binding.viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        binding.viewPager.setOffscreenPageLimit(1);

        RecyclerView recyclerView = (RecyclerView) binding.viewPager.getChildAt(0);
        if (recyclerView != null) {
            recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }

        binding.viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                activePosition = position;
                prefetchAround(position);
            }
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getLoginRequired().observe(getViewLifecycleOwner(), msg -> {
            Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireContext(), LoginActivity.class));
        });

        binding.tvError.setOnClickListener(v -> viewModel.loadFeed());

        // ViewModel survives rotation; only fetch when there is no retained list/error UI.
        FeedUiState existing = viewModel.getUiState().getValue();
        if (existing == null || (existing.videos.isEmpty() && existing.errorMessage == null)) {
            viewModel.loadFeed();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        contentBound = false;
    }

    public boolean isActivePage(int position) {
        return position == activePosition;
    }

    public VideoDto getVideoAt(int position) {
        return pagerAdapter.getVideo(position);
    }

    public void onVideoLikeChanged(long videoId, boolean isLiked, int likeCount) {
        // Local adapter update avoids ViewPager2 full refresh flicker.
        pagerAdapter.updateLike(videoId, isLiked, likeCount);
        viewModel.updateLike(videoId, isLiked, likeCount);
    }

    public void onVideoCommentChanged(long videoId, int commentCount) {
        pagerAdapter.updateCommentCount(videoId, commentCount);
        viewModel.updateCommentCount(videoId, commentCount);
    }

    public void refreshFeed() {
        if (isAdded() && viewModel != null) {
            viewModel.loadFeed();
        }
    }

    public void toggleLike(long videoId, ApiCallback<LikeResult> callback) {
        if (!authRepository.isLoggedIn()) {
            viewModel.toggleLike(videoId, false);
            if (callback != null) {
                callback.onError(401, getString(R.string.login_required));
            }
            return;
        }
        // Logged-in: keep ApiCallback for VideoPageFragment Toast/UI.
        // FeedViewModel.toggleLike has no callback; sync LiveData via onVideoLikeChanged.
        videoRepository.toggleLike(videoId, callback);
    }

    private void render(FeedUiState state) {
        if (binding == null) {
            return;
        }
        if (state.loading) {
            contentBound = false;
            showLoading();
            return;
        }
        if (state.errorMessage != null) {
            contentBound = false;
            showError(state.errorMessage);
            return;
        }
        if (state.isEmpty()) {
            contentBound = false;
            showEmpty();
            return;
        }

        if (shouldReplaceList(state.videos)) {
            pagerAdapter.submitList(state.videos);
            activePosition = 0;
            binding.viewPager.setCurrentItem(0, false);
            contentBound = true;
            showContent();
            prefetchAround(0);
        } else {
            // Like/comment LiveData sync: do not submitList (avoids flicker).
            showContent();
        }
    }

    private boolean shouldReplaceList(List<VideoDto> videos) {
        if (!contentBound || pagerAdapter.getItemCount() != videos.size()) {
            return true;
        }
        if (videos.isEmpty()) {
            return true;
        }
        VideoDto firstBound = pagerAdapter.getVideo(0);
        return firstBound == null || firstBound.id != videos.get(0).id;
    }

    private void prefetchAround(int position) {
        if (!isAdded()) {
            return;
        }
        MediaCacheManager cacheManager = MediaCacheManager.get(requireContext());
        prefetchAt(cacheManager, position);
        prefetchAt(cacheManager, position + 1);
        prefetchAt(cacheManager, position - 1);
    }

    private void prefetchAt(MediaCacheManager cacheManager, int position) {
        VideoDto video = pagerAdapter.getVideo(position);
        if (video == null) {
            return;
        }
        cacheManager.prefetchVideo(video.videoUrl);
        if (video.coverUrl != null && !video.coverUrl.isEmpty()) {
            cacheManager.prefetchImage(video.coverUrl);
        }
    }

    private void showLoading() {
        binding.viewPager.setVisibility(View.GONE);
        binding.tvEmpty.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.GONE);
        binding.progressLoading.setVisibility(View.VISIBLE);
    }

    private void showEmpty() {
        binding.viewPager.setVisibility(View.GONE);
        binding.progressLoading.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.GONE);
        binding.tvEmpty.setVisibility(View.VISIBLE);
    }

    private void showError(String message) {
        binding.viewPager.setVisibility(View.GONE);
        binding.progressLoading.setVisibility(View.GONE);
        binding.tvEmpty.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.VISIBLE);
        binding.tvError.setText(message != null ? message : getString(R.string.feed_load_failed));
    }

    private void showContent() {
        binding.viewPager.setVisibility(View.VISIBLE);
        binding.progressLoading.setVisibility(View.GONE);
        binding.tvEmpty.setVisibility(View.GONE);
        binding.tvError.setVisibility(View.GONE);
    }
}
