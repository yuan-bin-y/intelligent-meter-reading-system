"""视觉识别服务配置。"""

from __future__ import annotations

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    app_name: str = "smart-meter-ai-service"
    version: str = "1.0.0"

    # 兼容旧的 AI_MODE 配置。
    mode: str = "mock"            # env: AI_MODE
    # AI_RECOGNIZER 优先于 AI_MODE。
    recognizer: str = ""          # env: AI_RECOGNIZER
    host: str = "0.0.0.0"
    port: int = 8001              # env: AI_PORT
    log_level: str = "INFO"

    # Java 侧使用相同阈值判断是否需要复核。
    review_threshold: float = 0.60
    # OCR 自身的低置信度阈值。
    ocr_confidence_threshold: float = 0.70   # env: OCR_CONFIDENCE_THRESHOLD

    # 未指定本地目录时使用官方模型。
    paddle_model_name: str = "en_PP-OCRv5_mobile_rec"  # env: PADDLE_MODEL_NAME
    # 本地模型目录优先于 paddle_model_name。
    paddle_model_path: str = ""                         # env: PADDLE_MODEL_PATH
    paddle_device: str = "cpu"                          # env: PADDLE_DEVICE
    paddle_model_source: str = "BOS"                    # env: AI_PADDLE_MODEL_SOURCE
    # 图片质量检测阈值
    min_resolution: int = 200          # 最小边长
    blur_threshold: float = 60.0       # 拉普拉斯方差下限
    dark_brightness: float = 0.25      # 平均亮度下限
    bright_brightness: float = 0.93    # 平均亮度上限（过曝）
    min_aspect: float = 0.33           # 长宽比下限
    max_aspect: float = 3.0            # 长宽比上限

    model_config = {"env_prefix": "AI_", "env_file": ".env", "extra": "ignore"}

    # 非空时保存各阶段的中间结果。
    debug_dir: str = ""               # env: AI_DEBUG_DIR

    def effective_recognizer(self) -> str:
        """返回当前识别器名称。"""
        if self.recognizer:
            return self.recognizer.lower()
        return "mock" if self.mode == "mock" else "opencv"


settings = Settings()
