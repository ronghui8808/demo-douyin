package com.example.douyin.comment;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.douyin.R;
import com.example.douyin.auth.LoginActivity;
import com.example.douyin.databinding.BottomSheetCommentBinding;
import com.example.douyin.repository.AuthRepository;
import com.example.douyin.repository.CommentRepository;
import com.example.douyin.util.CountFormatter;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class CommentBottomSheet extends BottomSheetDialogFragment {

    private static final String ARG_VIDEO_ID = "video_id";
    private static final String ARG_COMMENT_COUNT = "comment_count";

    public interface CommentPostedListener {
        void onCommentPosted(long videoId, int newCommentCount);
    }

    private long videoId;
    private int commentCount;
    private CommentPostedListener postedListener;

    private BottomSheetCommentBinding binding;
    private CommentViewModel viewModel;
    private AuthRepository authRepository;
    private CommentAdapter adapter;

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
        binding = BottomSheetCommentBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CommentRepository commentRepository = new CommentRepository(requireContext());
        authRepository = new AuthRepository(requireContext());
        viewModel = new ViewModelProvider(
                this,
                new CommentViewModel.Factory(videoId, commentCount, commentRepository)
        ).get(CommentViewModel.class);
        adapter = new CommentAdapter();

        binding.recyclerComments.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerComments.setAdapter(adapter);
        binding.recyclerComments.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                CommentUiState state = viewModel.getUiState().getValue();
                if (dy <= 0 || state == null || state.loading || state.loadingMore || !state.hasMore) {
                    return;
                }
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager == null) {
                    return;
                }
                int lastVisible = layoutManager.findLastVisibleItemPosition();
                if (lastVisible >= adapter.getItemCount() - 3) {
                    viewModel.loadMore();
                }
            }
        });

        binding.btnSend.setOnClickListener(v -> sendComment());
        binding.etComment.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendComment();
                return true;
            }
            return false;
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getLoginRequired().observe(getViewLifecycleOwner(), ignored -> {
            Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireContext(), LoginActivity.class));
        });
        viewModel.getToastMessage().observe(getViewLifecycleOwner(), message -> {
            if ("empty_content".equals(message)) {
                Toast.makeText(requireContext(), R.string.comment_content_empty, Toast.LENGTH_SHORT).show();
            } else if (message != null) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getCommentPosted().observe(getViewLifecycleOwner(), newCount -> {
            binding.etComment.setText("");
            binding.recyclerComments.scrollToPosition(0);
            if (postedListener != null && newCount != null) {
                postedListener.onCommentPosted(videoId, newCount);
            }
        });

        viewModel.loadInitial();
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private void sendComment() {
        String content = binding.etComment.getText() != null
                ? binding.etComment.getText().toString()
                : "";
        viewModel.send(content, authRepository.isLoggedIn());
    }

    private void render(CommentUiState state) {
        if (state == null || binding == null) {
            return;
        }
        binding.tvTitle.setText(getString(
                R.string.comment_title_format,
                CountFormatter.format(state.commentCount)));

        adapter.submitList(state.comments);

        binding.progressLoading.setVisibility(state.loading ? View.VISIBLE : View.GONE);
        binding.progressLoadMore.setVisibility(state.loadingMore ? View.VISIBLE : View.GONE);
        binding.btnSend.setEnabled(!state.posting);

        if (state.loading) {
            binding.tvEmpty.setVisibility(View.GONE);
            binding.recyclerComments.setVisibility(View.INVISIBLE);
            return;
        }

        binding.recyclerComments.setVisibility(View.VISIBLE);
        if (state.error != null && state.comments.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.tvEmpty.setText(state.error);
        } else if (state.isEmpty()) {
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.tvEmpty.setText(R.string.comment_empty);
        } else {
            binding.tvEmpty.setVisibility(View.GONE);
        }
    }
}
