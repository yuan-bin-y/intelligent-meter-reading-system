"""表具类型判断。"""

from __future__ import annotations

import os

import cv2
import numpy as np

_WEIGHTS = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))), "models", "meter_classifier.onnx")

# ONNX 模型输出顺序
CLASSES = ("ELECTRIC", "WATER", "GAS")


def classifier_backend() -> str:
    return "onnx" if os.path.isfile(_WEIGHTS) else "heuristic-fallback"


def classify(dial_crop: np.ndarray) -> tuple[str, float]:
    """返回表具类型和置信度。"""
    if os.path.isfile(_WEIGHTS):
        try:
            return _classify_onnx(dial_crop)
        except Exception:  # 模型推理失败回退启发式
            pass
    return _classify_heuristic(dial_crop)


def _classify_onnx(crop: np.ndarray) -> tuple[str, float]:
    import onnxruntime as ort

    sess = ort.InferenceSession(_WEIGHTS, providers=["CPUExecutionProvider"])
    x = cv2.resize(crop, (224, 224)).astype(np.float32) / 255.0
    x = ((x - 0.5) / 0.5).transpose(2, 0, 1)[None]
    logits = sess.run(None, {sess.get_inputs()[0].name: x})[0][0]
    probs = np.exp(logits - logits.max())
    probs /= probs.sum()
    idx = int(np.argmax(probs))
    return CLASSES[idx], float(probs[idx])


def _classify_heuristic(crop: np.ndarray) -> tuple[str, float]:
    hsv = cv2.cvtColor(crop, cv2.COLOR_BGR2HSV)
    h, s, v = cv2.split(hsv)
    colored = (s > 60) & (v > 40)
    colored_frac = float(colored.mean())
    gray_panel = float(((s < 60) & (v > 120)).mean())

    # 灰白面板通常为电表。
    if colored_frac < 0.15 or gray_panel > 0.5:
        return "ELECTRIC", min(0.85, 0.55 + 0.4 * gray_panel)

    hues = h[colored].astype(np.int32)
    blue = np.sum((hues >= 90) & (hues <= 130))
    red = np.sum((hues <= 8) | (hues >= 165))
    yellow = np.sum((hues >= 15) & (hues <= 35))
    scores = {"WATER": blue * 1.5 + red * 0.6, "GAS": yellow * 1.8 + red * 0.3,
              "ELECTRIC": 1.0}
    best = max(scores, key=scores.get)  # type: ignore[arg-type]
    conf = float(min(0.85, 0.5 + scores[best] / max(1, int(colored.sum()))))
    return best, conf
