package com.example.douyin.local.service;

import android.content.Context;
import android.text.TextUtils;

import com.example.douyin.local.EntityMapper;
import com.example.douyin.local.db.LikeDao;
import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.VideoDao;
import com.example.douyin.local.db.entity.LikeEntity;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.local.db.entity.VideoEntity;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.FeedPage;
import com.example.douyin.network.model.LikeResult;
import com.example.douyin.network.model.VideoDto;
import com.example.douyin.oss.OssConfig;
import com.example.douyin.oss.OssUploadService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class LocalVideoService {

    private final Context appContext;
    private final UserDao userDao;
    private final VideoDao videoDao;
    private final LikeDao likeDao;

    public LocalVideoService(Context context, UserDao userDao, VideoDao videoDao, LikeDao likeDao) {
        this.appContext = context.getApplicationContext();
        this.userDao = userDao;
        this.videoDao = videoDao;
        this.likeDao = likeDao;
    }

    public ApiResponse<FeedPage> getFeed(int page, int size, Long currentUserId) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int offset = safePage * safeSize;

        List<VideoEntity> entities = videoDao.getFeed(offset, safeSize + 1);
        boolean hasMore = entities.size() > safeSize;
        if (hasMore) {
            entities = entities.subList(0, safeSize);
        }

        FeedPage feedPage = new FeedPage();
        feedPage.page = safePage;
        feedPage.size = safeSize;
        feedPage.hasMore = hasMore;
        feedPage.list = toVideoDtoList(entities, currentUserId);
        return ApiResponse.ok(feedPage);
    }

    public ApiResponse<FeedPage> getUserVideos(long userId, int page, int size, Long currentUserId) {
        if (userDao.findById(userId) == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        int safePage = Math.max(page, 0);
        int safeSize = Math.max(size, 1);
        int offset = safePage * safeSize;

        List<VideoEntity> entities = videoDao.getByUserId(userId, offset, safeSize + 1);
        boolean hasMore = entities.size() > safeSize;
        if (hasMore) {
            entities = entities.subList(0, safeSize);
        }

        FeedPage feedPage = new FeedPage();
        feedPage.page = safePage;
        feedPage.size = safeSize;
        feedPage.hasMore = hasMore;
        feedPage.list = toVideoDtoList(entities, currentUserId);
        return ApiResponse.ok(feedPage);
    }

    public ApiResponse<VideoDto> publishVideo(long userId, File sourceFile, File coverFile, String description)
            throws IOException {
        if (sourceFile == null || !sourceFile.exists()) {
            return ApiResponse.error(400, "视频文件无效");
        }
        if (userDao.findById(userId) == null) {
            return ApiResponse.error(401, "未登录");
        }

        String storedVideoPath;
        String storedCoverPath = null;
        try {
            if (OssConfig.get().isConfigured()) {
                OssUploadService uploader = OssUploadService.get(appContext);
                storedVideoPath = uploader.uploadVideo(sourceFile, userId);
                if (coverFile != null && coverFile.exists()) {
                    storedCoverPath = uploader.uploadImage(coverFile, userId);
                }
            } else {
                storedVideoPath = copyVideoToLocal(sourceFile);
                if (coverFile != null && coverFile.exists()) {
                    storedCoverPath = copyCoverToLocal(coverFile);
                }
            }
        } catch (Exception e) {
            return ApiResponse.error(500, e.getMessage() != null ? e.getMessage() : "上传失败");
        }

        VideoEntity entity = new VideoEntity();
        entity.userId = userId;
        entity.filePath = storedVideoPath;
        entity.coverPath = storedCoverPath;
        entity.description = TextUtils.isEmpty(description) ? "" : description.trim();
        entity.likeCount = 0;
        entity.commentCount = 0;
        entity.createdAt = System.currentTimeMillis();
        entity.id = videoDao.insert(entity);

        return ApiResponse.ok(toVideoDto(entity, userId));
    }

    private String copyVideoToLocal(File sourceFile) throws IOException {
        File videosDir = new File(appContext.getFilesDir(), "videos");
        if (!videosDir.exists() && !videosDir.mkdirs()) {
            throw new IOException("无法创建视频目录");
        }
        File targetFile = new File(videosDir, UUID.randomUUID().toString() + ".mp4");
        copyFile(sourceFile, targetFile);
        return targetFile.getAbsolutePath();
    }

    private String copyCoverToLocal(File sourceFile) throws IOException {
        File coversDir = new File(appContext.getFilesDir(), "covers");
        if (!coversDir.exists() && !coversDir.mkdirs()) {
            throw new IOException("无法创建封面目录");
        }
        String extension = sourceFile.getName().contains(".")
                ? sourceFile.getName().substring(sourceFile.getName().lastIndexOf('.'))
                : ".jpg";
        File targetFile = new File(coversDir, UUID.randomUUID().toString() + extension);
        copyFile(sourceFile, targetFile);
        return targetFile.getAbsolutePath();
    }

    public ApiResponse<LikeResult> toggleLike(long userId, long videoId) {
        VideoEntity video = videoDao.findById(videoId);
        if (video == null) {
            return ApiResponse.error(404, "视频不存在");
        }

        LikeEntity existing = likeDao.find(userId, videoId);
        boolean isLiked;
        if (existing != null) {
            likeDao.delete(userId, videoId);
            video.likeCount = Math.max(0, video.likeCount - 1);
            isLiked = false;
        } else {
            LikeEntity like = new LikeEntity();
            like.userId = userId;
            like.videoId = videoId;
            likeDao.insert(like);
            video.likeCount += 1;
            isLiked = true;
        }
        videoDao.updateLikeCount(videoId, video.likeCount);

        LikeResult result = new LikeResult();
        result.isLiked = isLiked;
        result.likeCount = video.likeCount;
        return ApiResponse.ok(result);
    }

    private List<VideoDto> toVideoDtoList(List<VideoEntity> entities, Long currentUserId) {
        List<VideoDto> list = new ArrayList<>();
        for (VideoEntity entity : entities) {
            list.add(toVideoDto(entity, currentUserId));
        }
        return list;
    }

    private VideoDto toVideoDto(VideoEntity entity, Long currentUserId) {
        VideoDto dto = new VideoDto();
        dto.id = entity.id;
        dto.videoUrl = EntityMapper.toVideoUrl(entity.filePath);
        if (!TextUtils.isEmpty(entity.coverPath)) {
            dto.coverUrl = EntityMapper.toVideoUrl(entity.coverPath);
        }
        dto.description = entity.description;
        dto.likeCount = entity.likeCount;
        dto.commentCount = entity.commentCount;

        UserEntity author = userDao.findById(entity.userId);
        if (author != null) {
            dto.author = EntityMapper.toUserDto(author);
        }

        if (currentUserId != null && currentUserId > 0) {
            dto.isLiked = likeDao.find(currentUserId, entity.id) != null;
        } else {
            dto.isLiked = false;
        }
        return dto;
    }

    private void copyFile(File source, File target) throws IOException {
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
