package com.example.douyin.profile;

import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;

import com.example.douyin.R;
import com.example.douyin.auth.PhoneMasker;
import com.example.douyin.auth.PhoneValidator;
import com.example.douyin.friends.FollowingListActivity;
import com.example.douyin.message.ChatActivity;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.UserProfileDto;
import com.example.douyin.repository.UserRepository;
import com.example.douyin.util.CountFormatter;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class UserProfileController {

    public interface LogoutListener {
        void onLogoutRequested();
    }

    public interface RefreshListener {
        void onRefreshComplete();
    }

    private final View root;
    private final long userId;
    private final boolean showLogout;
    private final boolean embedded;
    @Nullable
    private final LogoutListener logoutListener;
    @Nullable
    private RefreshListener refreshListener;

    private final UserRepository userRepository;
    private final ProfileVideoAdapter videoAdapter;

    private ProgressBar progressProfile;
    private View layoutProfileContent;
    private TextView tvSwipeHint;
    private TextView tvAvatarLetter;
    private TextView tvNickname;
    private TextView tvUsername;
    private TextView tvPhone;
    private TextView tvVideoCount;
    private TextView tvLikeCount;
    private TextView tvFollowingCount;
    private View layoutFollowingStat;
    private TextView tvVideosEmpty;
    private RecyclerView rvVideos;
    private MaterialButton btnLogout;
    private MaterialButton btnDm;
    @Nullable
    private SwipeRefreshLayout swipeRefresh;

    private boolean loaded;
    private boolean refreshPending;
    @Nullable
    private String currentNickname;

    public UserProfileController(@NonNull View root,
                                 long userId,
                                 boolean showLogout,
                                 boolean embedded,
                                 @Nullable LogoutListener logoutListener) {
        this.root = root;
        this.userId = userId;
        this.showLogout = showLogout;
        this.embedded = embedded;
        this.logoutListener = logoutListener;
        this.userRepository = new UserRepository(root.getContext());
        this.videoAdapter = new ProfileVideoAdapter();
        bindViews();
    }

    public void setRefreshListener(@Nullable RefreshListener refreshListener) {
        this.refreshListener = refreshListener != null ? refreshListener : this::stopRefreshIndicator;
    }

    private void bindViews() {
        progressProfile = root.findViewById(R.id.progress_profile);
        layoutProfileContent = root.findViewById(R.id.layout_profile_content);
        tvSwipeHint = root.findViewById(R.id.tv_swipe_hint);
        tvAvatarLetter = root.findViewById(R.id.tv_avatar_letter);
        tvNickname = root.findViewById(R.id.tv_nickname);
        tvUsername = root.findViewById(R.id.tv_username);
        tvPhone = root.findViewById(R.id.tv_phone);
        tvVideoCount = root.findViewById(R.id.tv_video_count);
        tvLikeCount = root.findViewById(R.id.tv_like_count);
        tvFollowingCount = root.findViewById(R.id.tv_following_count);
        layoutFollowingStat = root.findViewById(R.id.layout_following_stat);
        tvVideosEmpty = root.findViewById(R.id.tv_videos_empty);
        rvVideos = root.findViewById(R.id.rv_videos);
        btnLogout = root.findViewById(R.id.btn_logout);
        btnDm = root.findViewById(R.id.btn_dm);

        tvSwipeHint.setVisibility(embedded ? View.VISIBLE : View.GONE);
        btnLogout.setVisibility(showLogout ? View.VISIBLE : View.GONE);
        btnDm.setVisibility(showLogout ? View.GONE : View.VISIBLE);
        btnDm.setOnClickListener(v ->
                ChatActivity.start(root.getContext(), userId, currentNickname));
        if (layoutFollowingStat != null) {
            if (showLogout) {
                layoutFollowingStat.setOnClickListener(v ->
                        root.getContext().startActivity(
                                new Intent(root.getContext(), FollowingListActivity.class)));
            } else {
                layoutFollowingStat.setClickable(false);
                layoutFollowingStat.setFocusable(false);
            }
        }
        if (showLogout) {
            btnLogout.setOnClickListener(v -> {
                if (logoutListener != null) {
                    logoutListener.onLogoutRequested();
                }
            });
        }

        rvVideos.setLayoutManager(new GridLayoutManager(root.getContext(), 3));
        rvVideos.setAdapter(videoAdapter);
        bindSwipeRefresh();
    }

    private void bindSwipeRefresh() {
        swipeRefresh = root.findViewById(R.id.swipe_refresh);
        if (swipeRefresh == null) {
            return;
        }
        swipeRefresh.setColorSchemeResources(R.color.douyin_red);
        swipeRefresh.setOnRefreshListener(this::refresh);
        if (refreshListener == null) {
            refreshListener = this::stopRefreshIndicator;
        }
    }

    private void stopRefreshIndicator() {
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(false);
        }
    }

    public void load() {
        if (loaded || userId <= 0) {
            return;
        }
        loaded = true;
        progressProfile.setVisibility(View.VISIBLE);
        layoutProfileContent.setVisibility(View.GONE);
        tvVideosEmpty.setVisibility(View.GONE);

        userRepository.getUserProfile(userId, new ApiCallback<UserProfileDto>() {
            @Override
            public void onSuccess(UserProfileDto data) {
                bindProfile(data);
                loadVideos();
            }

            @Override
            public void onError(int code, String message) {
                progressProfile.setVisibility(View.GONE);
                tvVideosEmpty.setVisibility(View.VISIBLE);
                tvVideosEmpty.setText(R.string.profile_load_failed);
                notifyRefreshComplete();
            }
        });
    }

    public void refreshOnEnter() {
        if (userId <= 0) {
            return;
        }
        if (swipeRefresh != null) {
            swipeRefresh.setRefreshing(true);
        }
        refresh();
    }

    public void refresh() {
        loaded = false;
        refreshPending = true;
        videoAdapter.submitList(null);
        rvVideos.setVisibility(View.GONE);
        tvVideosEmpty.setVisibility(View.GONE);
        load();
    }

    private void notifyRefreshComplete() {
        if (!refreshPending) {
            return;
        }
        refreshPending = false;
        if (refreshListener != null) {
            refreshListener.onRefreshComplete();
        }
    }

    private void bindProfile(@NonNull UserProfileDto profile) {
        progressProfile.setVisibility(View.GONE);
        layoutProfileContent.setVisibility(View.VISIBLE);

        String nickname = !TextUtils.isEmpty(profile.nickname) ? profile.nickname : profile.username;
        currentNickname = nickname;
        tvNickname.setText(nickname);
        boolean usernameIsPhone = !TextUtils.isEmpty(profile.username)
                && (profile.username.equals(profile.phone)
                || PhoneValidator.isValidPhone(profile.username));
        if (usernameIsPhone || TextUtils.isEmpty(profile.username)) {
            tvUsername.setVisibility(View.GONE);
            tvUsername.setText("");
        } else {
            tvUsername.setVisibility(View.VISIBLE);
            tvUsername.setText("@" + profile.username);
        }
        if (!TextUtils.isEmpty(profile.phone)) {
            tvPhone.setVisibility(View.VISIBLE);
            tvPhone.setText(PhoneMasker.mask(profile.phone));
        } else {
            tvPhone.setVisibility(View.GONE);
            tvPhone.setText("");
        }
        tvVideoCount.setText(String.valueOf(profile.videoCount));
        tvLikeCount.setText(CountFormatter.format(profile.totalLikeCount));
        if (tvFollowingCount != null) {
            tvFollowingCount.setText(String.valueOf(profile.followingCount));
        }

        if (!TextUtils.isEmpty(nickname)) {
            tvAvatarLetter.setText(nickname.substring(0, 1));
        } else {
            tvAvatarLetter.setText("抖");
        }
    }

    private void loadVideos() {
        userRepository.getUserVideos(userId, 0, new ApiCallback<FeedPage>() {
            @Override
            public void onSuccess(FeedPage data) {
                List<com.example.douyin.network.model.VideoDto> list = data.list;
                if (list == null || list.isEmpty()) {
                    rvVideos.setVisibility(View.GONE);
                    tvVideosEmpty.setVisibility(View.VISIBLE);
                } else {
                    rvVideos.setVisibility(View.VISIBLE);
                    tvVideosEmpty.setVisibility(View.GONE);
                    videoAdapter.submitList(list);
                }
                notifyRefreshComplete();
            }

            @Override
            public void onError(int code, String message) {
                rvVideos.setVisibility(View.GONE);
                tvVideosEmpty.setVisibility(View.VISIBLE);
                tvVideosEmpty.setText(R.string.profile_videos_load_failed);
                notifyRefreshComplete();
            }
        });
    }
}
