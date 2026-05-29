package com.happyim.common.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SystemAnnouncement {
    private Long id;
    private String content;
    private Long createdBy;
    private LocalDateTime createdTime;
}
