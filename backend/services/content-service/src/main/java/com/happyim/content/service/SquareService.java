package com.happyim.content.service;

import com.happyim.common.mapper.UserMapper;
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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SquareService {

    private static final Logger log = LoggerFactory.getLogger(SquareService.class);
    private final MongoTemplate mongoTemplate;
    private final UserMapper userMapper;
    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;

    @Value("${happyim.mq.exchange}")
    private String exchangeName;

    @Value("${happyim.mq.routing-key}")
    private String routingKey;

    public SquareService(MongoTemplate mongoTemplate, UserMapper userMapper,
                         RabbitTemplate rabbitTemplate, StringRedisTemplate redisTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.userMapper = userMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
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
        doc.put("likes", new ArrayList<>());
        doc.put("comments", new ArrayList<>());
        doc.put("createdAt", System.currentTimeMillis());
        mongoTemplate.insert(doc, "square_posts");

        // 记入当日活跃
        incrActivity(userId, 2);

        return doc.get("_id").toString();
    }

    public void delete(String id, Long userId) {
        mongoTemplate.remove(new Query(Criteria.where("_id").is(new ObjectId(id)).and("userId").is(userId)), "square_posts");
    }

    // ==================== 全局列表 ====================

    public List<Map<String, Object>> getPosts(Long userId, int offset, int limit) {
        return getPosts(userId, null, offset, limit);
    }

    public List<Map<String, Object>> getPosts(Long userId, Long filterUserId, int offset, int limit) {
        Query q = new Query();
        if (filterUserId != null) {
            q.addCriteria(Criteria.where("userId").is(filterUserId));
        }
        q.with(Sort.by(Sort.Direction.DESC, "createdAt")).skip(offset).limit(limit);
        List<Map> list = mongoTemplate.find(q, Map.class, "square_posts");

        return list.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>(m);
            item.put("id", m.get("_id").toString());
            item.remove("_id");
            @SuppressWarnings("unchecked")
            List<Map> likes = (List<Map>) m.getOrDefault("likes", List.of());
            boolean liked = likes.stream().anyMatch(l -> userId.equals(l.get("userId")));
            item.put("isLiked", liked);
            return item;
        }).collect(Collectors.toList());
    }

    // ==================== 点赞 ====================

    public void like(String postId, Long userId) {
        User u = userMapper.findById(userId);
        Map<String, Object> likeEntry = new LinkedHashMap<>();
        likeEntry.put("userId", userId);
        likeEntry.put("nickname", u != null ? u.getNickname() : "");
        likeEntry.put("createdAt", System.currentTimeMillis());

        // $addToSet: 原子操作，已存在则跳过
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(new ObjectId(postId))),
                new Update().addToSet("likes", likeEntry),
                "square_posts");

        Map m = mongoTemplate.findById(new ObjectId(postId), Map.class, "square_posts");
        if (m != null && !userId.equals(m.get("userId"))) {
            sendNotification((Long) m.get("userId"), userId, postId, "like", u != null ? u.getNickname() : "");
        }
    }

    public void unlike(String postId, Long userId) {
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(new ObjectId(postId))),
                new Update().pull("likes", Query.query(Criteria.where("userId").is(userId))),
                "square_posts");
    }

    // ==================== 评论 ====================

    public void comment(String postId, Long userId, String content, Long replyTo) {
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
                new Query(Criteria.where("_id").is(new ObjectId(postId))),
                new Update().push("comments", commentEntry),
                "square_posts");

        // 记入当日活跃
        incrActivity(userId, 1);

        Map m = mongoTemplate.findById(new ObjectId(postId), Map.class, "square_posts");
        if (m != null && !userId.equals(m.get("userId"))) {
            sendNotification((Long) m.get("userId"), userId, postId, "comment",
                    content.length() > 20 ? content.substring(0, 20) : content);
        }
        if (replyTo != null && !replyTo.equals(userId) && (m == null || !replyTo.equals(m.get("userId")))) {
            sendNotification(replyTo, userId, postId, "reply",
                    content.length() > 20 ? content.substring(0, 20) : content);
        }
    }

    public Map<String, Object> getById(String postId, Long userId) {
        Map m = mongoTemplate.findById(new ObjectId(postId), Map.class, "square_posts");
        if (m == null) return null;
        Map<String, Object> item = new LinkedHashMap<>(m);
        item.put("id", m.get("_id").toString());
        item.remove("_id");
        @SuppressWarnings("unchecked")
        List<Map> likes = (List<Map>) m.getOrDefault("likes", List.of());
        boolean liked = likes.stream().anyMatch(l -> userId.equals(l.get("userId")));
        item.put("isLiked", liked);
        return item;
    }

    public void deleteComment(String postId, Long userId, int commentIndex) {
        // $pull 原子删除，避免读-改-写竞态
        mongoTemplate.updateFirst(
                new Query(Criteria.where("_id").is(new ObjectId(postId))),
                new Update().pull("comments", Query.query(
                        Criteria.where("userId").is(userId))),
                "square_posts");
    }

    // ==================== 排行榜（Redis ZSET） ====================

    public List<Map<String, Object>> getLeaderboard() {
        String key = leaderboardKey();
        // ZREVRANGE 按分数降序取 top 20
        Set<String> top = redisTemplate.opsForZSet().reverseRange(key, 0, 19);
        if (top == null || top.isEmpty()) return List.of();

        List<Long> userIds = top.stream().map(Long::parseLong).toList();
        List<User> users = userMapper.findByIds(userIds);
        Map<Long, User> userMap = new LinkedHashMap<>();
        for (User u : users) userMap.put(u.getId(), u);

        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (String uidStr : top) {
            Long uid = Long.parseLong(uidStr);
            Double score = redisTemplate.opsForZSet().score(key, uidStr);
            User u = userMap.get(uid);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", uid);
            item.put("nickname", u != null ? u.getNickname() : "");
            item.put("avatar", u != null ? resolveAvatar(u) : "");
            item.put("score", score != null ? score.intValue() : 0);
            result.add(item);
            rank++;
        }
        return result;
    }

    private String leaderboardKey() {
        return "square:leaderboard:" + LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private void incrActivity(Long userId, int score) {
        String key = leaderboardKey();
        redisTemplate.opsForZSet().incrementScore(key, String.valueOf(userId), score);
        // 设置过期：3 天后自动清理
        redisTemplate.expire(key, Duration.ofDays(3));
    }

    // ==================== 通知 ====================

    public List<Map<String, Object>> getNotifications(Long userId) {
        List<Map> list = mongoTemplate.find(
                new Query(Criteria.where("userId").is(userId)).with(Sort.by(Sort.Direction.DESC, "createdAt")).limit(50),
                Map.class, "square_notifications");
        return list.stream().map(n -> {
            Map<String, Object> item = new LinkedHashMap<>(n);
            item.put("id", n.get("_id").toString());
            item.remove("_id");
            return item;
        }).collect(Collectors.toList());
    }

    public int getUnreadCount(Long userId) {
        Map<String, Object> summary = getUserSummary(userId);
        Object count = summary.get("squareUnread");
        return count instanceof Number ? ((Number) count).intValue() : 0;
    }

    public Map<String, Object> getUserSummary(Long userId) {
        Map<String, Object> summary = mongoTemplate.findById(userId, Map.class, "user_summary");
        if (summary == null) {
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("_id", userId);
            doc.put("userId", userId);
            doc.put("momentUnread", 0);
            doc.put("squareUnread", 0);
            mongoTemplate.insert(doc, "user_summary");
            summary = doc;
        }
        if (!summary.containsKey("momentUnread")) summary.put("momentUnread", 0);
        if (!summary.containsKey("squareUnread")) summary.put("squareUnread", 0);
        if (summary.containsKey("_id")) summary.remove("_id");
        return summary;
    }

    public void markAllRead(Long userId) {
        mongoTemplate.updateMulti(
                new Query(Criteria.where("userId").is(userId).and("isRead").is(false)),
                new Update().set("isRead", true),
                "square_notifications");
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().set("squareUnread", 0).setOnInsert("userId", userId),
                "user_summary");
    }

    public void clearNotifications(Long userId) {
        mongoTemplate.remove(
                new Query(Criteria.where("userId").is(userId)),
                "square_notifications");
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().set("squareUnread", 0).setOnInsert("userId", userId),
                "user_summary");
    }

    // ==================== 内部 ====================

    private void incrActivity(Long userId, String nickname, String avatar, int score) {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String docId = today + ":" + userId;
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(docId)),
                new Update()
                        .inc("score", score)
                        .inc("postCount", score >= 2 ? 1 : 0)
                        .inc("commentCount", score == 1 ? 1 : 0)
                        .setOnInsert("date", today)
                        .set("userId", userId)
                        .set("nickname", nickname)
                        .set("avatar", avatar),
                "square_activity");
    }

    private void sendNotification(Long userId, Long fromUserId, String postId, String type, String content) {
        User from = userMapper.findById(fromUserId);
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("userId", userId);
        n.put("fromUserId", fromUserId);
        n.put("fromNickname", from != null ? from.getNickname() : "");
        n.put("fromAvatar", from != null ? resolveAvatar(from) : "");
        n.put("postId", postId);
        n.put("type", type);
        n.put("content", content);
        n.put("isRead", false);
        n.put("createdAt", System.currentTimeMillis());
        mongoTemplate.insert(n, "square_notifications");

        // 递增用户摘要未读数
        mongoTemplate.upsert(
                new Query(Criteria.where("_id").is(userId)),
                new Update().inc("squareUnread", 1).setOnInsert("userId", userId),
                "user_summary");

        // WS 推送
        try {
            Map<String, Object> msg = Map.of(
                "type", "square_notify",
                "targetUserId", userId
            );
            rabbitTemplate.convertAndSend(exchangeName, routingKey, msg);
        } catch (Exception e) {
            log.warn("推送广场事件失败: {}", e.getMessage());
        }
    }

    private String resolveAvatar(User u) {
        if (u == null) return null;
        String raw = u.getAvatarUrl();
        if (raw != null && !raw.isBlank() && !raw.startsWith("http")) return "/api/files/avatar/" + u.getId();
        return raw;
    }
}
