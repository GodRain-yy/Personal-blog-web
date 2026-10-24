package com.example.blog.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    SUCCESS(200, "success"),
    BAD_REQUEST(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),
    INTERNAL_ERROR(500, "服务器内部错误");

    private final Integer code;
    private final String message;
}
