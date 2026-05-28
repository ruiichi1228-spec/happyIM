package com.happyim.api.service;

import com.happyim.common.mapper.BlacklistMapper;
import com.happyim.common.mapper.FriendMapper;
import com.happyim.common.mapper.GroupChatMapper;
import com.happyim.common.mapper.GroupMemberMapper;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.Friend;
import com.happyim.common.model.entity.GroupMember;
import com.happyim.common.service.MessageFilterChain;
import com.happyim.common.service.MessageIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageService Unit Tests")
class MessageServiceTest {

    @Mock private MongoTemplate mongoTemplate;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private MessageIdGenerator idGenerator;
    @Mock private MessageFilterChain filterChain;
    @Mock private FriendMapper friendMapper;
    @Mock private BlacklistMapper blacklistMapper;
    @Mock private GroupChatMapper groupChatMapper;
    @Mock private GroupMemberMapper groupMemberMapper;
    @Mock private UserMapper userMapper;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;

    private MessageService messageService;

    @BeforeEach
    void setUp() {
        messageService = new MessageService(mongoTemplate, rabbitTemplate,
                idGenerator, filterChain, friendMapper, blacklistMapper, groupChatMapper, groupMemberMapper,
                userMapper, redisTemplate);
        ReflectionTestUtils.setField(messageService, "exchange", "chat.exchange");
        ReflectionTestUtils.setField(messageService, "routingKey", "ws.ws-1");
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Nested
    @DisplayName("sendMessage - 私聊")
    class PrivateChat {

        @Test
        @DisplayName("should send message successfully")
        void shouldSendMessage() {
            when(filterChain.execute("hello")).thenReturn("hello");
            when(idGenerator.generate("p_10001_10002", 0)).thenReturn("BD8U-AAAA-BBBB-CCCC");
            when(friendMapper.findByPair(10001L, 10002L)).thenReturn(new Friend());
            when(blacklistMapper.findByPair(10002L, 10001L)).thenReturn(null);

            Map<String, Object> result = messageService.sendMessage(
                    10001L, "p_10001_10002", 0, "hello", "text", null);

            assertNotNull(result.get("messageId"));
            assertEquals("BD8U-AAAA-BBBB-CCCC", result.get("messageId"));
            assertNotNull(result.get("createdAt"));
            verify(mongoTemplate).insert(any(Map.class), eq("messages"));
            verify(rabbitTemplate).convertAndSend(eq("chat.exchange"), eq("ws.ws-1"), any(Map.class));
        }

        @Test
        @DisplayName("should reject if not participant")
        void shouldRejectIfNotParticipant() {
            assertThrows(IllegalArgumentException.class, () ->
                    messageService.sendMessage(99999L, "p_10001_10002", 0, "hello", "text", null));
        }

        @Test
        @DisplayName("should reject if not friend")
        void shouldRejectIfNotFriend() {
            when(friendMapper.findByPair(10001L, 10002L)).thenReturn(null);
            assertThrows(IllegalArgumentException.class, () ->
                    messageService.sendMessage(10001L, "p_10001_10002", 0, "hello", "text", null));
        }

        @Test
        @DisplayName("should reject if blocked")
        void shouldRejectIfBlocked() {
            when(friendMapper.findByPair(10001L, 10002L)).thenReturn(new Friend());
            when(blacklistMapper.findByPair(10002L, 10001L)).thenReturn(new com.happyim.common.model.entity.Blacklist());
            assertThrows(IllegalArgumentException.class, () ->
                    messageService.sendMessage(10001L, "p_10001_10002", 0, "hello", "text", null));
        }

        @Test
        @DisplayName("should filter content through filter chain")
        void shouldFilterContent() {
            when(filterChain.execute("bad word")).thenReturn("*** word");
            when(idGenerator.generate("p_10001_10002", 0)).thenReturn("ID-1");
            when(friendMapper.findByPair(10001L, 10002L)).thenReturn(new Friend());
            when(blacklistMapper.findByPair(10002L, 10001L)).thenReturn(null);

            Map<String, Object> result = messageService.sendMessage(
                    10001L, "p_10001_10002", 0, "bad word", "text", null);

            verify(mongoTemplate).insert(argThat((Map m) ->
                    "*** word".equals(m.get("content"))), eq("messages"));
        }
    }

    @Nested
    @DisplayName("sendMessage - 群聊")
    class GroupChat {

        @Test
        @DisplayName("should send group message")
        void shouldSendGroupMessage() {
            GroupMember member = new GroupMember();
            member.setUserId(10001L);
            member.setRole(1);

            when(filterChain.execute("hello")).thenReturn("hello");
            when(idGenerator.generate("g_10000", 1)).thenReturn("GRP1-AAAA-BBBB-CCCC");
            when(groupMemberMapper.findByGroupAndUser(10000L, 10001L)).thenReturn(member);

            Map<String, Object> result = messageService.sendMessage(
                    10001L, "g_10000", 1, "hello", "text", null);

            assertNotNull(result.get("messageId"));
        }

        @Test
        @DisplayName("should reject if not group member")
        void shouldRejectIfNotMember() {
            when(groupMemberMapper.findByGroupAndUser(10000L, 10001L)).thenReturn(null);
            assertThrows(IllegalArgumentException.class, () ->
                    messageService.sendMessage(10001L, "g_10000", 1, "hello", "text", null));
        }
    }
}
