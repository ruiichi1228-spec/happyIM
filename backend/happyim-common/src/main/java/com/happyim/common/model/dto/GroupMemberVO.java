package com.happyim.common.model.dto;

public class GroupMemberVO {
    private Long userId;
    private String username;
    private String nickname;
    private String avatarUrl;
    private Integer role;
    private String groupNickname;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public Integer getRole() { return role; }
    public void setRole(Integer role) { this.role = role; }
    public String getGroupNickname() { return groupNickname; }
    public void setGroupNickname(String groupNickname) { this.groupNickname = groupNickname; }
}
