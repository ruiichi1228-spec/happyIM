package com.happyim.common.model.dto;

import java.util.List;

public class GroupDetailVO {
    private Long groupId;
    private String name;
    private String avatarUrl;
    private String description;
    private String notice;
    private Long ownerId;
    private Integer memberCount;
    private Integer maxMembers;
    private Boolean allowInvite;
    private Integer myRole;
    private List<GroupMemberVO> members;
    private String createdTime;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNotice() { return notice; }
    public void setNotice(String notice) { this.notice = notice; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
    public Boolean getAllowInvite() { return allowInvite; }
    public void setAllowInvite(Boolean allowInvite) { this.allowInvite = allowInvite; }
    public Integer getMyRole() { return myRole; }
    public void setMyRole(Integer myRole) { this.myRole = myRole; }
    public List<GroupMemberVO> getMembers() { return members; }
    public void setMembers(List<GroupMemberVO> members) { this.members = members; }
    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }
}
