package com.example.douyin.feed;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.douyin.R;
import com.example.douyin.comment.CommentBottomSheet;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.player.VideoPlayerController;
import com.example.douyin.util.CountFormatter;

public class VideoPageFragment extends Fragment {

    private static final String ARG_POSITION = "position";

    private int pagePosition;
    private VideoDto video;
    private VideoPlayerController playerController;
    private TextureView textureView;
    private ImageView ivLike;
    private TextView tvLikeCount;
    private TextView tvCommentCount;
    private TextView tvAuthor;
    private TextView tvDescription;
    private TextView tvAvatarLetter;
    private LinearLayout btnLike;

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

        textureView = view.findViewById(R.id.texture_video);
        ivLike = view.findViewById(R.id.iv_like);
        tvLikeCount = view.findViewById(R.id.tv_like_count);
        tvCommentCount = view.findViewById(R.id.tv_comment_count);
        tvAuthor = view.findViewById(R.id.tv_author);
        tvDescription = view.findViewById(R.id.tv_description);
        tvAvatarLetter = view.findViewById(R.id.tv_avatar_letter);
        btnLike = view.findViewById(R.id.btn_like);

        bindOverlay();
        setupActions(view);

        playerController = new VideoPlayerController(textureView);
        playerController.setVideoUrl(video.videoUrl);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (playerController != null && isPageActive()) {
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
}
