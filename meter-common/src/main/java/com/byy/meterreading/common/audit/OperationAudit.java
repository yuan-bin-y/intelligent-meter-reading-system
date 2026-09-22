package com.byy.meterreading.common.audit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要记录后台操作审计日志的方法。
 * 注解只描述固定的业务含义，当前用户、请求信息和执行结果由 AOP 在运行时补充。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationAudit {

    /** 所属业务模块，例如“设备管理”。 */
    String module();

    /** 执行的业务动作，例如“停用设备”。 */
    String action();

    /** 被操作的资源类型，例如 DEVICE；没有明确资源时可以留空。 */
    String resourceType() default "";

    /**
     * 从方法参数或返回值中获取资源 ID 的 SpEL 表达式，
     * 例如 #deviceId；留空时不记录资源 ID。
     */
    String resourceIdExpression() default "";

    /**
     * 是否记录经过脱敏和长度截断的请求参数。
     * 修改密码、重置密钥等敏感接口应设置为 false。
     */
    boolean recordParams() default true;
}
