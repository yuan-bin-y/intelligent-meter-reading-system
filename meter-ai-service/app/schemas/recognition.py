"""识别接口响应模型。"""

from __future__ import annotations

from typing import Optional

from pydantic import BaseModel, Field


class QualityReport(BaseModel):
    """图片质量检测结果。"""

    success: bool = True
    passed: bool = True
    quality_score: float = Field(1.0, alias="qualityScore")
    quality_issues: list[str] = Field(default_factory=list, alias="qualityIssues")
    suggestions: list[str] = Field(default_factory=list)
    width: int = 0
    height: int = 0
    brightness: float = 0.0
    sharpness: float = 0.0

    model_config = {"populate_by_name": True}


class RecognitionResult(BaseModel):
    """识别结果。"""

    success: bool = True
    meter_type: str = Field("ELECTRIC", alias="meterType")
    dial_type: str = Field("DIGITAL", alias="dialType")
    reading: Optional[str] = None
    unit: str = "kWh"
    confidence: float = 0.0
    quality_score: float = Field(0.0, alias="qualityScore")
    quality_issues: list[str] = Field(default_factory=list, alias="qualityIssues")
    needs_review: bool = Field(True, alias="needsReview")
    processed_image_url: Optional[str] = Field(None, alias="processedImageUrl")
    model_name: str = Field("smart-meter-baseline", alias="modelName")
    model_version: str = Field("mock-v1", alias="modelVersion")
    inference_time_ms: int = Field(0, alias="inferenceTimeMs")
    message: str = "识别成功"

    # 模型诊断字段
    raw_text: Optional[str] = Field(None, alias="rawText")          # 未修改的 OCR 文本
    recognizer: str = Field("opencv", alias="recognizer")           # mock/opencv/paddleocr
    display_box: Optional[list[int]] = Field(None, alias="displayBox")  # 显示区域 [x,y,w,h]
    warnings: list[str] = Field(default_factory=list)               # 低置信度/定位失败/格式异常等
    fail_stage: Optional[str] = Field(None, alias="failStage")      # 失败阶段标记
    ocr_variants: list[dict] = Field(default_factory=list, alias="ocrVariants")  # 各预处理变体原始输出

    model_config = {"populate_by_name": True}


class ModelInfo(BaseModel):
    """模型版本信息。"""

    name: str
    version: str
    meter_types: list[str] = Field(default_factory=list, alias="meterTypes")
    format: str = "onnx"
    size_bytes: int = Field(0, alias="sizeBytes")
    status: str = "PLACEHOLDER"   # PLACEHOLDER / READY（有待测准确率时才可写 READY）
    accuracy_note: str = Field("待训练/待测试", alias="accuracyNote")

    model_config = {"populate_by_name": True}


class ErrorResponse(BaseModel):
    """业务失败响应（success=false + 错误码）。"""

    success: bool = False
    error_code: str = Field("E_UNKNOWN", alias="errorCode")
    message: str = "识别失败"

    model_config = {"populate_by_name": True}
