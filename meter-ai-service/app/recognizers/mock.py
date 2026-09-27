"""用于接口联调的模拟识别器。"""

from __future__ import annotations

import hashlib

import numpy as np

from ..core.config import settings
from ..quality.checker import check_quality
from ..schemas.recognition import RecognitionResult
from .base import MeterRecognizer


class MockMeterRecognizer(MeterRecognizer):
    name = "mock"
    version = "mock-v1"

    def recognize(self, image: np.ndarray,
                  meter_type_hint: str | None = None,
                  dial_type_hint: str | None = None,
                  previous_reading: str | None = None) -> RecognitionResult:
        t0 = self.now_ms()
        quality = check_quality(image)

        digest = hashlib.sha1(np.ascontiguousarray(image).tobytes()).digest()
        seed = int.from_bytes(digest[:4], "big")
        integer_part = seed % 1_000_000
        decimal_part = (seed >> 20) % 100
        reading = f"{integer_part:06d}.{decimal_part:02d}"

        # 用质量分覆盖低置信度分支。
        confidence = round(0.45 + 0.52 * quality.quality_score, 4)

        meter_type = meter_type_hint if meter_type_hint in ("ELECTRIC", "WATER", "GAS") \
            else "ELECTRIC"
        dial_type = dial_type_hint if dial_type_hint in ("DIGITAL", "MECHANICAL", "POINTER") \
            else "DIGITAL"

        result = self.build_result(
            meter_type=meter_type, dial_type=dial_type, reading=reading,
            char_conf=1.0, detector_conf=confidence,
            quality_score=quality.quality_score,
            quality_issues=quality.quality_issues,
            review_threshold=settings.review_threshold,
            inference_ms=self.now_ms() - t0,
        )
        result.message = "模拟识别结果（AI_MODE=mock，未加载真实模型）"
        return result
