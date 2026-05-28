package com.happyim.common.model.entity;

import java.time.LocalDateTime;

public class GroupChat {
    private Long id;
    private String name;
    private Long ownerId;
    private String avatarUrl;
    private String description;
    private String notice;
    private Integer memberCount;
    private Integer maxMembers;
    private Integer allowInvite;
    private Integer status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getNotice() { return notice; }
    public void setNotice(String notice) { this.notice = notice; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
    public Integer getMaxMembers() { return maxMembers; }
    public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
    public Integer getAllowInvite() { return allowInvite; }
    public void setAllowInvite(Integer allowInvite) { this.allowInvite = allowInvite; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
    public LocalDateTime getUpdatedTime() { return updatedTime; }
    public void setUpdatedTime(LocalDateTime updatedTime) { this.updatedTime = updatedTime; }
}
