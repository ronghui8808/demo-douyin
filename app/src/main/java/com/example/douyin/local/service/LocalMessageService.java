package com.example.douyin.local.service;

import com.example.douyin.local.EntityMapper;
import com.example.douyin.local.db.ConversationDao;
import com.example.douyin.local.db.FollowDao;
import com.example.douyin.local.db.MessageDao;
import com.example.douyin.local.db.UserDao;
import com.example.douyin.local.db.entity.ConversationEntity;
import com.example.douyin.local.db.entity.MessageEntity;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.ConversationDto;
import com.example.douyin.network.model.MessageDto;
import com.example.douyin.network.model.UserDto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LocalMessageService {

    private static final int PREVIEW_MAX = 50;

    private final UserDao userDao;
    private final FollowDao followDao;
    private final ConversationDao conversationDao;
    private final MessageDao messageDao;

    public LocalMessageService(
            UserDao userDao,
            FollowDao followDao,
            ConversationDao conversationDao,
            MessageDao messageDao) {
        this.userDao = userDao;
        this.followDao = followDao;
        this.conversationDao = conversationDao;
        this.messageDao = messageDao;
    }

    public ApiResponse<MessageDto> send(long fromUserId, long toUserId, String content) {
        if (fromUserId == toUserId) {
            return ApiResponse.error(400, "不能发给自己");
        }
        if (content == null || content.trim().isEmpty()) {
            return ApiResponse.error(400, "消息不能为空");
        }
        content = content.trim();
        UserEntity peer = userDao.findById(toUserId);
        if (peer == null) {
            return ApiResponse.error(404, "用户不存在");
        }

        long low = Math.min(fromUserId, toUserId);
        long high = Math.max(fromUserId, toUserId);
        ConversationEntity conv = conversationDao.findByPair(low, high);
        if (conv == null) {
            if (followDao.find(fromUserId, toUserId) == null) {
                return ApiResponse.error(403, "关注后才能发私信");
            }
            conv = new ConversationEntity();
            conv.userLowId = low;
            conv.userHighId = high;
            conv.createdAt = System.currentTimeMillis();
            conv.lastMessageAt = conv.createdAt;
            conv.lastMessagePreview = preview(content);
            conv.id = conversationDao.insert(conv);
        }

        long now = System.currentTimeMillis();
        MessageEntity msg = new MessageEntity();
        msg.conversationId = conv.id;
        msg.senderId = fromUserId;
        msg.content = content;
        msg.createdAt = now;
        msg.readAt = null;
        msg.id = messageDao.insert(msg);

        conv.lastMessageAt = now;
        conv.lastMessagePreview = preview(content);
        conversationDao.update(conv);
        return ApiResponse.ok(toMessageDto(msg));
    }

    public ApiResponse<List<MessageDto>> listMessages(long viewerId, long peerUserId) {
        long low = Math.min(viewerId, peerUserId);
        long high = Math.max(viewerId, peerUserId);
        ConversationEntity conv = conversationDao.findByPair(low, high);
        if (conv == null) {
            return ApiResponse.ok(Collections.<MessageDto>emptyList());
        }
        long now = System.currentTimeMillis();
        messageDao.markRead(conv.id, viewerId, now);
        List<MessageEntity> rows = messageDao.listByConversation(conv.id);
        List<MessageDto> list = new ArrayList<>(rows.size());
        for (MessageEntity row : rows) {
            list.add(toMessageDto(row));
        }
        return ApiResponse.ok(list);
    }

    public ApiResponse<List<ConversationDto>> listConversations(long userId) {
        List<ConversationEntity> rows = conversationDao.listForUser(userId);
        List<ConversationDto> list = new ArrayList<>(rows.size());
        for (ConversationEntity row : rows) {
            long peerId = row.userLowId == userId ? row.userHighId : row.userLowId;
            UserEntity peer = userDao.findById(peerId);
            if (peer == null) {
                continue;
            }
            ConversationDto dto = new ConversationDto();
            dto.conversationId = row.id;
            UserDto peerDto = EntityMapper.toUserDto(peer);
            peerDto.phone = null;
            dto.peer = peerDto;
            dto.lastPreview = row.lastMessagePreview;
            dto.lastAt = row.lastMessageAt;
            dto.unreadCount = messageDao.countUnread(row.id, userId);
            list.add(dto);
        }
        return ApiResponse.ok(list);
    }

    private static String preview(String content) {
        if (content.length() <= PREVIEW_MAX) {
            return content;
        }
        return content.substring(0, PREVIEW_MAX);
    }

    private static MessageDto toMessageDto(MessageEntity entity) {
        MessageDto dto = new MessageDto();
        dto.id = entity.id;
        dto.conversationId = entity.conversationId;
        dto.senderId = entity.senderId;
        dto.content = entity.content;
        dto.createdAt = entity.createdAt;
        dto.readAt = entity.readAt;
        return dto;
    }
}
