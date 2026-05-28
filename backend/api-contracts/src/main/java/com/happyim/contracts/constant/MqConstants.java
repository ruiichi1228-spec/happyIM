package com.happyim.contracts.constant;

/**
 * RabbitMQ 常量 — 所有服务共享
 */
public final class MqConstants {

    private MqConstants() {}

    // ==================== Exchange ====================
    public static final String EXCHANGE = "happyim.exchange";

    // ==================== Routing Keys ====================
    /** 聊天消息推送（chat-service → chat-ws）*/
    public static final String ROUTE_CHAT_MESSAGE = "chat.message";
    /** 系统消息（user/group → chat-service）*/
    public static final String ROUTE_SYSTEM_MESSAGE = "system.message";
    /** 好友通知（user-service → chat-ws）*/
    public static final String ROUTE_FRIEND_NOTIFY = "notify.friend";
    /** 朋友圈通知（content-service → chat-ws）*/
    public static final String ROUTE_MOMENT_NOTIFY = "notify.moment";
    /** 广场通知（content-service → chat-ws）*/
    public static final String ROUTE_SQUARE_NOTIFY = "notify.square";
    /** 文件记录（chat-service → content-service）*/
    public static final String ROUTE_FILE_RECORD = "file.record";

    // ==================== Queues ====================
    public static final String QUEUE_CHAT_WS = "happyim:chat:ws-1";
    public static final String QUEUE_SYSTEM_MESSAGE = "happyim:system:message";
    public static final String QUEUE_FILE_RECORD = "happyim:file:record";
}
