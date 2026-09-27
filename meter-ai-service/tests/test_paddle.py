"""PaddleOCR 识别器测试。"""

from __future__ import annotations

import os
import sys
from pathlib import Path

import cv2
import numpy as np
import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.recognizers.paddle_ocr import PaddleOCRMeterRecognizer, RecognizerInitError
from app.pipeline import rules


def test_reading_format_regex():
    assert rules.is_valid_reading_format("000092.17")
    assert rules.is_valid_reading_format("92.17")
    assert rules.is_valid_reading_format("0")
    assert rules.is_valid_reading_format("1234567890")          # 10 位整数
    assert rules.is_valid_reading_format("12,5")                 # 逗号在清洗前允许
    assert not rules.is_valid_reading_format("12.3.4")
    assert not rules.is_valid_reading_format("12ab")
    assert not rules.is_valid_reading_format("")
    assert not rules.is_valid_reading_format("12345678901")      # 11 位超限


def test_normalize_ocr_text():
    assert rules.normalize_ocr_text(" 12,5 ") == "12.5"
    assert rules.normalize_ocr_text(".- 0000") == "0000"
    assert rules.normalize_ocr_text("D00 n") == "D00n"           # 去空格；字母不擅改


def test_pick_best_candidate_prefers_valid_format():
    cands = [
        {"text": "1a", "score": 0.9, "box": [0, 0, 10, 10]},
        {"text": "000092.17", "score": 0.6, "box": [50, 0, 100, 10]},
    ]
    best = rules.pick_best_candidate(cands)
    assert best["text"] == "000092.17"  # 格式合法优先于高分


def test_normalize_keeps_leading_zeros():
    res = rules.validate_reading("000092.17", "ELECTRIC")
    assert res.ok and res.reading == "000092.17"


class FakeEngine:
    """PaddleOCR 3.x 单结果测试替身。"""

    def __init__(self, text: str, score: float) -> None:
        self.text, self.score = text, score

    def predict(self, image):
        class _R:
            json = {"res": {"rec_text": self.text, "rec_score": self.score,
                            "input_path": None, "page_index": None}}

        return [_R()]


class LowScoreEngine(FakeEngine):
    def __init__(self) -> None:
        super().__init__("92.17", 0.42)


def _make_recognizer_with(engine) -> PaddleOCRMeterRecognizer:
    rec = PaddleOCRMeterRecognizer()
    rec._engine = engine
    return rec


def _sample_clear_image() -> np.ndarray:
    img = cv2.imdecode(
        np.fromfile(str(ROOT / "samples" / "clear_meter_000092.17.png"),
                    dtype=np.uint8), cv2.IMREAD_COLOR)
    return img


@pytest.fixture(name="force_display_crop")
def fixture_force_display_crop(monkeypatch):
    """隔离 YOLO，只测试 OCR 结果处理。"""
    def whole_image_crop(image):
        height, width = image.shape[:2]
        return image, [0, 0, width, height], 1.0

    monkeypatch.setattr("app.recognizers.paddle_ocr.detect.crop_display",
                        whole_image_crop)


def test_low_confidence_marks_review(force_display_crop):
    rec = _make_recognizer_with(LowScoreEngine())
    r = rec.recognize(_sample_clear_image())
    assert r.raw_text == "92.17"
    assert r.confidence < 0.5
    assert r.needs_review
    assert "W_LOW_CONFIDENCE" in r.warnings


def test_valid_reading_extracted(force_display_crop):
    rec = _make_recognizer_with(FakeEngine("000092.17", 0.96))
    r = rec.recognize(_sample_clear_image())
    assert r.reading == "000092.17"
    assert r.raw_text == "000092.17"
    assert r.recognizer == "paddleocr"
    assert r.unit == "kWh"
    assert not r.needs_review


def test_empty_text_returns_failure(force_display_crop):
    rec = _make_recognizer_with(FakeEngine("", 0.0))
    r = rec.recognize(_sample_clear_image())
    assert r.reading is None
    assert "W_EMPTY_TEXT" in r.warnings
    assert r.needs_review


def test_invalid_format_not_silently_fixed(force_display_crop):
    rec = _make_recognizer_with(FakeEngine("D00 n", 0.8))
    r = rec.recognize(_sample_clear_image())
    # 非数字原文保留在 rawText 中。
    assert r.raw_text == "D00n"
    assert r.reading is None
    assert r.needs_review


def test_result_contract_fields(force_display_crop):
    rec = _make_recognizer_with(FakeEngine("000092.17", 0.9))
    r = rec.recognize(_sample_clear_image())
    data = r.model_dump(by_alias=True)
    for key in ("reading", "rawText", "recognizer", "elapsed" if False else "inferenceTimeMs",
                "displayBox", "warnings", "confidence", "unit", "meterType"):
        assert key in data, f"缺少契约字段 {key}"


def test_init_failure_raises_clear_error(monkeypatch):
    rec = PaddleOCRMeterRecognizer()

    def boom():
        raise ImportError("No module named 'paddleocr'")

    monkeypatch.setattr(rec, "_ensure_engine", boom)
    with pytest.raises(ImportError):
        rec.recognize(_sample_clear_image())


def test_init_error_message_cached():
    rec = PaddleOCRMeterRecognizer()
    rec._init_error = "PaddleOCR 初始化失败: 测试错误"  # 模拟一次失败后的缓存
    for _ in range(2):
        # 缓存初始化错误，避免重复加载。
        with pytest.raises(RecognizerInitError, match="测试错误"):
            rec._ensure_engine()


def test_real_paddle_inference():
    pytest.importorskip("paddleocr", reason="当前 Python 无 paddleocr（需 ≤3.13）")
    rec = PaddleOCRMeterRecognizer()
    r = rec.recognize(_sample_clear_image())
    # 样例只校验结果结构，不绑定具体读数。
    assert r.success in (True, False)
    assert r.raw_text is None or isinstance(r.raw_text, str)
    assert 0.0 <= r.confidence <= 1.0
    assert r.recognizer == "paddleocr"
