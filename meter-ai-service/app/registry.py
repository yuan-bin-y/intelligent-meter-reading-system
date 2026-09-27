"""识别器注册与模型信息。"""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from .core.config import settings
from .pipeline import classify, detect
from .pipeline import reader as reader_mod
from .recognizers.base import MeterRecognizer


class RecognizerInitError(RuntimeError):
    """识别器初始化失败。"""


@lru_cache(maxsize=1)
def get_recognizer() -> MeterRecognizer:
    """返回进程内共享的识别器。"""
    kind = settings.effective_recognizer()
    if kind == "mock":
        from .recognizers.mock import MockMeterRecognizer

        return MockMeterRecognizer()
    if kind == "opencv":
        from .recognizers.real import ElectricMeterRecognizer

        return ElectricMeterRecognizer()
    if kind == "paddleocr":
        from .recognizers.paddle_ocr import PaddleOCRMeterRecognizer, RecognizerInitError

        recognizer = PaddleOCRMeterRecognizer()
        try:
            recognizer._ensure_engine()
        except RecognizerInitError as e:
            raise RecognizerInitError(str(e)) from e
        return recognizer
    raise RecognizerInitError(f"未知识别器类型: {kind}（支持 mock/opencv/paddleocr）")


def recognizer_kind() -> str:
    return settings.effective_recognizer()


@lru_cache(maxsize=1)
def list_models() -> list[dict]:
    """返回当前模型信息。"""
    models: list[dict] = []
    kind = recognizer_kind()
    if kind == "mock":
        models.append({
            "name": "smart-meter-baseline", "version": "mock-v1",
            "meterTypes": ["ELECTRIC", "WATER", "GAS"], "format": "heuristic",
            "sizeBytes": 0, "status": "READY",
            "accuracyNote": "模拟识别器，准确率待测试",
        })
    elif kind == "opencv":
        models.append({
            "name": "smart-meter-cv-baseline", "version": "cv-baseline-v1",
            "meterTypes": ["ELECTRIC", "WATER", "GAS"], "format": "heuristic",
            "sizeBytes": 0, "status": "READY",
            "accuracyNote": "OpenCV 七段码基线，准确率待测试",
        })
    else:
        model_ref = (Path(settings.paddle_model_path).name
                     if settings.paddle_model_path else settings.paddle_model_name)
        models.append({
            "name": model_ref, "version": "PP-OCRv5-mobile-yolo-crop-v1",
            "meterTypes": ["ELECTRIC", "WATER", "GAS"], "format": "paddle",
            "sizeBytes": _model_cache_size(), "status": "READY",
            "accuracyNote": "M2021同源保留集9/10；独立清晰集2/5、复杂集3/10",
        })
    for name, path, version in (
        ("meter-classifier", classify._WEIGHTS, "cls-v1"),
        ("dial-detector", detect._DETECTOR_PATH, "det-v1"),
        ("display-detector", detect._DISPLAY_DETECTOR_PATH, "yolo-display-v1"),
        ("digit-reader", reader_mod._DIGIT_MODEL, "read-v1"),
    ):
        if path and os.path.isfile(path):
            models.append({
                "name": name, "version": version,
                "meterTypes": ["ELECTRIC", "WATER", "GAS"], "format": "onnx",
                "sizeBytes": os.path.getsize(path), "status": "READY",
                "accuracyNote": "待测试",
            })
    return models


def _model_cache_size() -> int:
    """统计 PaddleOCR 模型缓存目录大小（~/.paddlex）。"""
    cache = Path.home() / ".paddlex"
    total = 0
    if cache.exists():
        for p in cache.rglob("*"):
            if p.is_file():
                total += p.stat().st_size
    return total
