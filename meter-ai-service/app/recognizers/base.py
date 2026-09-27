"""识别器公共接口。"""

from __future__ import annotations

import time
from abc import ABC, abstractmethod

import numpy as np

from ..pipeline.rules import unit_for
from ..schemas.recognition import RecognitionResult

MODEL_NAME = "smart-meter-baseline"


class MeterRecognizer(ABC):
    """表盘识别器。"""

    name: str = "base"
    version: str = "dev"

    @abstractmethod
    def recognize(self, image: np.ndarray,
                  meter_type_hint: str | None = None,
                  dial_type_hint: str | None = None,
                  previous_reading: str | None = None) -> RecognitionResult:
        """识别表盘图像。"""

    # 公共结果字段
    def build_result(self, *, meter_type: str, dial_type: str, reading: str | None,
                     char_conf: float, detector_conf: float,
                     quality_score: float, quality_issues: list[str],
                     review_threshold: float, inference_ms: int,
                     message: str = "识别成功") -> RecognitionResult:
        confidence = round(max(0.0, min(1.0, detector_conf * char_conf)), 4)
        needs_review = (
            reading is None
            or confidence < review_threshold
            or any(i.startswith("E_") for i in quality_issues)
        )
        return RecognitionResult(
            meter_type=meter_type,
            dial_type=dial_type,
            reading=reading,
            unit=unit_for(meter_type),
            confidence=confidence,
            quality_score=quality_score,
            quality_issues=quality_issues,
            needs_review=needs_review,
            model_name=f"{MODEL_NAME}-{self.name}",
            model_version=self.version,
            inference_time_ms=inference_ms,
            message=message,
        )

    @staticmethod
    def now_ms() -> int:
        return int(time.perf_counter() * 1000)
