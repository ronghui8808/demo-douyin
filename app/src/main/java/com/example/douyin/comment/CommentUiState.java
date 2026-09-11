package com.example.douyin.comment;

import com.example.douyin.network.model.CommentDto;

import java.util.Collections;
import java.util.List;

public final class CommentUiState {

    public final List<CommentDto> comments;
    public final boolean loading;
    public final boolean loadingMore;
    public final boolean posting;
    public final boolean hasMore;
    public final String error;
    public final int commentCount;

    public CommentUiState(List<CommentDto> comments,
                          boolean loading,
                          boolean loadingMore,
                          boolean posting,
                          boolean hasMore,
                          String error,
                          int commentCount) {
        this.comments = comments != null
                ? Collections.unmodifiableList(comments)
                : Collections.emptyList();
        this.loading = loading;
        this.loadingMore = loadingMore;
        this.posting = posting;
        this.hasMore = hasMore;
        this.error = error;
        this.commentCount = commentCount;
    }

    public static CommentUiState initial(int commentCount) {
        return new CommentUiState(Collections.emptyList(), false, false, false, false, null, commentCount);
    }

    public boolean isEmpty() {
        return !loading && error == null && comments.isEmpty();
    }
}
