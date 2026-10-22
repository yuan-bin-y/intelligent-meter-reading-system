package com.byy.meterreading.common.exception;

/** 回源容量已满或请求超时；客户端应退避重试，不能继续挤占数据库连接。 */
public class DatabaseBusyException extends RuntimeException {
    public DatabaseBusyException(String message) {
        super(message);
    }
}
