package com.example.douyin.feed;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.example.douyin.R;
import com.example.douyin.cache.MediaCacheManager;
import com.example.douyin.comment.CommentBottomSheet;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.player.VideoPlayerController;
import com.example.douyin.profile.UserProfileController;
import com.example.douyin.util.CountFormatter;
import com.example.douyin.widget.ProfileSwipeLayout;

public class VideoPageFragment extends Fragment {

    private static final String ARG_POSITION = "position";
    private static final int PAGE_VIDEO = 0;
    private static final int PAGE_PROFILE = 1;

    private int pagePosition;
    private VideoDto video;
    private ViewPager2 slidePager;
    private VideoPlayerController playerController;
    private TextureView textureView;
    private boolean playbackReady;
    private boolean videoBound;
    private ImageView ivLike;
    private TextView tvLikeCount;
    private TextView tvCommentCount;
    private TextView tvAuthor;
    private TextView tvDescription;
    private TextView tvAvatarLetter;
    private LinearLayout btnLike;
    private UserProfileController profileController;

    public static VideoPageFragment newInstance(int position) {
        VideoPageFragment fragment = new VideoPageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_POSITION, position);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        pagePosition = args != null ? args.getInt(ARG_POSITION, 0) : 0;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_video_page, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        video = resolveVideo();
        if (video == null) {
            return;
        }

        slidePager = view.findViewById(R.id.pager_slide);
        slidePager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        slidePager.setAdapter(new SlidePagerAdapter());
        slidePager.setCurrentItem(PAGE_VIDEO, false);
        slidePager.setUserInputEnabled(hasAuthorProfile());
        slidePager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                onSlidePageSelected(position);
            }
        });
    }

    private boolean hasAuthorProfile() {
        return video.author != null && video.author.id > 0;
    }

    private void onSlidePageSelected(int position) {
        if (position == PAGE_PROFILE) {
            if (playerController != null) {
                playerController.pause();
            }
        } else if (playerController != null && playbackReady && isPageActive() && isResumed()) {
            playerController.play();
        }
    }

    private void bindVideoPage(@NonNull View pageView) {
        if (videoBound) {
            return;
        }
        videoBound = true;

        textureView = pageView.findViewById(R.id.texture_video);
        ivLike = pageView.findViewById(R.id.iv_like);
        tvLikeCount = pageView.findViewById(R.id.tv_like_count);
        tvCommentCount = pageView.findViewById(R.id.tv_comment_count);
        tvAuthor = pageView.findViewById(R.id.tv_author);
        tvDescription = pageView.findViewById(R.id.tv_description);
        tvAvatarLetter = pageView.findViewById(R.id.tv_avatar_letter);
        btnLike = pageView.findViewById(R.id.btn_like);

        bindOverlay();
        setupActions(pageView);
        setupProfileSwipe(pageView);
        setupAvatarEntry(pageView);

        playerController = new VideoPlayerController(textureView);
        preparePlayback();
    }

    private void bindProfilePage(@NonNull View pageView) {
        if (profileController != null || !hasAuthorProfile()) {
            return;
        }
        setupProfileSwipeBack(pageView);
        profileController = new UserProfileController(
                pageView,
                video.author.id,
                false,
                true,
                null
        );
        profileController.load();
    }

    private void setupProfileSwipe(@NonNull View pageView) {
        ProfileSwipeLayout swipeLayout = pageView.findViewById(R.id.profile_swipe_layout);
        if (swipeLayout == null || !hasAuthorProfile()) {
            return;
        }
        swipeLayout.setMode(ProfileSwipeLayout.Mode.VIDEO);
        swipeLayout.setSwipeListener(new ProfileSwipeLayout.Listener() {
            @Override
            public void onSwipeToProfile() {
                openAuthorProfile();
            }

            @Override
            public void onSwipeToVideo() {
                // 视频页不处理右滑
            }
        });
    }

    private void setupProfileSwipeBack(@NonNull View pageView) {
        ProfileSwipeLayout swipeLayout = pageView.findViewById(R.id.profile_swipe_layout);
        if (swipeLayout == null) {
            return;
        }
        swipeLayout.setMode(ProfileSwipeLayout.Mode.PROFILE);
        swipeLayout.setSwipeListener(new ProfileSwipeLayout.Listener() {
            @Override
            public void onSwipeToProfile() {
                // 个人页不处理左滑
            }

            @Override
            public void onSwipeToVideo() {
                closeAuthorProfile();
            }
        });
    }

    private void setupAvatarEntry(@NonNull View pageView) {
        if (!hasAuthorProfile()) {
            return;
        }
        View avatarContainer = pageView.findViewById(R.id.view_avatar);
        if (avatarContainer == null) {
            return;
        }
        View clickTarget = (View) avatarContainer.getParent();
        clickTarget.setOnClickListener(v -> openAuthorProfile());
    }

    private void openAuthorProfile() {
        if (!hasAuthorProfile() || slidePager == null) {
            return;
        }
        slidePager.setCurrentItem(PAGE_PROFILE, true);
    }

    private void closeAuthorProfile() {
        if (slidePager == null) {
            return;
        }
        slidePager.setCurrentItem(PAGE_VIDEO, true);
    }

    private void preparePlayback() {
        MediaCacheManager.get(requireContext()).resolveVideoForPlayback(
                video.videoUrl,
                new MediaCacheManager.CacheCallback() {
                    @Override
                    public void onReady(String playableUrl) {
                        if (!isAdded() || playerController == null) {
                            return;
                        }
                        playbackReady = true;
                        playerController.setVideoUrl(playableUrl);
                        if (isPageActive() && isResumed() && isShowingVideo()) {
                            playerController.play();
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (!isAdded() || playerController == null) {
                            return;
                        }
                        playbackReady = true;
                        playerController.setVideoUrl(video.videoUrl);
                        if (isPageActive() && isResumed() && isShowingVideo()) {
                            playerController.play();
                        }
                    }
                }
        );
    }

    private boolean isShowingVideo() {
        return slidePager == null || slidePager.getCurrentItem() == PAGE_VIDEO;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (playerController != null && playbackReady && isPageActive() && isShowingVideo()) {
            playerController.play();
        }
    }

    @Override
    public void onPause() {
        if (playerController != null) {
            playerController.pause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (playerController != null) {
            playerController.release();
            playerController = null;
        }
        profileController = null;
        videoBound = false;
        super.onDestroyView();
    }

    private VideoDto resolveVideo() {
        Fragment parent = getParentFragment();
        if (parent instanceof FeedFragment) {
            return ((FeedFragment) parent).getVideoAt(pagePosition);
        }
        return null;
    }

    private boolean isPageActive() {
        Fragment parent = getParentFragment();
        if (parent instanceof FeedFragment) {
            return ((FeedFragment) parent).isActivePage(pagePosition);
        }
        return false;
    }

    private void bindOverlay() {
        if (video.author != null && !TextUtils.isEmpty(video.author.nickname)) {
            tvAuthor.setText("@" + video.author.nickname);
            tvAvatarLetter.setText(video.author.nickname.substring(0, 1));
        } else {
            tvAuthor.setText(R.string.demo_author);
            tvAvatarLetter.setText("抖");
        }
        tvDescription.setText(video.description);
        updateLikeUi(video.isLiked, video.likeCount);
        tvCommentCount.setText(CountFormatter.format(video.commentCount));
    }

    private void setupActions(View root) {
        btnLike.setOnClickListener(v -> onLikeClicked());
        root.findViewById(R.id.btn_comment).setOnClickListener(v -> openComments());
        root.findViewById(R.id.btn_share).setOnClickListener(v -> shareVideo());
    }

    private void openComments() {
        if (video == null) {
            return;
        }
        CommentBottomSheet sheet = CommentBottomSheet.newInstance(video.id, video.commentCount);
        sheet.setCommentPostedListener((videoId, newCommentCount) -> {
            if (!isAdded() || video == null || video.id != videoId) {
                return;
            }
            video.commentCount = newCommentCount;
            tvCommentCount.setText(CountFormatter.format(newCommentCount));
            Fragment parent = getParentFragment();
            if (parent instanceof FeedFragment) {
                ((FeedFragment) parent).onVideoCommentChanged(videoId, newCommentCount);
            }
        });
        sheet.show(getParentFragmentManager(), "comments");
    }

    private void onLikeClicked() {
        Fragment parent = getParentFragment();
        if (!(parent instanceof FeedFragment) || video == null) {
            return;
        }
        ((FeedFragment) parent).toggleLike(video.id, new ApiCallback<LikeResult>() {
            @Override
            public void onSuccess(LikeResult data) {
                if (!isAdded() || video == null) {
                    return;
                }
                video.isLiked = data.isLiked;
                video.likeCount = data.likeCount;
                updateLikeUi(data.isLiked, data.likeCount);
                ((FeedFragment) parent).onVideoLikeChanged(video.id, data.isLiked, data.likeCount);
            }

            @Override
            public void onError(int code, String message) {
                if (!isAdded() || code == 401) {
                    return;
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateLikeUi(boolean isLiked, int likeCount) {
        ivLike.setImageResource(isLiked ? R.drawable.ic_like_active : R.drawable.ic_like_normal);
        tvLikeCount.setText(CountFormatter.format(likeCount));
    }

    private void shareVideo() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, video.description + "\n" + video.videoUrl);
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)));
    }

    private final class SlidePagerAdapter extends RecyclerView.Adapter<SlidePagerAdapter.Holder> {

        private static final int TYPE_PROFILE = 0;
        private static final int TYPE_VIDEO = 1;

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(parent.getContext());
            View itemView;
            if (viewType == TYPE_PROFILE) {
                itemView = inflater.inflate(R.layout.page_profile_content, parent, false);
            } else {
                itemView = inflater.inflate(R.layout.page_video_content, parent, false);
            }
            return new Holder(itemView, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (holder.viewType == TYPE_VIDEO) {
                bindVideoPage(holder.itemView);
            } else {
                bindProfilePage(holder.itemView);
            }
        }

        @Override
        public int getItemCount() {
            return hasAuthorProfile() ? 2 : 1;
        }

        @Override
        public int getItemViewType(int position) {
            if (!hasAuthorProfile()) {
                return TYPE_VIDEO;
            }
            return position == PAGE_VIDEO ? TYPE_VIDEO : TYPE_PROFILE;
        }

        class Holder extends RecyclerView.ViewHolder {
            final int viewType;

            Holder(@NonNull View itemView, int viewType) {
                super(itemView);
                this.viewType = viewType;
            }
        }
    }
}
