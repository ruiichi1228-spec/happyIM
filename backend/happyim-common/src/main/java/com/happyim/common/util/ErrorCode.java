package com.happyim.common.util;

public enum ErrorCode {

    SUCCESS(0, "success"),
    PARAM_ERROR(10001, "参数错误"),
    NOT_LOGIN(10002, "未登录或Token已过期"),
    FORBIDDEN(10003, "权限不足"),
    NOT_FOUND(10004, "资源不存在"),
    DUPLICATE_OPERATION(10005, "操作过于频繁，请稍后再试"),
    EMAIL_ALREADY_REGISTERED(20001, "邮箱已被注册"),
    BAD_CREDENTIALS(20002, "邮箱或密码错误"),
    CODE_ERROR(20003, "验证码错误或已过期"),
    EMAIL_NOT_VERIFIED(20004, "请先验证邮箱"),
    ACCOUNT_DISABLED(20005, "账户已被禁用"),
    REFRESH_TOKEN_INVALID(20006, "Refresh Token已过期或无效"),
    USERNAME_ALREADY_EXISTS(20007, "用户名已存在"),
    USERNAME_FORMAT_ERROR(20008, "用户名格式不正确，需5位以上字母数字下划线"),
    ALREADY_FRIEND(30001, "对方已是你的好友"),
    BLOCKED_BY_USER(30003, "对方已将你拉黑"),
    CANNOT_OPERATE_SELF(30004, "不能对自己操作"),
    INTERNAL_ERROR(50001, "服务器内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
