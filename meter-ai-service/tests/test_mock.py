"""Mock 识别器与规则校验测试。"""

from __future__ import annotations

import cv2

from app.pipeline.rules import unit_for, validate_reading
from app.recognizers.mock import MockMeterRecognizer
from tests.conftest import make_meter_image


def test_mock_deterministic_per_image():
    img = make_meter_image()
    r1 = MockMeterRecognizer().recognize(img)
    r2 = MockMeterRecognizer().recognize(img)
    assert r1.reading == r2.reading
    assert r1.confidence == r2.confidence
    assert r1.success


def test_mock_respects_type_hint():
    r = MockMeterRecognizer().recognize(make_meter_image(), meter_type_hint="WATER")
    assert r.meter_type == "WATER"
    assert r.unit == "m³"


def test_mock_low_quality_triggers_review():
    r = MockMeterRecognizer().recognize(make_meter_image(blur=True))
    assert "E_BLURRY" in r.quality_issues
    assert r.needs_review


def test_mock_result_schema():
    r = MockMeterRecognizer().recognize(make_meter_image())
    data = r.model_dump(by_alias=True)
    for key in ("meterType", "dialType", "reading", "unit", "confidence",
                "qualityScore", "qualityIssues", "needsReview", "modelName",
                "modelVersion", "inferenceTimeMs", "message"):
        assert key in data, f"缺少契约字段 {key}"
    assert data["modelVersion"] == "mock-v1"
    assert 0.0 <= data["confidence"] <= 1.0


def test_unit_mapping():
    assert unit_for("ELECTRIC") == "kWh"
    assert unit_for("WATER") == "m³"
    assert unit_for("GAS") == "m³"


def test_rules_valid_reading():
    res = validate_reading("00000.68", "ELECTRIC")
    assert res.ok and res.reading == "00000.68"  # 前导零保留


def test_rules_invalid_readings():
    assert not validate_reading("12.3.4", "ELECTRIC").ok
    assert not validate_reading("12a4", "ELECTRIC").ok
    assert not validate_reading("-5.0", "ELECTRIC").ok
    assert not validate_reading("", "ELECTRIC").ok


def test_rules_reading_drop_warning():
    res = validate_reading("10.00", "ELECTRIC", previous_reading="20.00")
    assert res.ok  # 下降不拦截，仅警告
    assert "W_READING_DROP" in res.issues
