package com.example.douyin.local;

import android.text.TextUtils;

import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.network.model.UserDto;

public final class EntityMapper {

    private EntityMapper() {
    }

    public static UserDto toUserDto(UserEntity entity) {
        UserDto dto = new UserDto();
        dto.id = entity.id;
        dto.username = entity.username;
        dto.nickname = entity.nickname;
        dto.createdAt = entity.createdAt;
        if (!TextUtils.isEmpty(entity.avatarPath)) {
            dto.avatarUrl = "file://" + entity.avatarPath;
        }
        return dto;
    }

    public static String toVideoUrl(String filePath) {
        if (TextUtils.isEmpty(filePath)) {
            return "";
        }
        if (filePath.startsWith("http://") || filePath.startsWith("https://")
                || filePath.startsWith("file://")) {
            return filePath;
        }
        return "file://" + filePath;
    }
}
