"""图片质量检测测试。"""

from __future__ import annotations

import cv2
import numpy as np

from app.quality.checker import check_quality, decode_image
from tests.conftest import make_meter_image


def test_decode_valid_image():
    ok, buf = cv2.imencode(".png", make_meter_image())
    assert ok
    img = decode_image(buf.tobytes())
    assert img.shape[0] == 480 and img.shape[2] == 3


def test_decode_invalid_image():
    import pytest

    with pytest.raises(ValueError):
        decode_image(b"not-an-image")


def test_sharp_image_passes():
    report = check_quality(make_meter_image())
    assert report.passed
    assert report.quality_score > 0.7
    assert "E_BLURRY" not in report.quality_issues


def test_blurry_image_flagged():
    report = check_quality(make_meter_image(blur=True))
    assert not report.passed
    assert "E_BLURRY" in report.quality_issues
    assert report.quality_score < 0.7


def test_dark_image_flagged():
    report = check_quality(make_meter_image(dark=True))
    assert "E_TOO_DARK" in report.quality_issues
    assert "建议" in "".join(report.suggestions) or "拍摄" in "".join(report.suggestions)


def test_low_resolution_flagged():
    small = cv2.resize(make_meter_image(), (180, 120))
    report = check_quality(small)
    assert "E_LOW_RESOLUTION" in report.quality_issues


def test_report_fields_present():
    report = check_quality(make_meter_image())
    data = report.model_dump(by_alias=True)
    assert {"qualityScore", "qualityIssues", "passed"} <= set(data)
    assert 0.0 <= data["qualityScore"] <= 1.0
