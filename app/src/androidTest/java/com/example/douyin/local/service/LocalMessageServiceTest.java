package com.example.douyin.local.service;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.douyin.local.db.AppDatabase;
import com.example.douyin.local.db.entity.UserEntity;
import com.example.douyin.network.model.ApiResponse;
import com.example.douyin.network.model.ConversationDto;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class LocalMessageServiceTest {

    private AppDatabase db;
    private LocalMessageService messageService;
    private LocalFollowService followService;
    private long userA;
    private long userB;
    private long userC;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = AppDatabase.createInMemory(context);
        followService = new LocalFollowService(db.userDao(), db.followDao());
        messageService = new LocalMessageService(
                db.userDao(), db.followDao(), db.conversationDao(), db.messageDao());
        userA = insertUser("13900000001", "A");
        userB = insertUser("13900000002", "B");
        userC = insertUser("13900000003", "C");
        followService.follow(userA, userB);
    }

    @After
    public void tearDown() {
        db.close();
    }

    @Test
    public void send_requiresFollow_whenNoConversation() {
        assertEquals(403, messageService.send(userA, userC, "hi").code); // A not following C
        assertEquals(0, messageService.send(userA, userB, "hello").code);
    }

    @Test
    public void send_allowsReplyWithoutFollow_whenConversationExists() {
        assertEquals(0, messageService.send(userA, userB, "hi").code);
        // B does not follow A
        assertEquals(0, messageService.send(userB, userA, "reply").code);
    }

    @Test
    public void send_rejectsSelfAndBlank() {
        assertEquals(400, messageService.send(userA, userA, "x").code);
        assertEquals(400, messageService.send(userA, userB, "  ").code);
    }

    @Test
    public void samePair_singleConversation() {
        messageService.send(userA, userB, "1");
        messageService.send(userA, userB, "2");
        assertEquals(1, db.conversationDao().listForUser(userA).size());
    }

    @Test
    public void listMessages_marksRead_andClearsUnread() {
        messageService.send(userA, userB, "hi");
        ApiResponse<List<ConversationDto>> before = messageService.listConversations(userB);
        assertEquals(1, before.data.get(0).unreadCount);
        messageService.listMessages(userB, userA);
        ApiResponse<List<ConversationDto>> after = messageService.listConversations(userB);
        assertEquals(0, after.data.get(0).unreadCount);
    }

    @Test
    public void listConversations_orderedByLastAtDesc() throws InterruptedException {
        messageService.send(userA, userB, "first");
        // insert follow A->C then message later
        followService.follow(userA, userC);
        Thread.sleep(5);
        messageService.send(userA, userC, "second");
        List<ConversationDto> list = messageService.listConversations(userA).data;
        assertEquals(userC, list.get(0).peer.id);
        assertEquals(userB, list.get(1).peer.id);
    }

    private long insertUser(String phone, String nick) {
        UserEntity u = new UserEntity();
        u.username = phone;
        u.passwordHash = "";
        u.nickname = nick;
        u.phone = phone;
        u.createdAt = System.currentTimeMillis();
        return db.userDao().insert(u);
    }
}
