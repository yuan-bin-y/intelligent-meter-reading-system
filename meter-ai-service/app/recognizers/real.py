"""OpenCV 表盘识别流水线。"""

from __future__ import annotations

import numpy as np

from ..core.config import settings
from ..core.debug import Tracer
from ..pipeline import classify, detect, rules
from ..pipeline.preprocess import preprocess
from ..pipeline.reader import DialReader
from ..pipeline.rectify import deskew
from ..quality.checker import check_quality
from ..schemas.recognition import RecognitionResult
from .base import MeterRecognizer

_reader = DialReader()


class PipelineRecognizer(MeterRecognizer):
    """OpenCV 基线识别器。"""

    name = "real-cv"
    version = "cv-baseline-v1"
    default_meter_type = "ELECTRIC"
    label = "real-cv"

    def recognize(self, image: np.ndarray,
                  meter_type_hint: str | None = None,
                  dial_type_hint: str | None = None,
                  previous_reading: str | None = None) -> RecognitionResult:
        t0 = self.now_ms()
        quality = check_quality(image)

        processed = preprocess(image)
        processed = deskew(processed)
        box, det_conf = detect.detect_dial(processed)
        dial = processed[box[1]:box[3], box[0]:box[2]]

        # 按需保存调试图片。
        tracer = Tracer()
        tracer.save("01_input", image)
        tracer.save("02_preprocessed", processed)
        tracer.save("03_dial_crop", dial)
        tracer.log(f"表盘检测: bbox={box} conf={det_conf:.2f}")

        if meter_type_hint in ("ELECTRIC", "WATER", "GAS"):
            meter_type, type_conf = meter_type_hint, 1.0
        else:
            meter_type, type_conf = classify.classify(dial)
        tracer.log(f"表型分类: {meter_type} ({type_conf:.2f})")

        raw_reading, char_conf, style = _reader.read(dial, meter_type, tracer=tracer)
        dial_type = "POINTER" if style == "pointer" else (
            dial_type_hint if dial_type_hint in ("DIGITAL", "MECHANICAL") else "DIGITAL")

        check = rules.validate_reading(raw_reading, meter_type, previous_reading)
        reading = check.reading
        issues = quality.quality_issues + check.issues

        confidence = round(det_conf * char_conf, 4)
        needs_review = (
            reading is None
            or confidence < settings.review_threshold
            or any(i.startswith("E_") for i in issues)
        )
        message = "识别成功" if reading else (
            f"未能读出读数（失败阶段: {_reader._fail_stage or '未知'}），请人工复核或重拍")
        result = RecognitionResult(
            meter_type=meter_type,
            dial_type=dial_type,
            reading=reading,
            unit=rules.unit_for(meter_type),
            confidence=confidence,
            quality_score=quality.quality_score,
            quality_issues=issues,
            needs_review=needs_review,
            model_name=f"smart-meter-baseline-{self.label}",
            model_version="cv-baseline-v1",
            inference_time_ms=self.now_ms() - t0,
            message=message,
        )
        tracer.dump()
        return result


class ElectricMeterRecognizer(PipelineRecognizer):
    default_meter_type = "ELECTRIC"
    label = "real-electric"


class WaterMeterRecognizer(PipelineRecognizer):
    default_meter_type = "WATER"
    label = "real-water"


class GasMeterRecognizer(PipelineRecognizer):
    default_meter_type = "GAS"
    label = "real-gas"
