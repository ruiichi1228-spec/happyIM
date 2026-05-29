package com.happyim.chat.service;

import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.mapper.BlacklistMapper;
import com.happyim.common.mapper.FriendMapper;
import com.happyim.common.mapper.GroupChatMapper;
import com.happyim.common.mapper.GroupMemberMapper;
import com.happyim.common.model.entity.GroupChat;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.GroupMember;
import com.happyim.common.model.entity.User;
import com.happyim.common.service.MessageFilterChain;
import com.happyim.common.service.MessageIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);
    private static final String SESSION_PREFIX = "chat:session:";
    private static final String SESSION_ZSET = "chat:sessions:";

    private final MongoTemplate mongoTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final MessageIdGenerator idGenerator;
    private final MessageFilterChain filterChain;
    private final FriendMapper friendMapper;
    private final BlacklistMapper blacklistMapper;
    private final GroupChatMapper groupChatMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${happyim.mq.exchange}")
    private String exchange;

    @Value("${happyim.mq.routing-key}")
    private String routingKey;

    public MessageService(MongoTemplate mongoTemplate, RabbitTemplate rabbitTemplate,
                          MessageIdGenerator idGenerator, MessageFilterChain filterChain,
                          FriendMapper friendMapper, BlacklistMapper blacklistMapper,
                          GroupChatMapper groupChatMapper, GroupMemberMapper groupMemberMapper,
                          UserMapper userMapper,
                          RedisTemplate<String, String> redisTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.idGenerator = idGenerator;
        this.filterChain = filterChain;
        this.friendMapper = friendMapper;
        this.blacklistMapper = blacklistMapper;
        this.groupChatMapper = groupChatMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
    }

    // ==================== 发送消息 ====================

    public Map<String, Object> sendMessage(Long fromUserId, String conversationId, int convType,
                                            String content, String messageType, Map<String, Object> extra) {
        validateParticipant(fromUserId, conversationId, convType);

        // 群聊检查群是否已解散
        if (convType == 1) {
            long groupId = Long.parseLong(conversationId.substring(2));
            GroupChat group = groupChatMapper.findById(groupId);
            if (group == null || group.getStatus() == 1) {
                throw new BizException(ErrorCode.NOT_FOUND, "群组不存在或已解散");
            }
        }

        String filtered = "text".equals(messageType) ? filterChain.execute(content) : content;
        String messageId = idGenerator.generate(conversationId, convType);
        long now = System.currentTimeMillis();

        Map<String, Object> msgDoc = new LinkedHashMap<>();
        msgDoc.put("messageId", messageId);
        msgDoc.put("conversationId", conversationId);
        msgDoc.put("conversationType", convType);
        msgDoc.put("fromUserId", fromUserId);
        msgDoc.put("content", filtered);
        msgDoc.put("messageType", messageType);
        msgDoc.put("createdAt", now);

        if (extra != null) {
            if (extra.containsKey("fileName")) msgDoc.put("fileName", extra.get("fileName"));
            if (extra.containsKey("fileSize")) msgDoc.put("fileSize", extra.get("fileSize"));
            if (extra.containsKey("duration")) msgDoc.put("duration", extra.get("duration"));
            if (extra.containsKey("quoteMessageId")) {
                String qid = (String) extra.get("quoteMessageId");
                msgDoc.put("quoteMessageId", qid);
                // 查找引用消息的内容
                Map quoted = mongoTemplate.findOne(new Query(Criteria.where("messageId").is(qid)), Map.class, "messages");
                if (quoted != null) {
                    msgDoc.put("quoteContent", quoted.get("content"));
                    msgDoc.put("quoteSenderId", quoted.get("fromUserId"));
                    msgDoc.put("quoteMessageType", quoted.get("messageType"));
                }
            }
        }

        mongoTemplate.insert(msgDoc, "messages");

        // 写 feed + 更新接收者未读数 + 更新所有参与者的会话摘要
        if (convType == 0) {
            writePrivateFeeds(fromUserId, conversationId, messageId, now);
            String[] parts = conversationId.substring(2).split("_");
            long a = Long.parseLong(parts[0]), b = Long.parseLong(parts[1]);
            updateSessionLastMsg(a, conversationId, content, messageType, now);
            updateSessionLastMsg(b, conversationId, content, messageType, now);
        } else {
            writeGroupFeeds(fromUserId, conversationId, messageId, now);
            long groupId = Long.parseLong(conversationId.substring(2));
            for (GroupMember m : groupMemberMapper.findByGroupId(groupId)) {
                updateSessionLastMsg(m.getUserId(), conversationId, content, messageType, now);
            }
        }
        if (extra != null && extra.containsKey("fileName")) {
            long fileSize = extra.containsKey("fileSize") ? ((Number) extra.get("fileSize")).longValue() : 0;
            try {
                Map<String, Object> fileEvent = new LinkedHashMap<>();
                fileEvent.put("type", "file_record");
                fileEvent.put("userId", fromUserId);
                fileEvent.put("senderId", fromUserId);
                fileEvent.put("conversationId", conversationId);
                fileEvent.put("conversationType", convType);
                fileEvent.put("fileName", extra.get("fileName"));
                fileEvent.put("fileSize", fileSize);
                fileEvent.put("fileUrl", filtered);
                fileEvent.put("messageType", messageType);
                fileEvent.put("messageId", messageId);
                rabbitTemplate.convertAndSend(exchange, "file.record", fileEvent);
            } catch (Exception e) {
                log.warn("投递 file.record 事件失败: {}", e.getMessage());
            }
        }

        // 投递 MQ
        try {
            Map<String, Object> mqPayload = new LinkedHashMap<>(msgDoc);
            if (convType == 1) {
                long groupId = Long.parseLong(conversationId.substring(2));
                List<Long> memberIds = groupMemberMapper.findByGroupId(groupId).stream()
                        .map(GroupMember::getUserId).toList();
                mqPayload.put("members", memberIds);
            }
            rabbitTemplate.convertAndSend(exchange, routingKey, mqPayload);
        } catch (Exception e) {
            log.warn("MQ投递失败: {}", e.getMessage());
        }

        log.info("消息已发送: id={}, conv={}, from={}", messageId, conversationId, fromUserId);
        return Map.of("messageId", messageId, "createdAt", now);
    }

    // ==================== 消息历史（偏移分页） ====================

    public Map<String, Object> getMessages(Long userId, String conversationId, int offset, int limit) {
        Query feedQuery = new Query()
                .addCriteria(Criteria.where("userId").is(userId)
                        .and("conversationId").is(conversationId));
        long total = mongoTemplate.count(feedQuery, "message_feed");

        feedQuery.with(Sort.by(Sort.Direction.DESC, "messageId")).skip(offset).limit(limit);
        List<Map> feedEntries = mongoTemplate.find(feedQuery, Map.class, "message_feed");
        if (feedEntries.isEmpty()) {
            return Map.of("list", List.of(), "total", total, "hasMore", offset + limit < total);
        }

        List<String> msgIds = feedEntries.stream().map(e -> (String) e.get("messageId")).toList();
        Query msgQuery = new Query(Criteria.where("messageId").in(msgIds));
        List<Map> rawMessages = mongoTemplate.find(msgQuery, Map.class, "messages");

        // 按 messageId 升序排列，保证前端显示 [旧→新]
        rawMessages.sort((a, b) -> String.valueOf(a.get("messageId")).compareTo(String.valueOf(b.get("messageId"))));

        return Map.of("list", rawMessages, "total", total, "hasMore", offset + limit < total);
    }

    // ==================== 会话列表 ====================

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getConversationList(Long userId) {
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").is(userId)),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "messageId")),
                Aggregation.group("conversationId")
                        .first("messageId").as("lastMessageId")
                        .first("conversationId").as("convId")
        );
        AggregationResults<Map> results = mongoTemplate.aggregate(agg, "message_feed", Map.class);
        List<Map<String, Object>> list = new ArrayList<>();

        for (Map row : results.getMappedResults()) {
            String convId = (String) row.get("convId");
            String lastMsgId = (String) row.get("lastMessageId");

            Map msgDoc = mongoTemplate.findOne(
                    new Query(Criteria.where("messageId").is(lastMsgId)), Map.class, "messages");

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", convId);
            int type = convId.startsWith("g_") ? 1 : 0;
            item.put("type", type);

            if (type == 0) fillPrivateSession(userId, convId, item);
            else fillGroupSession(convId, item);

            if (msgDoc != null) {
                item.put("lastMsgContent", msgDoc.getOrDefault("content", ""));
                item.put("lastMsgType", msgDoc.getOrDefault("messageType", "text"));
                item.put("lastMsgTime", msgDoc.getOrDefault("createdAt", 0));
                item.put("lastSenderId", msgDoc.getOrDefault("fromUserId", ""));
            } else {
                item.put("lastMsgContent", "");
                item.put("lastMsgType", "");
                item.put("lastMsgTime", 0);
                item.put("lastSenderId", "");
            }

            // 未读数直接从 Redis hash 读取，不对比游标
            String hashKey = SESSION_PREFIX + userId + ":" + convId;
            Object unreadObj = redisTemplate.opsForHash().get(hashKey, "unread_count");
            int unread = 0;
            if (unreadObj != null) {
                try { unread = Integer.parseInt(unreadObj.toString()); } catch (NumberFormatException e) {}
            }
            item.put("unreadCount", unread);

            // 同步写回 Redis 缓存
            Map<String, String> redisHash = new HashMap<>();
            redisHash.put("type", String.valueOf(item.get("type")));
            redisHash.put("peer_id", String.valueOf(item.getOrDefault("peerId", "")));
            redisHash.put("peer_name", String.valueOf(item.getOrDefault("peerName", "")));
            redisHash.put("peer_avatar", String.valueOf(item.getOrDefault("peerAvatar", "")));
            redisHash.put("last_msg_content", String.valueOf(item.get("lastMsgContent")));
            redisHash.put("last_msg_time", String.valueOf(item.get("lastMsgTime")));
            redisHash.put("last_sender_id", String.valueOf(item.get("lastSenderId")));
            redisHash.put("unread_count", String.valueOf(unread));
            redisTemplate.opsForHash().putAll(hashKey, redisHash);

            Object lastTime = item.get("lastMsgTime");
            if (lastTime instanceof Number) {
                redisTemplate.opsForZSet().add(SESSION_ZSET + userId, convId, ((Number) lastTime).doubleValue());
            }

            list.add(item);
        }

        // 补充无消息但已初始化的会话
        Set<String> feedConvIds = list.stream().map(m -> (String) m.get("conversationId")).collect(java.util.stream.Collectors.toSet());
        Set<String> hashKeys = redisTemplate.keys(SESSION_PREFIX + userId + ":*");
        if (hashKeys != null) {
            for (String key : hashKeys) {
                String convId = key.substring((SESSION_PREFIX + userId + ":").length());
                if (feedConvIds.contains(convId)) continue;

                Map<Object, Object> hash = redisTemplate.opsForHash().entries(key);
                if (hash.isEmpty()) continue;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("conversationId", convId);
                item.put("type", Integer.valueOf(String.valueOf(hash.getOrDefault("type", "0"))));
                item.put("peerId", String.valueOf(hash.getOrDefault("peer_id", "")));
                item.put("peerName", String.valueOf(hash.getOrDefault("peer_name", "")));
                item.put("peerAvatar", String.valueOf(hash.getOrDefault("peer_avatar", "")));
                item.put("lastMsgContent", String.valueOf(hash.getOrDefault("last_msg_content", "")));
                item.put("lastMsgType", "");
                item.put("lastMsgTime", hash.getOrDefault("last_msg_time", "0"));
                item.put("lastSenderId", "");
                item.put("unreadCount", Integer.valueOf(String.valueOf(hash.getOrDefault("unread_count", "0"))));
                list.add(item);
            }
        }

        list.sort((a, b) -> {
            Object ta = a.get("lastMsgTime"), tb = b.get("lastMsgTime");
            long la = ta instanceof Number ? ((Number) ta).longValue() : Long.parseLong(String.valueOf(ta));
            long lb = tb instanceof Number ? ((Number) tb).longValue() : Long.parseLong(String.valueOf(tb));
            return Long.compare(lb, la);
        });

        return list;
    }

    // ==================== 已读 ====================

    public void markRead(Long userId, String conversationId) {
        String key = SESSION_PREFIX + userId + ":" + conversationId;
        redisTemplate.opsForHash().put(key, "unread_count", "0");
        // 同时记录游标：该会话最新的 messageId，之后可从这条开始拉新消息
        Query q = new Query(Criteria.where("userId").is(userId).and("conversationId").is(conversationId))
                .with(Sort.by(Sort.Direction.DESC, "messageId")).limit(1);
        List<Map> feeds = mongoTemplate.find(q, Map.class, "message_feed");
        if (!feeds.isEmpty()) {
            redisTemplate.opsForHash().put(key, "read_cursor", feeds.get(0).get("messageId").toString());
        }
    }

    // ==================== 系统消息 ====================

    public void sendSystemMessage(String conversationId, int convType, String content, String subType, String msgType) {
        String messageId = idGenerator.generate(conversationId, convType);
        long now = System.currentTimeMillis();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("messageId", messageId);
        doc.put("conversationId", conversationId);
        doc.put("conversationType", convType);
        doc.put("fromUserId", 0L);
        doc.put("content", content);
        doc.put("messageType", msgType);
        doc.put("systemType", subType);
        doc.put("createdAt", now);
        mongoTemplate.insert(doc, "messages");

        try {
            Map<String, Object> mqPayload = new LinkedHashMap<>(doc);
            if (convType == 1) {
                long groupId = Long.parseLong(conversationId.substring(2));
                List<Long> memberIds = groupMemberMapper.findByGroupId(groupId).stream()
                        .map(GroupMember::getUserId).toList();
                mqPayload.put("members", memberIds);
            }
            rabbitTemplate.convertAndSend(exchange, routingKey, mqPayload);
        } catch (Exception e) {
            log.warn("系统消息MQ投递失败: {}", e.getMessage());
        }

        if (convType == 1) {
            long groupId = Long.parseLong(conversationId.substring(2));
            for (GroupMember m : groupMemberMapper.findByGroupId(groupId)) {
                insertFeed(m.getUserId(), conversationId, messageId, now);
                updateSessionLastMsg(m.getUserId(), conversationId, content, msgType, now);
                incrementUnread(m.getUserId(), conversationId);
            }
        } else {
            String[] parts = conversationId.substring(2).split("_");
            long uid1 = Long.parseLong(parts[0]), uid2 = Long.parseLong(parts[1]);
            insertFeed(uid1, conversationId, messageId, now);
            insertFeed(uid2, conversationId, messageId, now);
            updateSessionLastMsg(uid1, conversationId, content, msgType, now);
            incrementUnread(uid1, conversationId);
            updateSessionLastMsg(uid2, conversationId, content, msgType, now);
            incrementUnread(uid2, conversationId);
        }
    }

    // ==================== 撤回 / 删除 ====================

    public void deleteMessage(Long userId, String messageId) {
        mongoTemplate.remove(
                new Query(Criteria.where("userId").is(userId).and("messageId").is(messageId)),
                "message_feed");
    }

    public void recallMessage(Long userId, String messageId) {
        Query q = new Query(Criteria.where("messageId").is(messageId));
        Map msg = mongoTemplate.findOne(q, Map.class, "messages");
        if (msg == null) throw new BizException(ErrorCode.NOT_FOUND, "消息不存在");

        long fromUserId = toLong(msg.get("fromUserId"));
        if (fromUserId != userId) throw new BizException(ErrorCode.FORBIDDEN, "只能撤回自己的消息");

        long createdAt = toLong(msg.get("createdAt"));
        if (System.currentTimeMillis() - createdAt > 120_000) {
            throw new BizException(ErrorCode.FORBIDDEN, "超过2分钟的消息无法撤回");
        }

        org.springframework.data.mongodb.core.query.Update u = new org.springframework.data.mongodb.core.query.Update();
        u.set("messageType", "recall");
        u.set("content", "消息已被撤回");
        mongoTemplate.updateFirst(q, u, "messages");

        try {
            String convId = (String) msg.get("conversationId");
            int convType = toInt(msg.get("conversationType"));
            Map<String, Object> mqPayload = new LinkedHashMap<>();
            mqPayload.put("messageId", messageId);
            mqPayload.put("conversationId", convId);
            mqPayload.put("conversationType", convType);
            mqPayload.put("fromUserId", fromUserId);
            mqPayload.put("messageType", "recall");
            mqPayload.put("content", "消息已被撤回");
            mqPayload.put("createdAt", System.currentTimeMillis());
            if (convType == 1) {
                long groupId = Long.parseLong(convId.substring(2));
                List<Long> memberIds = groupMemberMapper.findByGroupId(groupId).stream()
                        .map(GroupMember::getUserId).toList();
                mqPayload.put("members", memberIds);
            }
            rabbitTemplate.convertAndSend(exchange, routingKey, mqPayload);
        } catch (Exception e) {
            log.warn("撤回MQ投递失败: {}", e.getMessage());
        }
    }

    public void deleteConversation(Long userId, String conversationId) {
        redisTemplate.delete(SESSION_PREFIX + userId + ":" + conversationId);
        log.info("会话已删除: userId={}, convId={}", userId, conversationId);
    }

    public void clearHistory(Long userId, String conversationId) {
        mongoTemplate.remove(new Query(Criteria.where("userId").is(userId).and("conversationId").is(conversationId)), "message_feed");
        redisTemplate.delete(SESSION_PREFIX + userId + ":" + conversationId);
        log.info("聊天记录已清除: userId={}, convId={}", userId, conversationId);
    }

    // ==================== 搜索 ====================

    public Map<String, Object> searchMessages(Long userId, String conversationId, String type,
                                                String keyword, long since, int offset, int limit) {
        Criteria criteria = Criteria.where("conversationId").is(conversationId);
        if (type != null && !type.isEmpty() && !type.equals("all")) {
            if (type.equals("media")) criteria.and("messageType").in("image", "video");
            else criteria.and("messageType").is(type);
        }
        if (keyword != null && !keyword.isEmpty()) criteria.and("content").regex(keyword, "i");
        if (since > 0) criteria.and("createdAt").lte(since);
        Query query = new Query(criteria).with(Sort.by(Sort.Direction.DESC, "messageId")).skip(offset).limit(limit);
        List<Map> list = mongoTemplate.find(query, Map.class, "messages");
        long total = mongoTemplate.count(new Query(criteria), "messages");
        return Map.of("list", list, "total", total, "hasMore", offset + limit < total);
    }

    // ==================== 内部方法 ====================

    private void validateParticipant(Long userId, String conversationId, int convType) {
        if (convType == 0) {
            String[] parts = conversationId.substring(2).split("_");
            long a = Long.parseLong(parts[0]), b = Long.parseLong(parts[1]);
            if (userId != a && userId != b) throw new BizException(ErrorCode.FORBIDDEN, "你不是该会话的参与者");
            long peer = (userId == a) ? b : a;
            if (friendMapper.findByPair(userId, peer) == null || friendMapper.findByPair(peer, userId) == null)
                throw new BizException(ErrorCode.FORBIDDEN, "你们不是好友");
            if (blacklistMapper.findByPair(userId, peer) != null)
                throw new BizException(ErrorCode.FORBIDDEN, "你已将对方拉黑，无法发送消息");
            if (blacklistMapper.findByPair(peer, userId) != null)
                throw new BizException(ErrorCode.FORBIDDEN, "对方已将你拉黑");
        } else {
            long groupId = Long.parseLong(conversationId.substring(2));
            if (groupMemberMapper.findByGroupAndUser(groupId, userId) == null)
                throw new BizException(ErrorCode.FORBIDDEN, "你不是该群的成员");
        }
    }

    private void writePrivateFeeds(Long fromUserId, String convId, String messageId, long now) {
        String[] parts = convId.substring(2).split("_");
        long a = Long.parseLong(parts[0]), b = Long.parseLong(parts[1]);
        long receiverId = fromUserId == a ? b : a;
        insertFeed(receiverId, convId, messageId, now);
        insertFeed(fromUserId, convId, messageId, now);
        // 接收者未读 +1
        incrementUnread(receiverId, convId);
    }

    private void writeGroupFeeds(Long fromUserId, String convId, String messageId, long now) {
        long groupId = Long.parseLong(convId.substring(2));
        List<GroupMember> members = groupMemberMapper.findByGroupId(groupId);
        for (GroupMember m : members) {
            insertFeed(m.getUserId(), convId, messageId, now);
            if (!m.getUserId().equals(fromUserId)) {
                incrementUnread(m.getUserId(), convId);
            }
        }
    }

    private void insertFeed(Long userId, String convId, String messageId, long now) {
        Map<String, Object> feed = new LinkedHashMap<>();
        feed.put("userId", userId);
        feed.put("conversationId", convId);
        feed.put("messageId", messageId);
        feed.put("createdAt", now);
        mongoTemplate.insert(feed, "message_feed");
    }

    private void incrementUnread(Long userId, String convId) {
        String hashKey = SESSION_PREFIX + userId + ":" + convId;
        redisTemplate.opsForHash().increment(hashKey, "unread_count", 1);
    }

    private void updateSessionLastMsg(Long userId, String convId, String content, String msgType, long now) {
        String hashKey = SESSION_PREFIX + userId + ":" + convId;
        Map<String, String> updates = new HashMap<>();
        updates.put("last_msg_content", content);
        updates.put("last_msg_type", msgType);
        updates.put("last_msg_time", String.valueOf(now));
        redisTemplate.opsForHash().putAll(hashKey, updates);
        redisTemplate.opsForZSet().add(SESSION_ZSET + userId, convId, (double) now);
    }

    private void fillPrivateSession(Long userId, String convId, Map<String, Object> item) {
        String[] parts = convId.substring(2).split("_");
        long peerId = Long.parseLong(parts[0]) == userId ? Long.parseLong(parts[1]) : Long.parseLong(parts[0]);
        item.put("peerId", peerId);
        User peer = userMapper.findById(peerId);
        if (peer != null) {
            item.put("peerName", peer.getNickname() != null ? peer.getNickname() : peer.getUsername());
            String raw = peer.getAvatarUrl();
            item.put("peerAvatar", raw != null && !raw.isBlank() && !raw.startsWith("http")
                    ? "/api/files/avatar/" + peer.getId() : (raw != null ? raw : ""));
        } else {
            item.put("peerName", "");
            item.put("peerAvatar", "");
        }
    }

    private void fillGroupSession(String convId, Map<String, Object> item) {
        long groupId = Long.parseLong(convId.substring(2));
        item.put("peerId", groupId);
        GroupChat group = groupChatMapper.findById(groupId);
        if (group != null) {
            item.put("peerName", group.getName() != null ? group.getName() : "");
            item.put("peerAvatar", group.getAvatarUrl() != null ? resolveGroupAvatar(group.getAvatarUrl()) : "");
        } else {
            item.put("peerName", "");
            item.put("peerAvatar", "");
        }
    }

    private String resolveGroupAvatar(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith("http")) return raw;
        if (raw.contains("/")) return "/api/files/download/" + raw.substring(raw.indexOf("/") + 1);
        return raw;
    }

    private long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return 0L;
    }

    private int toInt(Object val) {
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) return Integer.parseInt((String) val);
        return 0;
    }
}
