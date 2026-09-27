"""指针表识别器。"""

from __future__ import annotations

import numpy as np

from ..pipeline import pointer as pointer_pipeline
from ..schemas.recognition import RecognitionResult
from .base import MeterRecognizer


class PointerMeterRecognizer(MeterRecognizer):
    """使用 OpenCV 多子表盘算法识别指针表。"""

    name = "pointer-placeholder"
    version = "placeholder-v0"

    def recognize(self, image: np.ndarray,
                  meter_type_hint: str | None = None,
                  dial_type_hint: str | None = None,
                  previous_reading: str | None = None) -> RecognitionResult:
        t0 = self.now_ms()
        reading, conf = pointer_pipeline.read_pointer_dials(image)
        if reading is None:
            raise ValueError("E_POINTER_NOT_DETECTED")
        return self.build_result(
            meter_type=meter_type_hint or "WATER", dial_type="POINTER",
            reading=reading, char_conf=conf, detector_conf=0.6,
            quality_score=1.0, quality_issues=[],
            review_threshold=0.60, inference_ms=self.now_ms() - t0,
            message="指针式读数（CV 基线，建议人工复核）",
        )
