package com.happyim.common.model.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SensitiveWord {
    private Long id;
    private String word;
    private LocalDateTime createdTime;
}
