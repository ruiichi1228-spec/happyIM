package com.happyim.api.service;

import com.happyim.common.mapper.FriendMapper;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.Friend;
import com.happyim.common.model.entity.User;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MomentService {

    private static final Logger log = LoggerFactory.getLogger(MomentService.class);
    private final MongoTemplate mongoTemplate;
    private final FriendMapper friendMapper;
    private final UserMapper userMapper;
    private final RabbitTemplate rabbitTemplate;

    @Value("${happyim.mq.exchange}")
    private String exchangeName;

    @Value("${happyim.mq.routing-key}")
    private String routingKey;

    public MomentService(MongoTemplate mongoTemplate, FriendMapper friendMapper,
                         UserMapper userMapper, RabbitTemplate rabbitTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.friendMapper = friendMapper;
        this.userMapper = userMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    // ==================== 发布 ====================

    public String publish(Long userId, String content, String mediaUrls) {
        User u = userMapper.findById(userId);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("userId", userId);
        doc.put("nickname", u != null ? u.getNickname() : "");
        doc.put("avatar", u != null ? resolveAvatar(u) : "");
        doc.put("content", content);
        doc.put("mediaUrls", mediaUrls);
        doc.put("visibility", 0);
        doc.put("likes", new ArrayList<>());
        doc.put("comments", new ArrayList<>());
        doc.put("createdAt", System.currentTimeMillis());
        mongoTemplate.insert(doc, "moments");
        return doc.get("_id").toString();
    }

    public void delete(String id, Long userId) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(new ObjectId(id)).and("userId").is(userId)), "moments");
    }

    // ==================== 时间线 ====================

    public List<Map<String, Object>> getTimeline(Long userId, int offset, int limit) {
        return getTimeline(userId, null, offset, limit);
    }

    public List<Map<String, Object>> getTimeline(Long userId, Long filterUserId, int offset, int limit) {
        Query q = new Query();
        if (filterUserId != null) {
            q.addCriteria(Criteria.where("userId").is(filterUserId));
        } else {
            List<Friend> friends = friendMapper.findByUserId(userId);
            List<Long> friendIds = new ArrayList<>(friends.stream().map(Friend::getFriendId).toList());
            friendIds.add(userId);
            q.addCriteria(Criteria.where("userId").in(friendIds));
        }
        q.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip(offset).limit(limit);
        List<Map> list = mongoTemplate.find(q, Map.class, "moments");

        return list.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>(m);
            item.put("id", m.get("_id").toString());
            item.remove("_id");
            // 检查当前用户是否已点赞
            List<Map> likes = (List<Map>) m.getOrDefault("likes", List.of());
            boolean liked = likes.stream().anyMatch(l -> userId.equals(l.get("userId")));
            item.put("isLiked", liked);
            return item;
        }).collect(Collectors.toList());
    }

    // ==================== 点赞 ====================

    public void like(String momentId, Long userId) {
        User u = userMapper.findById(userId);
        Map<String, Object> likeEntry = new LinkedHashMap<>();
        likeEntry.put("userId", userId);
        likeEntry.put("nickname", u != null ? u.getNickname() : "");
        likeEntry.put("createdAt", System.currentTimeMillis());

        // 先移除旧的，再添加（防止重复）
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(new ObjectId(momentId))),
                new Update().pull("likes", Query.query(Criteria.where("userId").is(userId))),
                "moments");
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(new ObjectId(momentId))),
                new Update().push("likes", likeEntry),
                "moments");

        // 通知
        Map m = mongoTemplate.findById(new ObjectId(momentId), Map.class, "moments");
        if (m != null && !userId.equals(m.get("userId"))) {
            sendNotification((Long) m.get("userId"), userId, momentId, "like", "赞了你的动态");
        }
    }

    public void unlike(String momentId, Long userId) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(new ObjectId(momentId))),
                new Update().pull("likes", Query.query(Criteria.where("userId").is(userId))),
                "moments");
    }

    // ==================== 评论 ====================

    public void comment(String momentId, Long userId, String content, Long replyTo) {
        User u = userMapper.findById(userId);
        User ru = replyTo != null ? userMapper.findById(replyTo) : null;
        Map<String, Object> commentEntry = new LinkedHashMap<>();
        commentEntry.put("userId", userId);
        commentEntry.put("nickname", u != null ? u.getNickname() : "");
        commentEntry.put("content", content);
        if (replyTo != null) {
            commentEntry.put("replyToUserId", replyTo);
            commentEntry.put("replyToNickname", ru != null ? ru.getNickname() : "");
        }
        commentEntry.put("createdAt", System.currentTimeMillis());

        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(new ObjectId(momentId))),
                new Update().push("comments", commentEntry),
                "moments");

        Map m = mongoTemplate.findById(new ObjectId(momentId), Map.class, "moments");
        if (m != null && !userId.equals(m.get("userId"))) {
            sendNotification((Long) m.get("userId"), userId, momentId, "comment", content.length() > 20 ? content.substring(0, 20) : content);
        }
        if (replyTo != null && !replyTo.equals(userId) && (m == null || !replyTo.equals(m.get("userId")))) {
            sendNotification(replyTo, userId, momentId, "reply", content.length() > 20 ? content.substring(0, 20) : content);
        }
    }

    public void deleteComment(String momentId, Long userId, int commentIndex) {
        Map m = mongoTemplate.findById(new ObjectId(momentId), Map.class, "moments");
        if (m == null) return;
        List<Map> comments = (List<Map>) m.get("comments");
        if (comments != null && commentIndex >= 0 && commentIndex < comments.size()) {
            Map c = comments.get(commentIndex);
            if (userId.equals(c.get("userId"))) {
                comments.remove(commentIndex);
                mongoTemplate.updateFirst(
                        new Query(Criteria.where("_id").is(new ObjectId(momentId))),
                        new Update().set("comments", comments),
                        "moments");
            }
        }
    }

    // ==================== 通知 ====================

    public List<Map<String, Object>> getNotifications(Long userId) {
        List<Map> list = mongoTemplate.find(
                new Query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(50),
                Map.class, "moment_notifications");
        return list.stream().map(n -> {
            Map<String, Object> item = new LinkedHashMap<>(n);
            item.put("id", n.get("_id").toString());
            item.remove("_id");
            return item;
        }).collect(Collectors.toList());
    }

    public int getUnreadCount(Long userId) {
        Map<String, Object> summary = getUserSummary(userId);
        Object count = summary.get("momentUnread");
        return count instanceof Number ? ((Number) count).intValue() : 0;
    }

    public Map<String, Object> getUserSummary(Long userId) {
        Map<String, Object> summary = mongoTemplate.findById(userId, Map.class, "user_summary");
        if (summary == null) {
            // 首次访问：统计实际未读数并初始化
            int count = (int) mongoTemplate.count(
                    new Query(Criteria.where("userId").is(userId).and("isRead").is(false)),
                    "moment_notifications");
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("_id", userId);
            doc.put("userId", userId);
            doc.put("momentUnread", count);
            doc.put("squareUnread", 0);
            mongoTemplate.insert(doc, "user_summary");
            summary = doc;
        }
        // 补齐缺少的字段
        if (!summary.containsKey("momentUnread")) summary.put("momentUnread", 0);
        if (!summary.containsKey("squareUnread")) summary.put("squareUnread", 0);
        if (summary.containsKey("_id")) summary.remove("_id");
        return summary;
    }

    public void markAllRead(Long userId) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("userId").is(userId).and("isRead").is(false)),
                new Update().set("isRead", true),
                "moment_notifications");
        // 重置摘要未读数
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().set("momentUnread", 0).setOnInsert("userId", userId),
                "user_summary");
    }

    public void clearNotifications(Long userId) {
        mongoTemplate.remove(
                new Query(Criteria.where("userId").is(userId)),
                "moment_notifications");
        // 重置摘要未读数
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().set("momentUnread", 0).setOnInsert("userId", userId),
                "user_summary");
    }

    public Map<String, Object> getById(String momentId, Long userId) {
        Map m = mongoTemplate.findById(new ObjectId(momentId), Map.class, "moments");
        if (m == null) return null;
        Map<String, Object> item = new LinkedHashMap<>(m);
        item.put("id", m.get("_id").toString());
        item.remove("_id");
        List<Map> likes = (List<Map>) m.getOrDefault("likes", List.of());
        boolean liked = likes.stream().anyMatch(l -> userId.equals(l.get("userId")));
        item.put("isLiked", liked);
        return item;
    }

    private void sendNotification(Long userId, Long fromUserId, String momentId, String type, String content) {
        User from = userMapper.findById(fromUserId);
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("userId", userId);
        n.put("fromUserId", fromUserId);
        n.put("fromNickname", from != null ? from.getNickname() : "");
        n.put("fromAvatar", from != null ? resolveAvatar(from) : "");
        n.put("momentId", momentId);
        n.put("type", type);
        n.put("content", content);
        n.put("isRead", false);
        n.put("createdAt", System.currentTimeMillis());
        mongoTemplate.insert(n, "moment_notifications");

        // 递增用户摘要的未读数
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().inc("momentUnread", 1).setOnInsert("userId", userId),
                "user_summary");

        // WS 推送
        notifyMomentEvent(userId);
    }

    private void notifyMomentEvent(Long targetUserId) {
        try {
            Map<String, Object> msg = Map.of(
                "type", "moment_notify",
                "targetUserId", targetUserId
            );
            rabbitTemplate.convertAndSend(exchangeName, routingKey, msg);
        } catch (Exception e) {
            log.warn("推送朋友圈事件失败: targetUserId={}, {}", targetUserId, e.getMessage());
        }
    }

    private String resolveAvatar(User u) {
        if (u == null) return null;
        String raw = u.getAvatarUrl();
        if (raw != null && !raw.isBlank() && !raw.startsWith("http")) return "/api/files/avatar/" + u.getId();
        return raw;
    }
}
