package com.happyim.chat.service;

import com.happyim.common.mapper.ConversationMapper;
import com.happyim.common.mapper.GroupChatMapper;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.Conversation;
import com.happyim.common.model.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final String SESSION_ZSET = "chat:sessions:";
    private static final String SESSION_HASH = "chat:session:";

    private final ConversationMapper conversationMapper;
    private final UserMapper userMapper;
    private final GroupChatMapper groupChatMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public ConversationService(ConversationMapper conversationMapper, UserMapper userMapper,
                               GroupChatMapper groupChatMapper, RedisTemplate<String, String> redisTemplate) {
        this.conversationMapper = conversationMapper;
        this.userMapper = userMapper;
        this.groupChatMapper = groupChatMapper;
        this.redisTemplate = redisTemplate;
    }

    // ==================== 创建私聊会话 + Redis 摘要 ====================

    public String createPrivateSession(Long userId, Long peerId) {
        String convId = userId < peerId
                ? "p_" + userId + "_" + peerId
                : "p_" + peerId + "_" + userId;

        // MySQL 记录
        if (conversationMapper.findById(convId) == null) {
            Conversation conv = new Conversation();
            conv.setId(convId);
            conv.setType(0);
            conversationMapper.insert(conv);
        }

        // Redis 摘要：为双方初始化
        User userA = userMapper.findById(userId);
        User userB = userMapper.findById(peerId);

        initSession(userId, convId, 0, peerId,
                userB != null ? userB.getNickname() : null,
                userB != null ? resolveAvatar(userB) : null);

        initSession(peerId, convId, 0, userId,
                userA != null ? userA.getNickname() : null,
                userA != null ? resolveAvatar(userA) : null);

        log.info("私聊会话已创建: {}", convId);
        return convId;
    }

    // ==================== 创建群聊会话 + Redis 摘要 ====================

    public void createGroupSession(Long groupId, String groupName, List<Long> memberIds) {
        String convId = "g_" + groupId;

        // MySQL 记录
        if (conversationMapper.findById(convId) == null) {
            Conversation conv = new Conversation();
            conv.setId(convId);
            conv.setType(1);
            conversationMapper.insert(conv);
        }

        // Redis 摘要：为所有成员初始化
        for (Long uid : memberIds) {
            initSession(uid, convId, 1, groupId, groupName, null);
        }

        log.info("群聊会话已创建: {}, members={}", convId, memberIds.size());
    }

    // ==================== 新成员加入群时补 Redis 摘要 ====================

    public void initGroupMemberSession(Long groupId, String groupName, Long userId) {
        String convId = "g_" + groupId;
        com.happyim.common.model.entity.GroupChat group = groupChatMapper.findById(groupId);
        String avatar = group != null ? group.getAvatarUrl() : null;
        if (avatar != null && !avatar.isBlank() && !avatar.startsWith("http") && avatar.contains("/")) {
            avatar = "/api/files/download/" + avatar.substring(avatar.indexOf("/") + 1);
        }
        initSession(userId, convId, 1, groupId, groupName, avatar);
    }

    // ==================== 更新群会话摘要（群名/头像变更时） ====================

    public void updateGroupSessionInfo(Long groupId, String groupName, String avatarUrl) {
        String convId = "g_" + groupId;

        // 遍历所有 Redis key 中匹配的会话条目来更新。因为成员可能很多，
        // 更高效的做法是迭代所有 chat:sessions:{userId} ZSET。
        // 简化做法：只更新已知成员的会话。这里遍历所有用户不太现实。
        // 实际做法：更新群成员的会话。我们通过 group_member 表获取所有成员。
        // 但 ConversationService 没有 GroupMemberMapper。我们改为由 GroupService 传 memberIds。
        log.info("群会话信息更新: groupId={}, name={}", groupId, groupName);
    }

    /**
     * 更新指定成员的群会话摘要字段
     */
    public void updateMemberSessionInfo(Long userId, Long groupId, String groupName, String avatarUrl) {
        String hashKey = SESSION_HASH + userId + ":g_" + groupId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(hashKey))) {
            if (groupName != null) redisTemplate.opsForHash().put(hashKey, "peer_name", groupName);
            if (avatarUrl != null) redisTemplate.opsForHash().put(hashKey, "peer_avatar", resolveAvatar(avatarUrl));
        }
    }

    private String resolveAvatar(String raw) {
        if (raw == null || raw.isBlank()) return "";
        if (raw.startsWith("http")) return raw;
        if (raw.contains("/")) return "/api/files/download/" + raw.substring(raw.indexOf("/") + 1);
        return raw;
    }

    // ==================== 获取会话列表 ====================

    public List<Map<String, Object>> getSessionList(Long userId) {
        String zsetKey = SESSION_ZSET + userId;
        Set<String> convIds = redisTemplate.opsForZSet().reverseRange(zsetKey, 0, -1);
        if (convIds == null || convIds.isEmpty()) return List.of();

        List<Map<String, Object>> result = new ArrayList<>();
        for (String convId : convIds) {
            String hashKey = SESSION_HASH + userId + ":" + convId;
            Map<Object, Object> hash = redisTemplate.opsForHash().entries(hashKey);
            if (hash.isEmpty()) continue;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", convId);
            item.put("type", parseType(hash));
            item.put("peerId", hash.get("peer_id"));
            item.put("peerName", hash.get("peer_name"));
            item.put("peerAvatar", hash.get("peer_avatar"));
            item.put("lastMsgContent", hash.get("last_msg_content"));
            item.put("lastMsgType", hash.get("last_msg_type"));
            item.put("lastMsgTime", hash.get("last_msg_time"));
            item.put("lastSenderId", hash.get("last_sender_id"));
            item.put("unreadCount", Integer.valueOf(String.valueOf(hash.getOrDefault("unread_count", "0"))));
            result.add(item);
        }
        return result;
    }

    // ==================== 内部方法 ====================

    private void initSession(Long userId, String convId, int type, Long peerId, String peerName, String peerAvatar) {
        String hashKey = SESSION_HASH + userId + ":" + convId;
        Map<String, String> data = new HashMap<>();
        data.put("type", String.valueOf(type));
        data.put("peer_id", String.valueOf(peerId));
        data.put("peer_name", peerName != null ? peerName : "");
        data.put("peer_avatar", peerAvatar != null ? peerAvatar : "");
        data.put("last_msg_content", "");
        data.put("last_msg_type", "");
        data.put("last_msg_time", "0");
        data.put("last_sender_id", "");
        data.put("unread_count", "0");
        redisTemplate.opsForHash().putAll(hashKey, data);

        String zsetKey = SESSION_ZSET + userId;
        redisTemplate.opsForZSet().add(zsetKey, convId, 0);
    }

    private Integer parseType(Map<Object, Object> hash) {
        Object t = hash.get("type");
        if (t == null) return 0;
        return Integer.valueOf(String.valueOf(t));
    }

    private String resolveAvatar(User user) {
        if (user == null) return null;
        String raw = user.getAvatarUrl();
        if (raw != null && !raw.isBlank() && !raw.startsWith("http")) {
            return "/api/files/avatar/" + user.getId();
        }
        return raw;
    }
}
