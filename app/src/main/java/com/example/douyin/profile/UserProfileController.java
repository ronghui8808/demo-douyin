package com.example.douyin.profile;

import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
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

    private final View root;
    private final long userId;
    private final boolean showLogout;
    private final boolean embedded;
    @Nullable
    private final LogoutListener logoutListener;

    private final UserRepository userRepository;
    private final ProfileVideoAdapter videoAdapter;

    private ProgressBar progressProfile;
    private View layoutProfileContent;
    private TextView tvSwipeHint;
    private TextView tvAvatarLetter;
    private TextView tvNickname;
    private TextView tvUsername;
    private TextView tvVideoCount;
    private TextView tvLikeCount;
    private TextView tvVideosEmpty;
    private RecyclerView rvVideos;
    private MaterialButton btnLogout;

    private boolean loaded;

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

    private void bindViews() {
        progressProfile = root.findViewById(R.id.progress_profile);
        layoutProfileContent = root.findViewById(R.id.layout_profile_content);
        tvSwipeHint = root.findViewById(R.id.tv_swipe_hint);
        tvAvatarLetter = root.findViewById(R.id.tv_avatar_letter);
        tvNickname = root.findViewById(R.id.tv_nickname);
        tvUsername = root.findViewById(R.id.tv_username);
        tvVideoCount = root.findViewById(R.id.tv_video_count);
        tvLikeCount = root.findViewById(R.id.tv_like_count);
        tvVideosEmpty = root.findViewById(R.id.tv_videos_empty);
        rvVideos = root.findViewById(R.id.rv_videos);
        btnLogout = root.findViewById(R.id.btn_logout);

        tvSwipeHint.setVisibility(embedded ? View.VISIBLE : View.GONE);
        btnLogout.setVisibility(showLogout ? View.VISIBLE : View.GONE);
        if (showLogout) {
            btnLogout.setOnClickListener(v -> {
                if (logoutListener != null) {
                    logoutListener.onLogoutRequested();
                }
            });
        }

        rvVideos.setLayoutManager(new GridLayoutManager(root.getContext(), 3));
        rvVideos.setAdapter(videoAdapter);
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
            }
        });
    }

    public void refresh() {
        loaded = false;
        videoAdapter.submitList(null);
        rvVideos.setVisibility(View.GONE);
        tvVideosEmpty.setVisibility(View.GONE);
        load();
    }

    private void bindProfile(@NonNull UserProfileDto profile) {
        progressProfile.setVisibility(View.GONE);
        layoutProfileContent.setVisibility(View.VISIBLE);

        String nickname = !TextUtils.isEmpty(profile.nickname) ? profile.nickname : profile.username;
        tvNickname.setText(nickname);
        tvUsername.setText("@" + profile.username);
        tvVideoCount.setText(String.valueOf(profile.videoCount));
        tvLikeCount.setText(CountFormatter.format(profile.totalLikeCount));

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
                    return;
                }
                rvVideos.setVisibility(View.VISIBLE);
                tvVideosEmpty.setVisibility(View.GONE);
                videoAdapter.submitList(list);
            }

            @Override
            public void onError(int code, String message) {
                rvVideos.setVisibility(View.GONE);
                tvVideosEmpty.setVisibility(View.VISIBLE);
                tvVideosEmpty.setText(R.string.profile_videos_load_failed);
            }
        });
    }
}
