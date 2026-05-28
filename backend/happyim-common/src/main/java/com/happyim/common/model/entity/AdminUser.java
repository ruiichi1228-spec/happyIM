package com.happyim.common.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminUser {
    private Long id;
    private String username;
    private String password;
    private String nickname;
    private LocalDateTime createdTime;
}
