package com.happyim.common.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FriendRequestDTO {

    @NotNull(message = "toUserId不能为空")
    private Long toUserId;

    @NotBlank(message = "申请留言不能为空")
    private String message;

    public Long getToUserId() { return toUserId; }
    public void setToUserId(Long toUserId) { this.toUserId = toUserId; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
