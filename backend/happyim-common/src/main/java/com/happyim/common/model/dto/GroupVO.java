package com.happyim.common.model.dto;

public class GroupVO {
    private Long groupId;
    private String name;
    private String avatarUrl;
    private Long ownerId;
    private Integer memberCount;
    private Integer myRole;

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getMyRole() { return myRole; }
    public void setMyRole(Integer myRole) { this.myRole = myRole; }
}
