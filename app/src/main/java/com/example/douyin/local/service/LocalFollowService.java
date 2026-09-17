package com.example.douyin.local.service;

import com.example.douyin.local.EntityMapper;
import com.example.douyin.local.db.FollowDao;
import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.entity.FollowEntity;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.MatchPhonesResult;
import com.example.douyin.network.model.PhoneMatchItem;
import com.example.douyin.network.model.UserDto;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class LocalFollowService {

    private final UserDao userDao;
    private final FollowDao followDao;

    public LocalFollowService(UserDao userDao, FollowDao followDao) {
        this.userDao = userDao;
        this.followDao = followDao;
    }

    public ApiResponse<MatchPhonesResult> matchPhones(long viewerId, List<String> phones) {
        MatchPhonesResult result = new MatchPhonesResult();
        result.matches = new ArrayList<>();
        if (phones == null || phones.isEmpty()) {
            return ApiResponse.ok(result);
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String phone : phones) {
            if (phone != null && !phone.isEmpty()) {
                unique.add(phone);
            }
        }
        if (unique.isEmpty()) {
            return ApiResponse.ok(result);
        }

        List<UserEntity> users = userDao.findByPhones(new ArrayList<>(unique));
        for (UserEntity user : users) {
            if (user.id == viewerId) {
                continue;
            }
            PhoneMatchItem item = new PhoneMatchItem();
            item.matchedPhone = user.phone;
            UserDto dto = EntityMapper.toUserDto(user);
            dto.phone = null;
            item.user = dto;
            item.following = isFollowing(viewerId, user.id);
            result.matches.add(item);
        }
        return ApiResponse.ok(result);
    }

    public ApiResponse<Boolean> follow(long followerId, long followeeId) {
        if (followerId == followeeId) {
            return ApiResponse.error(400, "不能关注自己");
        }
        if (userDao.findById(followeeId) == null) {
            return ApiResponse.error(404, "用户不存在");
        }
        if (followDao.find(followerId, followeeId) == null) {
            FollowEntity entity = new FollowEntity();
            entity.followerId = followerId;
            entity.followeeId = followeeId;
            entity.createdAt = System.currentTimeMillis();
            followDao.insert(entity);
        }
        return ApiResponse.ok(Boolean.TRUE);
    }

    public ApiResponse<Boolean> unfollow(long followerId, long followeeId) {
        followDao.delete(followerId, followeeId);
        return ApiResponse.ok(Boolean.TRUE);
    }

    public ApiResponse<List<UserDto>> listFollowing(long followerId) {
        List<FollowEntity> rows = followDao.listByFollower(followerId);
        List<UserDto> list = new ArrayList<>();
        for (FollowEntity row : rows) {
            UserEntity user = userDao.findById(row.followeeId);
            if (user != null) {
                UserDto dto = EntityMapper.toUserDto(user);
                dto.phone = null;
                list.add(dto);
            }
        }
        return ApiResponse.ok(list);
    }

    public int countFollowing(long followerId) {
        return followDao.countByFollower(followerId);
    }

    public boolean isFollowing(long followerId, long followeeId) {
        return followDao.find(followerId, followeeId) != null;
    }
}
