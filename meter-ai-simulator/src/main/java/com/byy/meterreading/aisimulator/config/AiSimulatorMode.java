package com.byy.meterreading.aisimulator.config;

/** AI 模拟器的执行模式。 */
public enum AiSimulatorMode {

    /** 返回固定的成功识别结果。 */
    SUCCESS,

    /** 返回可被后端正常记录的业务识别失败。 */
    FAILURE,

    /** 模拟服务异常，用于验证 RabbitMQ 重试和死信队列。 */
    TRANSIENT_FAILURE
}
