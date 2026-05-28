package com.happyim.api.service;

import com.happyim.common.util.BizException;
import com.happyim.common.util.ErrorCode;
import com.happyim.common.mapper.GroupChatMapper;
import com.happyim.common.mapper.GroupMemberMapper;
import com.happyim.common.mapper.UserMapper;
import com.happyim.common.model.dto.*;
import com.happyim.common.model.entity.GroupChat;
import com.happyim.common.model.entity.GroupMember;
import com.happyim.common.model.entity.User;
import com.happyim.common.service.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class GroupService {

    private static final Logger log = LoggerFactory.getLogger(GroupService.class);

    private final GroupChatMapper groupChatMapper;
    private final GroupMemberMapper groupMemberMapper;
    private final ConversationService conversationService;
    private final UserMapper userMapper;
    private final IdGenerator idGenerator;
    private final MessageService messageService;

    public GroupService(GroupChatMapper groupChatMapper, GroupMemberMapper groupMemberMapper,
                        ConversationService conversationService, UserMapper userMapper,
                        IdGenerator idGenerator, MessageService messageService) {
        this.groupChatMapper = groupChatMapper;
        this.groupMemberMapper = groupMemberMapper;
        this.conversationService = conversationService;
        this.userMapper = userMapper;
        this.idGenerator = idGenerator;
        this.messageService = messageService;
    }

    // ==================== 创建群 ====================

    @Transactional
    public GroupDetailVO createGroup(Long ownerId, CreateGroupRequest req) {
        // 生成群 ID
        Long groupId = idGenerator.nextGroupId();

        // 插入群
        GroupChat group = new GroupChat();
        group.setId(groupId);
        group.setName(req.getName());
        group.setOwnerId(ownerId);
        group.setMemberCount(1 + req.getMemberIds().size());
        group.setAllowInvite(1);
        groupChatMapper.insert(group);

        // 插入群主
        GroupMember owner = new GroupMember();
        owner.setGroupId(groupId);
        owner.setUserId(ownerId);
        owner.setRole(1);
        groupMemberMapper.insert(owner);

        // 批量插入成员
        if (!req.getMemberIds().isEmpty()) {
            List<GroupMember> members = new ArrayList<>();
            for (Long uid : req.getMemberIds()) {
                if (uid.equals(ownerId)) continue;
                GroupMember m = new GroupMember();
                m.setGroupId(groupId);
                m.setUserId(uid);
                m.setRole(3);
                members.add(m);
            }
            if (!members.isEmpty()) {
                groupMemberMapper.insertBatch(members);
            }
        }

        // 创建会话 + Redis 摘要（为所有初始成员）
        List<Long> allMemberIds = new ArrayList<>();
        allMemberIds.add(ownerId);
        allMemberIds.addAll(req.getMemberIds());
        conversationService.createGroupSession(groupId, req.getName(), allMemberIds);

        log.info("群创建成功: id={}, name={}, owner={}", groupId, req.getName(), ownerId);

        return buildDetail(group, ownerId);
    }

    // ==================== 群列表 ====================

    public List<GroupVO> getMyGroups(Long userId) {
        List<GroupChat> groups = groupChatMapper.findByMemberId(userId);
        List<GroupVO> vos = new ArrayList<>();
        for (GroupChat g : groups) {
            GroupMember me = groupMemberMapper.findByGroupAndUser(g.getId(), userId);
            GroupVO vo = new GroupVO();
            vo.setGroupId(g.getId());
            vo.setName(g.getName());
            vo.setAvatarUrl(resolveGroupAvatar(g.getAvatarUrl()));
            vo.setOwnerId(g.getOwnerId());
            vo.setMemberCount(g.getMemberCount());
            vo.setMyRole(me != null ? me.getRole() : 3);
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 群详情 ====================

    public GroupDetailVO getGroupDetail(Long groupId, Long userId) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null || group.getStatus() == 1) {
            throw new BizException(ErrorCode.NOT_FOUND, "群不存在或已解散");
        }
        return buildDetail(group, userId);
    }

    // ==================== 修改群信息 ====================

    public void updateGroupInfo(Long groupId, Long userId, Map<String, Object> body) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null) throw new BizException(ErrorCode.NOT_FOUND);

        GroupMember me = groupMemberMapper.findByGroupAndUser(groupId, userId);
        if (me == null || (me.getRole() != 1 && me.getRole() != 2)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅群主和管理员可修改群信息");
        }

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("id", groupId);
        if (body.get("name") != null) params.put("name", body.get("name"));
        if (body.get("description") != null) params.put("description", body.get("description"));
        if (body.get("notice") != null) params.put("notice", body.get("notice"));
        if (body.containsKey("allowInvite")) params.put("allowInvite", Boolean.TRUE.equals(body.get("allowInvite")) ? 1 : 0);
        if (body.get("avatarUrl") != null) params.put("avatarUrl", body.get("avatarUrl"));

        groupChatMapper.updateInfo(params);

        // 同步更新 Redis 会话摘要
        String updatedName = (String) body.get("name");
        String updatedAvatar = (String) body.get("avatarUrl");
        if (updatedName != null || updatedAvatar != null) {
            List<GroupMember> members = groupMemberMapper.findByGroupId(groupId);
            for (GroupMember m : members) {
                conversationService.updateMemberSessionInfo(m.getUserId(), groupId,
                        updatedName != null ? updatedName : group.getName(),
                        updatedAvatar != null ? updatedAvatar : group.getAvatarUrl());
            }
        }

        // 系统通知：只在值确实变化时发送
        if (updatedName != null && !updatedName.equals(group.getName())) {
            messageService.sendSystemMessage("g_" + groupId, 1, "群名称已修改为 \"" + updatedName + "\"", "name_change", "system");
        }
        String updatedNotice = (String) body.get("notice");
        if (updatedNotice != null && !updatedNotice.isEmpty() && !updatedNotice.equals(group.getNotice())) {
            messageService.sendSystemMessage("g_" + groupId, 1, updatedNotice, "announcement", "announcement");
        }
        log.info("群信息已更新: groupId={}", groupId);
    }

    // ==================== 邀请成员 ====================

    @Transactional
    public void addMembers(Long groupId, Long inviterId, List<Object> rawIds) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null || group.getStatus() == 1) throw new BizException(ErrorCode.NOT_FOUND);

        GroupMember inviter = groupMemberMapper.findByGroupAndUser(groupId, inviterId);
        if (inviter == null) throw new BizException(ErrorCode.FORBIDDEN, "你不是群成员");
        if (inviter.getRole() == 3 && group.getAllowInvite() == 0) {
            throw new BizException(ErrorCode.FORBIDDEN, "普通成员不允许邀请");
        }

        int added = 0;
        for (Object raw : rawIds) {
            Long uid = ((Number) raw).longValue();
            if (groupMemberMapper.findByGroupAndUser(groupId, uid) != null) continue;
            if (group.getMemberCount() + added >= group.getMaxMembers()) {
                throw new BizException(ErrorCode.DUPLICATE_OPERATION, "群人数已达上限");
            }
            GroupMember m = new GroupMember();
            m.setGroupId(groupId);
            m.setUserId(uid);
            m.setRole(3);
            groupMemberMapper.insert(m);
            added++;
        }

        if (added > 0) {
            groupChatMapper.updateMemberCount(groupId, added);
            for (Object raw : rawIds) {
                Long uid = ((Number) raw).longValue();
                if (groupMemberMapper.findByGroupAndUser(groupId, uid) != null) {
                    conversationService.initGroupMemberSession(groupId, group.getName(), uid);
                }
            }
        }
        for (Object raw : rawIds) {
            Long uid = ((Number) raw).longValue();
            if (groupMemberMapper.findByGroupAndUser(groupId, uid) != null) {
                User u = userMapper.findById(uid);
                messageService.sendSystemMessage("g_" + groupId, 1, (u != null ? u.getNickname() : uid) + " 加入了群聊", "member_join", "system");
            }
        }
        log.info("邀请成员: groupId={}, added={}", groupId, added);
    }

    // ==================== 移除成员 ====================

    @Transactional
    public void removeMember(Long groupId, Long operatorId, Long targetUserId) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null) throw new BizException(ErrorCode.NOT_FOUND);

        GroupMember operator = groupMemberMapper.findByGroupAndUser(groupId, operatorId);
        if (operator == null) throw new BizException(ErrorCode.FORBIDDEN);

        boolean isSelf = operatorId.equals(targetUserId);
        boolean isOwnerOrAdmin = operator.getRole() == 1 || operator.getRole() == 2;

        if (!isSelf && !isOwnerOrAdmin) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        if (isSelf && operator.getRole() == 1) {
            throw new BizException(ErrorCode.FORBIDDEN, "群主不能退出，请先转让群");
        }

        GroupMember target = groupMemberMapper.findByGroupAndUser(groupId, targetUserId);
        if (target == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不在群内");

        groupMemberMapper.deleteByGroupAndUser(groupId, targetUserId);
        groupChatMapper.updateMemberCount(groupId, -1);
        User removedUser = userMapper.findById(targetUserId);
        messageService.sendSystemMessage("g_" + groupId, 1, (removedUser != null ? removedUser.getNickname() : targetUserId) + " 被移出群聊", "member_leave", "system");
        log.info("移除成员: groupId={}, userId={}", groupId, targetUserId);
    }

    // ==================== 退出群 ====================

    @Transactional
    public void leaveGroup(Long groupId, Long userId) {
        removeMember(groupId, userId, userId);
    }

    // ==================== 解散群 ====================

    @Transactional
    public void dissolveGroup(Long groupId, Long userId) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!group.getOwnerId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅群主可解散群");
        }
        groupChatMapper.dissolve(groupId);
        log.info("群已解散: groupId={}", groupId);
    }

    // ==================== 设置管理员 ====================

    public void setRole(Long groupId, Long ownerId, Long targetUserId, Integer role) {
        GroupChat group = groupChatMapper.findById(groupId);
        if (group == null) throw new BizException(ErrorCode.NOT_FOUND);
        if (!group.getOwnerId().equals(ownerId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅群主可设置管理员");
        }
        if (role < 2 || role > 3) throw new BizException(ErrorCode.PARAM_ERROR, "角色无效");
        GroupMember target = groupMemberMapper.findByGroupAndUser(groupId, targetUserId);
        if (target == null) throw new BizException(ErrorCode.NOT_FOUND, "用户不在群内");
        if (target.getRole() == 1) throw new BizException(ErrorCode.FORBIDDEN, "不能修改群主角色");
        groupMemberMapper.updateRole(groupId, targetUserId, role);
        log.info("角色变更: groupId={}, userId={}, role={}", groupId, targetUserId, role);
    }

    // ==================== 内部 ====================

    private GroupDetailVO buildDetail(GroupChat group, Long userId) {
        GroupDetailVO vo = new GroupDetailVO();
        vo.setGroupId(group.getId());
        vo.setName(group.getName());
        vo.setAvatarUrl(resolveGroupAvatar(group.getAvatarUrl()));
        vo.setDescription(group.getDescription());
        vo.setNotice(group.getNotice());
        vo.setOwnerId(group.getOwnerId());
        vo.setMemberCount(group.getMemberCount());
        vo.setMaxMembers(group.getMaxMembers());
        vo.setAllowInvite(group.getAllowInvite() == null || group.getAllowInvite() == 1);
        vo.setCreatedTime(group.getCreatedTime() != null ? group.getCreatedTime().toString() : null);

        List<GroupMember> members = groupMemberMapper.findByGroupId(group.getId());
        List<GroupMemberVO> memberVOs = new ArrayList<>();
        for (GroupMember m : members) {
            User u = userMapper.findById(m.getUserId());
            GroupMemberVO mv = new GroupMemberVO();
            mv.setUserId(m.getUserId());
            if (u != null) {
                mv.setUsername(u.getUsername());
                mv.setNickname(u.getNickname());
                mv.setAvatarUrl(u.getAvatarUrl() != null && !u.getAvatarUrl().startsWith("http") ? "/api/files/avatar/" + u.getId() : u.getAvatarUrl());
            }
            mv.setRole(m.getRole());
            mv.setGroupNickname(m.getGroupNickname());
            memberVOs.add(mv);
            if (m.getUserId().equals(userId)) {
                vo.setMyRole(m.getRole());
            }
        }
        vo.setMembers(memberVOs);
        if (vo.getMyRole() == null) vo.setMyRole(0); // 不在群内
        return vo;
    }

    private String resolveGroupAvatar(String raw) {
        if (raw == null || raw.isBlank()) return null;
        if (raw.startsWith("http")) return raw;
        // MinIO path: happyim/images/xxx.jpg → /api/files/download/images/xxx.jpg
        if (raw.contains("/")) {
            return "/api/files/download/" + raw.substring(raw.indexOf("/") + 1);
        }
        return raw;
    }
}
