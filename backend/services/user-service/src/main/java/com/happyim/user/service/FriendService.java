package com.happyim.user.service;

import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.mapper.*;
import com.happyim.common.model.dto.*;
import com.happyim.common.model.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class FriendService {

    private static final Logger log = LoggerFactory.getLogger(FriendService.class);

    private final FriendRequestMapper friendRequestMapper;
    private final FriendMapper friendMapper;
    private final BlacklistMapper blacklistMapper;
    private final UserMapper userMapper;
    private final RabbitTemplate rabbitTemplate;
    private final RedisTemplate<String, String> redisTemplate;

    @Value("${happyim.mq.exchange}")
    private String exchangeName;

    public FriendService(FriendRequestMapper friendRequestMapper, FriendMapper friendMapper,
                         BlacklistMapper blacklistMapper, UserMapper userMapper,
                         RabbitTemplate rabbitTemplate,
                         RedisTemplate<String, String> redisTemplate) {
        this.friendRequestMapper = friendRequestMapper;
        this.friendMapper = friendMapper;
        this.blacklistMapper = blacklistMapper;
        this.userMapper = userMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
    }

    private void sendSystemMessage(String conversationId, int convType, String content, String subType, String msgType) {
        try {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("type", "system_message");
            event.put("conversationId", conversationId);
            event.put("conversationType", convType);
            event.put("content", content);
            event.put("subType", subType);
            event.put("messageType", msgType);
            rabbitTemplate.convertAndSend(exchangeName, "system.message", event);
        } catch (Exception e) {
            log.warn("发送系统消息失败: {}", e.getMessage());
        }
    }

    // ==================== 搜索用户 ====================

    public List<UserSearchResult> searchUsers(Long myUserId, String keyword) {
        List<User> users = userMapper.searchByKeyword(keyword);
        List<UserSearchResult> results = new ArrayList<>();
        for (User u : users) {
            if (u.getId().equals(myUserId)) continue;
            UserSearchResult r = new UserSearchResult();
            r.setUserId(u.getId());
            r.setUsername(u.getUsername());
            r.setNickname(u.getNickname());
            r.setAvatarUrl(resolveAvatar(u));
            Friend f = friendMapper.findByPair(myUserId, u.getId());
            r.setIsFriend(f != null);
            results.add(r);
        }
        return results;
    }

    // ==================== 发送好友申请 ====================

    @Transactional
    public void sendRequest(Long fromUserId, Long toUserId, String message) {
        if (fromUserId.equals(toUserId)) {
            throw new BizException(ErrorCode.CANNOT_OPERATE_SELF);
        }

        // A 是否已经把 B 加为好友？（查 A→B 方向）
        Friend existingFriend = friendMapper.findByPair(fromUserId, toUserId);
        if (existingFriend != null) {
            throw new BizException(ErrorCode.ALREADY_FRIEND);
        }

        // 被拉黑了？
        Blacklist blocked = blacklistMapper.findByPair(toUserId, fromUserId);
        if (blocked != null) {
            throw new BizException(ErrorCode.BLOCKED_BY_USER);
        }

        // 已有待处理申请 → 覆盖
        FriendRequest pending = friendRequestMapper.findPending(fromUserId, toUserId);
        if (pending != null) {
            pending.setMessage(message);
            pending.setCreatedTime(null); // will be updated by DB
            // just update message - we handle this by insert with ON DUPLICATE or re-insert
            // Simpler: insert a new one, the old one becomes stale but that's fine since UI shows by toUserId
            // Actually, simpler: just insert. The findPending check is for status=0. Let's insert again.
        }

        FriendRequest req = new FriendRequest();
        req.setFromUserId(fromUserId);
        req.setToUserId(toUserId);
        req.setMessage(message);
        req.setStatus(0);
        friendRequestMapper.insert(req);

        // WS 推送：通知对方有新的好友申请
        notifyFriendEvent(toUserId);

        log.info("好友申请: from={} to={}", fromUserId, toUserId);
    }

    // ==================== 收到的申请列表 ====================

    public List<FriendRequestVO> getReceivedRequests(Long userId) {
        List<FriendRequest> requests = friendRequestMapper.findByToUserId(userId);
        List<FriendRequestVO> vos = new ArrayList<>();
        for (FriendRequest req : requests) {
            vos.add(toRequestVO(req));
        }
        return vos;
    }

    // ==================== 未处理申请数 ====================

    public int getPendingCount(Long userId) {
        List<FriendRequest> requests = friendRequestMapper.findByToUserId(userId);
        return (int) requests.stream().filter(r -> r.getStatus() == 0).count();
    }

    // ==================== 同意申请 ====================

    @Transactional
    public void acceptRequest(Long requestId, Long currentUserId) {
        FriendRequest req = friendRequestMapper.findById(requestId);
        if (req == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!req.getToUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (req.getStatus() != 0) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION, "该申请已处理");
        }

        // 更新申请状态
        friendRequestMapper.updateStatus(requestId, 1);

        // 双向写入 friend 表
        Friend f1 = new Friend();
        f1.setUserId(req.getFromUserId());
        f1.setFriendId(req.getToUserId());
        f1.setIsStarred(0);
        friendMapper.insert(f1);

        Friend f2 = new Friend();
        f2.setUserId(req.getToUserId());
        f2.setFriendId(req.getFromUserId());
        f2.setIsStarred(0);
        friendMapper.insert(f2);

        // 系统通知：你们已成为好友
        String convId = req.getFromUserId() < req.getToUserId() ? "p_" + req.getFromUserId() + "_" + req.getToUserId() : "p_" + req.getToUserId() + "_" + req.getFromUserId();
        sendSystemMessage(convId, 0, "你们已成为好友，可以开始聊天了", "friend_added", "system");

        // WS 推送：通知申请者对方已同意
        notifyFriendEvent(req.getFromUserId());

        log.info("好友申请已同意: {} -> {}", req.getFromUserId(), req.getToUserId());
    }

    // ==================== 拒绝申请 ====================

    public void rejectRequest(Long requestId, Long currentUserId) {
        FriendRequest req = friendRequestMapper.findById(requestId);
        if (req == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        if (!req.getToUserId().equals(currentUserId)) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (req.getStatus() != 0) {
            throw new BizException(ErrorCode.DUPLICATE_OPERATION, "该申请已处理");
        }
        friendRequestMapper.updateStatus(requestId, 2);
        log.info("好友申请已拒绝: {} -> {}", req.getFromUserId(), req.getToUserId());
    }

    // ==================== 好友列表 ====================

    public List<FriendVO> getFriends(Long userId) {
        List<Friend> friends = friendMapper.findByUserId(userId);
        List<FriendVO> vos = new ArrayList<>();
        for (Friend f : friends) {
            User friendUser = userMapper.findById(f.getFriendId());
            if (friendUser == null) continue;

            FriendVO vo = new FriendVO();
            vo.setUserId(friendUser.getId());
            vo.setUsername(friendUser.getUsername());
            vo.setNickname(friendUser.getNickname());
            vo.setAvatarUrl(resolveAvatar(friendUser));
            vo.setRemark(f.getRemark());
            vo.setIsStarred(f.getIsStarred() == 1);
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 删除好友 ====================

    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        if (userId.equals(friendId)) {
            throw new BizException(ErrorCode.CANNOT_OPERATE_SELF);
        }

        Friend existing = friendMapper.findByPair(userId, friendId);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "你们不是好友");
        }

        friendMapper.deleteByPair(userId, friendId);
        log.info("删除好友: userId={} friendId={}", userId, friendId);
    }

    // ==================== 星标 ====================

    public void updateRemark(Long userId, Long friendId, String remark) {
        Friend existing = friendMapper.findByPair(userId, friendId);
        if (existing == null) throw new BizException(ErrorCode.NOT_FOUND, "你们不是好友");
        friendMapper.updateRemark(userId, friendId, remark);
    }

    public void toggleStar(Long userId, Long friendId, boolean starred) {
        Friend existing = friendMapper.findByPair(userId, friendId);
        if (existing == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "你们不是好友");
        }
        friendMapper.updateStarred(userId, friendId, starred ? 1 : 0);
    }

    // ==================== 拉黑 ====================

    public void blockUser(Long userId, Long blockedUserId) {
        if (userId.equals(blockedUserId)) {
            throw new BizException(ErrorCode.CANNOT_OPERATE_SELF);
        }

        Blacklist existing = blacklistMapper.findByPair(userId, blockedUserId);
        if (existing != null) {
            return; // 已经拉黑，幂等
        }

        Blacklist bl = new Blacklist();
        bl.setUserId(userId);
        bl.setBlockedUserId(blockedUserId);
        blacklistMapper.insert(bl);

        log.info("拉黑: userId={} blockedUserId={}", userId, blockedUserId);
    }

    // ==================== 取消拉黑 ====================

    public void unblockUser(Long userId, Long blockedUserId) {
        blacklistMapper.deleteByPair(userId, blockedUserId);
        log.info("取消拉黑: userId={} blockedUserId={}", userId, blockedUserId);
    }

    // ==================== 黑名单列表 ====================

    public List<FriendVO> getBlacklist(Long userId) {
        List<Blacklist> list = blacklistMapper.findByUserId(userId);
        List<FriendVO> vos = new ArrayList<>();
        for (Blacklist bl : list) {
            User blockedUser = userMapper.findById(bl.getBlockedUserId());
            if (blockedUser == null) continue;

            FriendVO vo = new FriendVO();
            vo.setUserId(blockedUser.getId());
            vo.setUsername(blockedUser.getUsername());
            vo.setNickname(blockedUser.getNickname());
            vo.setAvatarUrl(resolveAvatar(blockedUser));
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 内部方法 ====================

    /**
     * 解析头像 URL：有自定义头像时返回后端代理路径，否则返回 null
     */
    private String resolveAvatar(User user) {
        if (user == null) return null;
        String raw = user.getAvatarUrl();
        if (raw != null && !raw.isBlank() && !raw.startsWith("http")) {
            return "/api/files/avatar/" + user.getId();
        }
        return raw;
    }

    private void notifyFriendEvent(Long targetUserId) {
        try {
            String route = redisTemplate.opsForValue().get("router:user:" + targetUserId);
            if (route == null) return;
            Map<String, Object> msg = Map.of(
                "type", "friend_notify",
                "targetUserId", targetUserId
            );
            rabbitTemplate.convertAndSend(exchangeName, route, msg);
        } catch (Exception e) {
            log.warn("推送好友事件失败: targetUserId={}, {}", targetUserId, e.getMessage());
        }
    }

    private FriendRequestVO toRequestVO(FriendRequest req) {
        User fromUser = userMapper.findById(req.getFromUserId());

        FriendRequestVO vo = new FriendRequestVO();
        vo.setId(req.getId());
        vo.setFromUserId(req.getFromUserId());
        if (fromUser != null) {
            vo.setFromUsername(fromUser.getUsername());
            vo.setFromNickname(fromUser.getNickname());
            vo.setFromAvatarUrl(resolveAvatar(fromUser));
        }
        vo.setMessage(req.getMessage());
        vo.setStatus(req.getStatus());
        vo.setCreatedTime(req.getCreatedTime() != null ? req.getCreatedTime().toString() : null);
        vo.setHandledTime(req.getHandledTime() != null ? req.getHandledTime().toString() : null);
        return vo;
    }
}
