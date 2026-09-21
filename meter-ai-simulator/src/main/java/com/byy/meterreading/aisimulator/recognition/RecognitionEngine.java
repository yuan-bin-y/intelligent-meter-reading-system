package com.byy.meterreading.aisimulator.recognition;

import com.byy.meterreading.aisimulator.mq.RecognitionTaskMessage;

/** 视觉识别引擎边界；真实模型接入时实现该接口即可。 */
public interface RecognitionEngine {

    RecognitionOutcome recognize(RecognitionTaskMessage task);
}
