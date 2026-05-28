package com.happyim.user.service;

import com.happyim.common.mapper.*;
import com.happyim.common.model.entity.*;
import com.happyim.common.security.JwtUtil;
import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
public class AdminService {

    private static final Logger log = LoggerFactory.getLogger(AdminService.class);

    private final AdminUserMapper adminUserMapper;
    private final UserMapper userMapper;
    private final GroupChatMapper groupChatMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final SensitiveWordMapper sensitiveWordMapper;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AdminService(AdminUserMapper adminUserMapper, UserMapper userMapper,
                        GroupChatMapper groupChatMapper, GroupMemberMapper groupMemberMapper,
                        SensitiveWordMapper sensitiveWordMapper,
                        JwtUtil jwtUtil, RedisTemplate<String, String> redisTemplate,
                        MongoTemplate mongoTemplate) {
        this.adminUserMapper = adminUserMapper;
        this.userMapper = userMapper;
        this.groupChatMapper = groupChatMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.jwtUtil = jwtUtil;
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
    }

    // ==================== 登录 ====================

    public Map<String, Object> login(String username, String password) {
        AdminUser admin = adminUserMapper.findByUsername(username);
        if (admin == null || !passwordEncoder.matches(password, admin.getPassword())) {
            throw new BizException(ErrorCode.BAD_CREDENTIALS, "管理员用户名或密码错误");
        }

        String token = jwtUtil.generateAdminToken(admin.getId());
        Map<String, Object> result = new HashMap<>();
        result.put("adminId", admin.getId());
        result.put("username", admin.getUsername());
        result.put("nickname", admin.getNickname());
        result.put("token", token);
        return result;
    }

    // ==================== 仪表盘 ====================

    public Map<String, Object> dashboard() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userMapper.countTotalUsers());
        stats.put("todayNewUsers", userMapper.countTodayNewUsers());
        stats.put("totalGroups", groupChatMapper.countTotalGroups());

        // 在线用户数
        Set<String> onlineKeys = redisTemplate.keys("online:user:*");
        stats.put("onlineUsers", onlineKeys != null ? onlineKeys.size() : 0);

        // 今日消息数
        long startOfDay = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        long todayMessages = mongoTemplate.count(
                new Query(Criteria.where("createdAt").gte(startOfDay)), "messages");
        stats.put("todayMessages", todayMessages);

        // 文件数（MongoDB file_feed 集合）
        long totalFiles = mongoTemplate.count(new Query(), "file_feed");
        stats.put("totalFiles", totalFiles);

        return stats;
    }

    // ==================== 用户管理 ====================

    public Map<String, Object> listUsers(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<User> users = userMapper.findUsersPage(keyword, offset, pageSize);
        int total = userMapper.countUsers(keyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", users);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    public User getUserDetail(Long id) {
        User user = userMapper.findById(id);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        user.setPassword(null);
        return user;
    }

    public void updateUserStatus(Long userId, Integer status) {
        User user = userMapper.findById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        userMapper.updateStatus(userId, status);
        log.info("管理员修改用户状态: userId={}, newStatus={}", userId, status);
    }

    // ==================== 群组管理 ====================

    public Map<String, Object> listGroups(String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<GroupChat> groups = groupChatMapper.findGroupsPage(keyword, offset, pageSize);
        int total = groupChatMapper.countGroups(keyword);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", groups);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    public List<Map<String, Object>> getGroupMembers(Long groupId) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "群组不存在");
        }
        List<GroupMember> members = groupMemberMapper.findByGroupId(groupId);
        List<Long> userIds = members.stream().map(GroupMember::getUserId).toList();
        List<User> users = userIds.isEmpty() ? List.of() : userMapper.findByIds(userIds);

        Map<Long, User> userMap = new HashMap<>();
        for (User u : users) userMap.put(u.getId(), u);

        List<Map<String, Object>> result = new ArrayList<>();
        for (GroupMember gm : members) {
            User u = userMap.get(gm.getUserId());
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("userId", gm.getUserId());
            item.put("role", gm.getRole());
            item.put("groupNickname", gm.getGroupNickname());
            item.put("nickname", u != null ? u.getNickname() : "");
            item.put("avatarUrl", u != null ? u.getAvatarUrl() : "");
            result.add(item);
        }
        return result;
    }

    @Transactional
    public void dissolveGroup(Long groupId) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "群组不存在");
        }
        if (group.getStatus() == 1) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION, "群组已解散");
        }
        groupChatMapper.dissolve(groupId);
        log.info("管理员解散群组: groupId={}, name={}", groupId, group.getName());
    }

    // ==================== 敏感词管理 ====================

    public List<SensitiveWord> listSensitiveWords() {
        return sensitiveWordMapper.findAll();
    }

    @Transactional
    public void addSensitiveWord(String word) {
        if (word == null || word.isBlank()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "敏感词不能为空");
        }
        SensitiveWord existing = sensitiveWordMapper.findByWord(word);
        if (existing != null) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION, "该敏感词已存在");
        }
        sensitiveWordMapper.insert(word);
        // TODO: notify chat-service to reload via MQ event
        log.info("管理员添加敏感词: {}", word);
    }

    @Transactional
    public void deleteSensitiveWord(Long id) {
        sensitiveWordMapper.deleteById(id);
        // TODO: notify chat-service to reload via MQ event
        log.info("管理员删除敏感词: id={}", id);
    }

    // ==================== 文件管理 ====================

    public Map<String, Object> listFiles(String keyword, String fileType, int page, int pageSize) {
        List<Criteria> criteriaList = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            criteriaList.add(Criteria.where("fileName").regex(keyword, "i"));
        }
        if (fileType != null && !fileType.isBlank() && !"all".equals(fileType)) {
            criteriaList.add(Criteria.where("fileType").is(fileType));
        }
        Criteria criteria = criteriaList.isEmpty()
                ? new Criteria() : new Criteria().andOperator(criteriaList.toArray(new Criteria[0]));

        // aggregation: group by messageId to dedup, then sort + paginate
        long total = mongoTemplate.count(new Query(criteria), "file_feed");

        var agg = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("messageId")
                        .first("fileName").as("fileName")
                        .first("fileSize").as("fileSize")
                        .first("fileType").as("fileType")
                        .first("fileUrl").as("fileUrl")
                        .first("senderName").as("senderName")
                        .first("conversationName").as("conversationName")
                        .first("createdAt").as("createdAt")
                        .first("messageType").as("messageType"),
                Aggregation.sort(Sort.Direction.DESC, "createdAt"),
                Aggregation.skip((long) (page - 1) * pageSize),
                Aggregation.limit(pageSize)
        );

        List<Map> list = mongoTemplate.aggregate(agg, "file_feed", Map.class).getMappedResults();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        return result;
    }

    public Map<String, Object> fileStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        long total = mongoTemplate.count(new Query(), "file_feed");
        stats.put("totalFiles", total);

        // 按类型统计
        var agg = Aggregation.newAggregation(
                Aggregation.group("fileType").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count")
        );
        List<Map> typeStats = mongoTemplate.aggregate(agg, "file_feed", Map.class).getMappedResults();
        stats.put("byType", typeStats);

        // 总大小
        var sizeAgg = Aggregation.newAggregation(
                Aggregation.group().sum("fileSize").as("totalSize")
        );
        List<Map> sizeResult = mongoTemplate.aggregate(sizeAgg, "file_feed", Map.class).getMappedResults();
        long totalSize = sizeResult.isEmpty() ? 0 : ((Number) sizeResult.get(0).get("totalSize")).longValue();
        stats.put("totalSize", totalSize);
        stats.put("totalSizeFormatted", formatFileSize(totalSize));

        return stats;
    }

    @Transactional
    public void deleteFile(String messageId) {
        mongoTemplate.remove(new Query(Criteria.where("messageId").is(messageId)), "file_feed");
        log.info("管理员删除文件记录: messageId={}", messageId);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1073741824) return String.format("%.1f MB", bytes / 1048576.0);
        return String.format("%.2f GB", bytes / 1073741824.0);
    }

    // ==================== 初始化 ====================

    public void ensureDefaultAdmin() {
        try {
            if (adminUserMapper.countAll() == 0) {
                AdminUser admin = new AdminUser();
                admin.setUsername("admin");
                admin.setPassword(passwordEncoder.encode("admin123"));
                admin.setNickname("系统管理员");
                adminUserMapper.insert(admin);
                log.info("已创建默认管理员账号: admin / admin123");
            }
        } catch (Exception e) {
            log.warn("无法初始化默认管理员账号（表可能尚未创建）: {}", e.getMessage());
        }
    }
}
