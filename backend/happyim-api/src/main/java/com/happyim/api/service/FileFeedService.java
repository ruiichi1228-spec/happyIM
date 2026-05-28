package com.happyim.api.service;

import com.happyim.common.mapper.GroupChatMapper;
import com.happyim.common.mapper.GroupMemberMapper;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.entity.GroupMember;
import com.happyim.common.model.entity.User;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class FileFeedService {

    private static final Logger log = LoggerFactory.getLogger(FileFeedService.class);
    private final MongoTemplate mongoTemplate;
    private final UserMapper userMapper;
    private final GroupChatMapper groupChatMapper;
    private final GroupMemberMapper groupMemberMapper;

    public FileFeedService(MongoTemplate mongoTemplate, UserMapper userMapper,
                           GroupChatMapper groupChatMapper, GroupMemberMapper groupMemberMapper) {
        this.mongoTemplate = mongoTemplate;
        this.userMapper = userMapper;
        this.groupChatMapper = groupChatMapper;
        this.groupMemberMapper = groupMemberMapper;
    }

    // ==================== 写入 ====================

    public void recordFile(Long userId, Long senderId, String conversationId, int conversationType,
                           String fileName, long fileSize, String fileUrl, String messageType,
                           String messageId) {
        // 为每个参与者都写一份（按 userId + messageId 去重）
        List<Long> participants = new ArrayList<>();
        participants.add(userId);
        if (conversationType == 0) {
            String[] parts = conversationId.substring(2).split("_");
            long a = Long.parseLong(parts[0]);
            long b = Long.parseLong(parts[1]);
            if (a != userId) participants.add(a);
            else participants.add(b);
        } else {
            List<GroupMember> members = groupMemberMapper.findByGroupId(
                    Long.parseLong(conversationId.substring(2)));
            if (members != null) {
                for (GroupMember gm : members) {
                    if (!participants.contains(gm.getUserId())) participants.add(gm.getUserId());
                }
            }
        }

        String ext = "";
        if (fileName != null && fileName.contains(".")) {
            ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }

        String conversationName = resolveConversationName(conversationId, conversationType, userId);
        User sender = userMapper.findById(senderId);
        String senderName = sender != null ? (sender.getNickname() != null ? sender.getNickname() : sender.getUsername()) : "";
        long now = System.currentTimeMillis();

        for (Long pid : participants) {
            // upsert by userId + messageId 防止重复
            Query q = new Query(Criteria.where("userId").is(pid).and("messageId").is(messageId));
            Update u = new Update()
                    .setOnInsert("userId", pid)
                    .set("senderId", senderId)
                    .set("senderName", senderName)
                    .set("conversationId", conversationId)
                    .set("conversationType", conversationType)
                    .set("conversationName", conversationName)
                    .set("fileName", fileName)
                    .set("fileSize", fileSize)
                    .set("fileType", ext)
                    .set("fileUrl", fileUrl)
                    .set("messageType", messageType)
                    .set("messageId", messageId)
                    .set("createdAt", now);
            mongoTemplate.upsert(q, u, "file_feed");
        }
    }

    // ==================== 查询文件列表 ====================

    public Map<String, Object> getFeed(Long userId, int page, int size,
                                        String fileType, Long senderId, String conversationId) {
        Query q = new Query(Criteria.where("userId").is(userId));
        if (fileType != null && !fileType.isEmpty()) {
            q.addCriteria(Criteria.where("fileType").is(fileType));
        }
        if (senderId != null) {
            q.addCriteria(Criteria.where("senderId").is(senderId));
        }
        if (conversationId != null && !conversationId.isEmpty()) {
            q.addCriteria(Criteria.where("conversationId").is(conversationId));
        }
        long total = mongoTemplate.count(q, "file_feed");
        q.with(Sort.by(Sort.Direction.DESC, "messageId")).skip((page - 1) * size).limit(size);
        List<Map> list = mongoTemplate.find(q, Map.class, "file_feed");

        List<Map<String, Object>> items = list.stream().map(m -> {
            Map<String, Object> item = new LinkedHashMap<>(m);
            item.put("id", m.get("_id").toString());
            item.remove("_id");
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    // ==================== 筛选选项 ====================

    public List<Map<String, Object>> getSenders(Long userId) {
        // 用 distinct 或 aggregation 取所有不重复的发送者
        Query q = new Query(Criteria.where("userId").is(userId));
        q.fields().include("senderId", "senderName");
        List<Map> all = mongoTemplate.find(q, Map.class, "file_feed");
        Map<Long, String> seen = new LinkedHashMap<>();
        for (Map m : all) {
            Long sid = toLong(m.get("senderId"));
            if (sid != null && !seen.containsKey(sid)) {
                seen.put(sid, (String) m.get("senderName"));
            }
        }
        return seen.entrySet().stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("senderId", e.getKey());
            item.put("senderName", e.getValue());
            return item;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getConversations(Long userId) {
        Query q = new Query(Criteria.where("userId").is(userId));
        q.fields().include("conversationId", "conversationName", "conversationType");
        List<Map> all = mongoTemplate.find(q, Map.class, "file_feed");
        Map<String, String> seen = new LinkedHashMap<>();
        for (Map m : all) {
            String cid = (String) m.get("conversationId");
            if (cid != null && !seen.containsKey(cid)) {
                seen.put(cid, (String) m.get("conversationName"));
            }
        }
        return seen.entrySet().stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("conversationId", e.getKey());
            item.put("conversationName", e.getValue());
            return item;
        }).collect(Collectors.toList());
    }

    // ==================== 内部 ====================

    private String resolveConversationName(String conversationId, int conversationType, Long selfId) {
        if (conversationType == 0) {
            String[] parts = conversationId.substring(2).split("_");
            long otherId = Long.parseLong(parts[0]) == selfId ? Long.parseLong(parts[1]) : Long.parseLong(parts[0]);
            User u = userMapper.findById(otherId);
            return u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "";
        } else {
            var g = groupChatMapper.findById(Long.parseLong(conversationId.substring(2)));
            return g != null ? g.getName() : "";
        }
    }

    private Long toLong(Object val) {
        if (val instanceof Number) return ((Number) val).longValue();
        if (val instanceof String) return Long.parseLong((String) val);
        return null;
    }
}
