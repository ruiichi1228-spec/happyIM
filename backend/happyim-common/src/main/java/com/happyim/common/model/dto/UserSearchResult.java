package com.happyim.common.model.dto;

public class UserSearchResult {

    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private Boolean isFriend;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Boolean getIsFriend() { return isFriend; }
    public void setIsFriend(Boolean isFriend) { this.isFriend = isFriend; }
}
