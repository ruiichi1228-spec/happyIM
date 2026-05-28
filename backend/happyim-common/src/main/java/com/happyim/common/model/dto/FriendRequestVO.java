package com.happyim.common.model.dto;

public class FriendRequestVO {

    private Long id;
    private Long fromUserId;
    private String fromUsername;
    private String fromNickname;
    private String fromAvatarUrl;
    private String message;
    private Integer status;
    private String createdTime;
    private String handledTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getFromUserId() { return fromUserId; }
    public void setFromUserId(Long fromUserId) { this.fromUserId = fromUserId; }

    public String getFromUsername() { return fromUsername; }
    public void setFromUsername(String fromUsername) { this.fromUsername = fromUsername; }

    public String getFromNickname() { return fromNickname; }
    public void setFromNickname(String fromNickname) { this.fromNickname = fromNickname; }

    public String getFromAvatarUrl() { return fromAvatarUrl; }
    public void setFromAvatarUrl(String fromAvatarUrl) { this.fromAvatarUrl = fromAvatarUrl; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public String getCreatedTime() { return createdTime; }
    public void setCreatedTime(String createdTime) { this.createdTime = createdTime; }

    public String getHandledTime() { return handledTime; }
    public void setHandledTime(String handledTime) { this.handledTime = handledTime; }
}
