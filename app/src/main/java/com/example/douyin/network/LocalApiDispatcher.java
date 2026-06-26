package com.example.douyin.network;

import android.content.Context;
import android.text.TextUtils;

import com.example.douyin.local.service.LocalAuthService;
import com.example.douyin.local.service.LocalCommentService;
import com.example.douyin.local.service.LocalVideoService;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.LoginRequest;
import com.example.douyin.network.model.PostCommentRequest;
import com.example.douyin.network.model.RegisterRequest;
import com.google.gson.Gson;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import okhttp3.Headers;
import okhttp3.MultipartReader;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import okio.Buffer;

public final class LocalApiDispatcher {

    private LocalApiDispatcher() {
    }

    public static LocalApiResult dispatch(
            Context context,
            Request request,
            LocalAuthService authService,
            LocalVideoService videoService,
            LocalCommentService commentService,
            Gson gson
    ) {
        try {
            String path = normalizePath(request.url().encodedPath());
            String method = request.method();

            if ("POST".equals(method) && path.equals("/api/auth/register")) {
                RegisterRequest body = readJsonBody(request, gson, RegisterRequest.class);
                return LocalApiResult.from(authService.register(body), gson);
            }

            if ("POST".equals(method) && path.equals("/api/auth/login")) {
                LoginRequest body = readJsonBody(request, gson, LoginRequest.class);
                return LocalApiResult.from(
                        authService.login(body.username, body.password),
                        gson
                );
            }

            if ("GET".equals(method) && path.equals("/api/users/me")) {
                Long userId = requireUserId(request, authService);
                if (userId == null) {
                    return LocalApiResult.from(ApiResponse.error(401, "未登录"), gson);
                }
                return LocalApiResult.from(authService.getMe(userId), gson);
            }

            if ("GET".equals(method) && path.equals("/api/videos/feed")) {
                int page = parseQueryInt(request, "page", 0);
                int size = parseQueryInt(request, "size", 10);
                Long userId = optionalUserId(request, authService);
                return LocalApiResult.from(videoService.getFeed(page, size, userId), gson);
            }

            if ("POST".equals(method) && path.equals("/api/videos")) {
                Long userId = requireUserId(request, authService);
                if (userId == null) {
                    return LocalApiResult.from(ApiResponse.error(401, "未登录"), gson);
                }
                PublishParts parts = parseMultipart(context, request);
                if (parts == null || parts.videoFile == null) {
                    return LocalApiResult.from(ApiResponse.error(400, "缺少视频文件"), gson);
                }
                return LocalApiResult.from(
                        videoService.publishVideo(userId, parts.videoFile, parts.coverFile, parts.description),
                        gson
                );
            }

            if ("POST".equals(method) && path.matches("/api/videos/\\d+/like")) {
                Long userId = requireUserId(request, authService);
                if (userId == null) {
                    return LocalApiResult.from(ApiResponse.error(401, "未登录"), gson);
                }
                long videoId = parsePathLong(path, "/api/videos/", "/like");
                return LocalApiResult.from(videoService.toggleLike(userId, videoId), gson);
            }

            if ("GET".equals(method) && path.matches("/api/videos/\\d+/comments")) {
                long videoId = parsePathLong(path, "/api/videos/", "/comments");
                int page = parseQueryInt(request, "page", 0);
                int size = parseQueryInt(request, "size", 20);
                return LocalApiResult.from(
                        commentService.getComments(videoId, page, size),
                        gson
                );
            }

            if ("POST".equals(method) && path.matches("/api/videos/\\d+/comments")) {
                Long userId = requireUserId(request, authService);
                if (userId == null) {
                    return LocalApiResult.from(ApiResponse.error(401, "未登录"), gson);
                }
                long videoId = parsePathLong(path, "/api/videos/", "/comments");
                PostCommentRequest body = readJsonBody(request, gson, PostCommentRequest.class);
                String content = body != null ? body.content : "";
                return LocalApiResult.from(
                        commentService.postComment(userId, videoId, content),
                        gson
                );
            }

            if ("GET".equals(method) && path.matches("/api/users/\\d+/videos")) {
                long targetUserId = parsePathLong(path, "/api/users/", "/videos");
                int page = parseQueryInt(request, "page", 0);
                int size = parseQueryInt(request, "size", 10);
                Long currentUserId = optionalUserId(request, authService);
                return LocalApiResult.from(
                        videoService.getUserVideos(targetUserId, page, size, currentUserId),
                        gson
                );
            }

            return LocalApiResult.from(ApiResponse.error(404, "接口不存在: " + path), gson);
        } catch (Exception e) {
            return LocalApiResult.from(
                    ApiResponse.error(500, e.getMessage() != null ? e.getMessage() : "服务器错误"),
                    gson
            );
        }
    }

    private static String normalizePath(String path) {
        if (TextUtils.isEmpty(path)) {
            return "/";
        }
        if (!path.startsWith("/")) {
            return "/" + path;
        }
        return path;
    }

    private static <T> T readJsonBody(Request request, Gson gson, Class<T> clazz) throws IOException {
        RequestBody body = request.body();
        if (body == null) {
            return null;
        }
        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        String json = buffer.readString(StandardCharsets.UTF_8);
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        return gson.fromJson(json, clazz);
    }

    private static Long requireUserId(Request request, LocalAuthService authService) {
        Long userId = optionalUserId(request, authService);
        if (userId == null || userId <= 0) {
            return null;
        }
        return userId;
    }

    private static Long optionalUserId(Request request, LocalAuthService authService) {
        String header = request.header("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return authService.resolveUserId(header.substring(7).trim());
    }

    private static int parseQueryInt(Request request, String name, int defaultValue) {
        String value = request.url().queryParameter(name);
        if (TextUtils.isEmpty(value)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parsePathLong(String path, String prefix, String suffix) {
        String middle = path.substring(prefix.length(), path.length() - suffix.length());
        return Long.parseLong(middle);
    }

    private static PublishParts parseMultipart(Context context, Request request) throws IOException {
        RequestBody body = request.body();
        if (body == null) {
            return null;
        }

        File videoFile = null;
        File coverFile = null;
        String description = "";

        Buffer buffer = new Buffer();
        body.writeTo(buffer);
        ResponseBody responseBody = ResponseBody.create(body.contentType(), buffer.readByteString());

        try (MultipartReader reader = new MultipartReader(responseBody)) {
            MultipartReader.Part part;
            while ((part = reader.nextPart()) != null) {
                Headers headers = part.headers();
                String disposition = headers.get("Content-Disposition");
                if (disposition == null) {
                    part.close();
                    continue;
                }

                if (disposition.contains("name=\"video\"")) {
                    File cacheDir = new File(context.getCacheDir(), "uploads");
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs();
                    }
                    videoFile = File.createTempFile("upload_", ".mp4", cacheDir);
                    try (InputStream input = part.body().inputStream();
                         OutputStream output = new FileOutputStream(videoFile)) {
                        byte[] dataBuffer = new byte[8192];
                        int read;
                        while ((read = input.read(dataBuffer)) != -1) {
                            output.write(dataBuffer, 0, read);
                        }
                    }
                } else if (disposition.contains("name=\"cover\"")) {
                    File cacheDir = new File(context.getCacheDir(), "uploads");
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs();
                    }
                    coverFile = File.createTempFile("cover_", ".jpg", cacheDir);
                    try (InputStream input = part.body().inputStream();
                         OutputStream output = new FileOutputStream(coverFile)) {
                        byte[] dataBuffer = new byte[8192];
                        int read;
                        while ((read = input.read(dataBuffer)) != -1) {
                            output.write(dataBuffer, 0, read);
                        }
                    }
                } else if (disposition.contains("name=\"description\"")) {
                    description = part.body().readUtf8();
                }
                part.close();
            }
        }

        PublishParts parts = new PublishParts();
        parts.videoFile = videoFile;
        parts.coverFile = coverFile;
        parts.description = description;
        return parts;
    }

    private static final class PublishParts {
        File videoFile;
        File coverFile;
        String description;
    }
}
