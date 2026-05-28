package com.happyim.common.model.entity;

import java.time.LocalDateTime;

public class Friend {

    private Long id;
    private Long userId;
    private Long friendId;
    private String remark;
    private Integer isStarred;
    private LocalDateTime createdTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getFriendId() { return friendId; }
    public void setFriendId(Long friendId) { this.friendId = friendId; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public Integer getIsStarred() { return isStarred; }
    public void setIsStarred(Integer isStarred) { this.isStarred = isStarred; }

    public LocalDateTime getCreatedTime() { return createdTime; }
    public void setCreatedTime(LocalDateTime createdTime) { this.createdTime = createdTime; }
}
