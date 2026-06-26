package com.example.douyin.local.service;

import android.text.TextUtils;

import com.example.douyin.local.EntityMapper;
import com.example.douyin.local.db.CommentDao;
import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.VideoDao;
import com.example.douyin.local.db.entity.CommentEntity;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.db.entity.VideoEntity;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.CommentDto;
import com.example.douyin.network.model.CommentPage;

import java.util.ArrayList;
import java.util.List;

public class LocalCommentService {

    private final UserDao userDao;
    private final VideoDao videoDao;
    private final CommentDao commentDao;

    public LocalCommentService(UserDao userDao, VideoDao videoDao, CommentDao commentDao) {
        this.userDao = userDao;
        this.videoDao = videoDao;
        this.commentDao = commentDao;
    }

    public ApiResponse<CommentPage> getComments(long videoId, int page, int size) {
        if (videoDao.findById(videoId) == null) {
            return ApiResponse.error(404, "视频不存在");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int offset = safePage * safeSize;

        List<CommentEntity> entities = commentDao.getByVideoId(videoId, offset, safeSize + 1);
        boolean hasMore = entities.size() > safeSize;
        if (hasMore) {
            entities = entities.subList(0, safeSize);
        }

        CommentPage commentPage = new CommentPage();
        commentPage.page = safePage;
        commentPage.size = safeSize;
        commentPage.hasMore = hasMore;
        commentPage.list = toCommentDtoList(entities);
        return ApiResponse.ok(commentPage);
    }

    public ApiResponse<CommentDto> postComment(long userId, long videoId, String content) {
        if (TextUtils.isEmpty(content) || content.trim().isEmpty()) {
            return ApiResponse.error(400, "评论内容不能为空");
        }

        VideoEntity video = videoDao.findById(videoId);
        if (video == null) {
            return ApiResponse.error(404, "视频不存在");
        }

        UserEntity user = userDao.findById(userId);
        if (user == null) {
            return ApiResponse.error(401, "未登录");
        }

        CommentEntity entity = new CommentEntity();
        entity.videoId = videoId;
        entity.userId = userId;
        entity.content = content.trim();
        entity.createdAt = System.currentTimeMillis();
        entity.id = commentDao.insert(entity);

        video.commentCount += 1;
        videoDao.updateCommentCount(videoId, video.commentCount);

        return ApiResponse.ok(toCommentDto(entity, user));
    }

    private List<CommentDto> toCommentDtoList(List<CommentEntity> entities) {
        List<CommentDto> list = new ArrayList<>();
        for (CommentEntity entity : entities) {
            UserEntity user = userDao.findById(entity.userId);
            if (user != null) {
                list.add(toCommentDto(entity, user));
            }
        }
        return list;
    }

    private CommentDto toCommentDto(CommentEntity entity, UserEntity user) {
        CommentDto dto = new CommentDto();
        dto.id = entity.id;
        dto.videoId = entity.videoId;
        dto.content = entity.content;
        dto.createdAt = entity.createdAt;
        dto.user = EntityMapper.toUserDto(user);
        return dto;
    }
}
