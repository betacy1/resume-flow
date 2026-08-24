package com.resumeflow.common;

import lombok.Getter;

/**
 * 数据冲突异常（乐观锁版本落后）：
 * 插件保存字段时提交的 version 落后于服务端，返回 HTTP 409 并携带服务端最新数据，
 * 由插件提示"拉取最新 / 覆盖保存"。
 */
@Getter
public class ConflictException extends RuntimeException {

    /** 服务端当前最新数据（随 409 响应体下发） */
    private final Object serverData;

    public ConflictException(String message, Object serverData) {
        super(message);
        this.serverData = serverData;
    }
}
