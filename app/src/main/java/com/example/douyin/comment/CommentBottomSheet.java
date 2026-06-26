package com.example.douyin.comment;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.auth.LoginActivity;
import com.example.douyin.network.ApiCallback;
import com.example.douyin.network.model.CommentDto;
import com.example.douyin.network.model.CommentPage;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.repository.CommentRepository;
import com.example.douyin.util.CountFormatter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.util.List;

public class CommentBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_VIDEO_ID = "video_id";
    private static final String ARG_COMMENT_COUNT = "comment_count";
    private static final int PAGE_SIZE = 20;

    public interface CommentPostedListener {
        void onCommentPosted(long videoId, int newCommentCount);
    }

    private long videoId;
    private int commentCount;
    private CommentPostedListener postedListener;

    private CommentRepository commentRepository;
    private AuthRepository authRepository;
    private CommentAdapter adapter;

    private TextView tvTitle;
    private RecyclerView recyclerComments;
    private ProgressBar progressLoading;
    private ProgressBar progressLoadMore;
    private TextView tvEmpty;
    private EditText etComment;
    private TextView btnSend;

    private int currentPage;
    private boolean hasMore;
    private boolean loading;
    private boolean posting;

    public static CommentBottomSheet newInstance(long videoId, int commentCount) {
        CommentBottomSheet sheet = new CommentBottomSheet();
        Bundle args = new Bundle();
        args.putLong(ARG_VIDEO_ID, videoId);
        args.putInt(ARG_COMMENT_COUNT, commentCount);
        sheet.setArguments(args);
        return sheet;
    }

    public void setCommentPostedListener(CommentPostedListener listener) {
        this.postedListener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            videoId = args.getLong(ARG_VIDEO_ID);
            commentCount = args.getInt(ARG_COMMENT_COUNT);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_comment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        commentRepository = new CommentRepository(requireContext());
        authRepository = new AuthRepository(requireContext());
        adapter = new CommentAdapter();

        tvTitle = view.findViewById(R.id.tv_title);
        recyclerComments = view.findViewById(R.id.recycler_comments);
        progressLoading = view.findViewById(R.id.progress_loading);
        progressLoadMore = view.findViewById(R.id.progress_load_more);
        tvEmpty = view.findViewById(R.id.tv_empty);
        etComment = view.findViewById(R.id.et_comment);
        btnSend = view.findViewById(R.id.btn_send);

        updateTitle();
        recyclerComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerComments.setAdapter(adapter);
        recyclerComments.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || loading || !hasMore) {
                    return;
                }
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 3) {
                    loadComments(currentPage + 1, false);
                }
            }
        });

        btnSend.setOnClickListener(v -> postComment());
        etComment.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                postComment();
                return true;
            }
            return false;
        });

        loadComments(0, true);
    }

    @Override
    public void onStart() {
        super.onStart();
        if (!(getDialog() instanceof BottomSheetDialog)) {
            return;
        }
        BottomSheetDialog dialog = (BottomSheetDialog) getDialog();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            int peekHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.58f);
            bottomSheet.getLayoutParams().height = peekHeight;
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setPeekHeight(peekHeight);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    private void updateTitle() {
        tvTitle.setText(getString(R.string.comment_title_format, CountFormatter.format(commentCount)));
    }

    private void loadComments(int page, boolean replace) {
        if (loading) {
            return;
        }
        loading = true;
        if (replace) {
            progressLoading.setVisibility(View.VISIBLE);
            tvEmpty.setVisibility(View.GONE);
            recyclerComments.setVisibility(View.INVISIBLE);
        } else {
            progressLoadMore.setVisibility(View.VISIBLE);
        }

        commentRepository.getComments(videoId, page, PAGE_SIZE, new ApiCallback<CommentPage>() {
            @Override
            public void onSuccess(CommentPage data) {
                if (!isAdded()) {
                    return;
                }
                loading = false;
                progressLoading.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);

                List<CommentDto> list = data.list;
                if (replace) {
                    adapter.submitList(list);
                    recyclerComments.setVisibility(View.VISIBLE);
                    if (list == null || list.isEmpty()) {
                        tvEmpty.setVisibility(View.VISIBLE);
                    } else {
                        tvEmpty.setVisibility(View.GONE);
                    }
                } else {
                    adapter.appendList(list);
                }

                currentPage = data.page;
                hasMore = data.hasMore;
            }

            @Override
            public void onError(int code, String message) {
                if (!isAdded()) {
                    return;
                }
                loading = false;
                progressLoading.setVisibility(View.GONE);
                progressLoadMore.setVisibility(View.GONE);
                if (replace) {
                    recyclerComments.setVisibility(View.VISIBLE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    tvEmpty.setText(message != null ? message : getString(R.string.comment_load_failed));
                } else {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void postComment() {
        if (posting) {
            return;
        }
        if (!authRepository.isLoggedIn()) {
            Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireContext(), LoginActivity.class));
            return;
        }

        String content = etComment.getText() != null ? etComment.getText().toString().trim() : "";
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(requireContext(), R.string.comment_content_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        posting = true;
        btnSend.setEnabled(false);
        commentRepository.postComment(videoId, content, new ApiCallback<CommentDto>() {
            @Override
            public void onSuccess(CommentDto data) {
                if (!isAdded()) {
                    return;
                }
                posting = false;
                btnSend.setEnabled(true);
                etComment.setText("");
                adapter.prependComment(data);
                tvEmpty.setVisibility(View.GONE);
                recyclerComments.scrollToPosition(0);
                commentCount += 1;
                updateTitle();
                if (postedListener != null) {
                    postedListener.onCommentPosted(videoId, commentCount);
                }
            }

            @Override
            public void onError(int code, String message) {
                if (!isAdded()) {
                    return;
                }
                posting = false;
                btnSend.setEnabled(true);
                if (code == 401) {
                    Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(requireContext(), LoginActivity.class));
                    return;
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
