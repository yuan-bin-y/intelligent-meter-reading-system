package com.byy.meterreading.aisimulator.config;

/** AI Worker 执行模式。 */
public enum AiSimulatorMode {

    /** 调用 Python 识别服务。 */
    MODEL,

    /** 返回固定的成功识别结果。 */
    SUCCESS,

    /** 返回业务识别失败。 */
    FAILURE,

    /** 模拟服务异常。 */
    TRANSIENT_FAILURE
}
