-- HappyIM 初始化数据库脚本

CREATE TABLE IF NOT EXISTS `user` (
    `id`              BIGINT PRIMARY KEY COMMENT '系统内部ID，号段生成器分配',
    `username`        VARCHAR(64) NOT NULL COMMENT '用户ID，5位以上字母数字下划线',
    `email`           VARCHAR(255) NOT NULL COMMENT '邮箱',
    `password`        VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    `nickname`        VARCHAR(100) NOT NULL COMMENT '展示昵称',
    `avatar_url`      VARCHAR(500) DEFAULT NULL COMMENT '头像URL',
    `gender`          TINYINT DEFAULT 0 COMMENT '性别: 0=未设置, 1=男, 2=女',
    `signature`       VARCHAR(60) DEFAULT NULL COMMENT '个人签名',
    `description`     VARCHAR(250) DEFAULT NULL COMMENT '其它说明',
    `email_verified`  TINYINT DEFAULT 0 COMMENT '邮箱是否已验证: 0=未验证, 1=已验证',
    `status`          TINYINT DEFAULT 1 COMMENT '账户状态: 0=禁用, 1=正常',
    `last_login_time` DATETIME DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip`   VARCHAR(50) DEFAULT NULL COMMENT '最后登录IP',
    `created_time`    DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`),
    INDEX `idx_created_time` (`created_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `id_segment` (
    `biz_tag`    VARCHAR(32) PRIMARY KEY COMMENT '业务标识',
    `max_id`     BIGINT NOT NULL COMMENT '当前已分配的最大ID',
    `step`       INT NOT NULL DEFAULT 1000 COMMENT '每次预分配的号段长度',
    `updated_at` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ID号段表';

INSERT IGNORE INTO id_segment (biz_tag, max_id, step) VALUES ('user', 20021228, 1000);
INSERT IGNORE INTO id_segment (biz_tag, max_id, step) VALUES ('group', 20021228, 1000);

-- 个人信息扩展字段（如已建表则手动执行）
-- ALTER TABLE user ADD COLUMN gender TINYINT DEFAULT 0 COMMENT '性别: 0=未设置, 1=男, 2=女';
-- ALTER TABLE user ADD COLUMN signature VARCHAR(60) DEFAULT NULL COMMENT '个人签名';
-- ALTER TABLE user ADD COLUMN description VARCHAR(250) DEFAULT NULL COMMENT '其它说明';

CREATE TABLE IF NOT EXISTS `friend_request` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `from_user_id`  BIGINT NOT NULL COMMENT '申请人ID',
    `to_user_id`    BIGINT NOT NULL COMMENT '被申请人ID',
    `message`       VARCHAR(255) COMMENT '申请留言',
    `status`        TINYINT DEFAULT 0 COMMENT '0=待处理 1=已同意 2=已拒绝',
    `handled_time`  DATETIME COMMENT '处理时间',
    `created_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_from` (`from_user_id`),
    INDEX `idx_to_status` (`to_user_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友申请表';

CREATE TABLE IF NOT EXISTS `friend` (
    `id`            BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`       BIGINT NOT NULL COMMENT '用户ID',
    `friend_id`     BIGINT NOT NULL COMMENT '好友ID',
    `remark`        VARCHAR(64) COMMENT '备注名',
    `is_starred`    TINYINT DEFAULT 0 COMMENT '是否星标',
    `created_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_friend_pair` (`user_id`, `friend_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='好友关系表';

CREATE TABLE IF NOT EXISTS `blacklist` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`         BIGINT NOT NULL COMMENT '拉黑者ID',
    `blocked_user_id` BIGINT NOT NULL COMMENT '被拉黑者ID',
    `reason`          VARCHAR(255),
    `created_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_block_pair` (`user_id`, `blocked_user_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='黑名单表';

CREATE TABLE IF NOT EXISTS `group_chat` (
    `id`              BIGINT PRIMARY KEY COMMENT '群ID，号段生成器分配',
    `name`            VARCHAR(128) NOT NULL COMMENT '群名称',
    `owner_id`        BIGINT NOT NULL COMMENT '群主ID',
    `avatar_url`      VARCHAR(500) COMMENT '群头像',
    `description`     VARCHAR(500) COMMENT '群简介',
    `notice`          TEXT COMMENT '群公告',
    `member_count`    INT DEFAULT 0 COMMENT '成员数',
    `max_members`     INT DEFAULT 500 COMMENT '上限',
    `allow_invite`    TINYINT DEFAULT 1 COMMENT '允许普通成员邀请:0=仅管理员,1=所有人',
    `status`          TINYINT DEFAULT 0 COMMENT '0=正常 1=已解散',
    `created_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊表';

CREATE TABLE IF NOT EXISTS `group_member` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `group_id`        BIGINT NOT NULL,
    `user_id`         BIGINT NOT NULL,
    `role`            TINYINT DEFAULT 3 COMMENT '1=群主 2=管理员 3=普通成员',
    `group_nickname`  VARCHAR(64) COMMENT '群内昵称',
    `muted_until`     DATETIME COMMENT '禁言截止时间',
    `joined_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_group_user` (`group_id`, `user_id`),
    INDEX `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群成员表';

CREATE TABLE IF NOT EXISTS `conversation` (
    `id`              VARCHAR(64) PRIMARY KEY COMMENT '会话ID: p_10001_10002 / g_10000',
    `type`            TINYINT NOT NULL COMMENT '0=私聊 1=群聊',
    `created_time`    DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_time`    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

CREATE TABLE IF NOT EXISTS `moment` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`     BIGINT NOT NULL,
    `content`     TEXT,
    `media_urls`  TEXT COMMENT 'JSON数组，图片URL列表',
    `visibility`  TINYINT DEFAULT 0 COMMENT '0=公开 1=仅好友',
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='朋友圈动态';

CREATE TABLE IF NOT EXISTS `moment_like` (
    `id`          BIGINT PRIMARY KEY AUTO_INCREMENT,
    `moment_id`   BIGINT NOT NULL,
    `user_id`     BIGINT NOT NULL,
    `created_at`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_moment_user` (`moment_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞';

CREATE TABLE IF NOT EXISTS `moment_comment` (
    `id`              BIGINT PRIMARY KEY AUTO_INCREMENT,
    `moment_id`       BIGINT NOT NULL,
    `user_id`         BIGINT NOT NULL,
    `content`         VARCHAR(500) NOT NULL,
    `reply_to_user_id` BIGINT COMMENT '回复谁',
    `created_at`      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_moment` (`moment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论';

CREATE TABLE IF NOT EXISTS `moment_notification` (
    `id`           BIGINT PRIMARY KEY AUTO_INCREMENT,
    `user_id`      BIGINT NOT NULL COMMENT '接收通知的用户',
    `from_user_id` BIGINT COMMENT '触发通知的用户',
    `moment_id`    BIGINT COMMENT '关联的动态ID',
    `type`         VARCHAR(20) NOT NULL COMMENT 'like/comment/reply',
    `content`      VARCHAR(255) COMMENT '通知内容',
    `is_read`      TINYINT DEFAULT 0,
    `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user` (`user_id`),
    INDEX `idx_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='朋友圈通知';
