"""表盘和数字显示区检测。"""

from __future__ import annotations

import os
from functools import lru_cache

import cv2
import numpy as np

_WEIGHTS_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))), "models")
_DETECTOR_PATH = os.path.join(_WEIGHTS_DIR, "dial_detector.onnx")
_DISPLAY_DETECTOR_PATH = os.path.join(_WEIGHTS_DIR, "display_detector.onnx")

# 当前检测模型的有效框分数较低，阈值按独立样本结果设置。
DISPLAY_CONFIDENCE = 0.01
DISPLAY_PAD_X_RATIO = 0.04
DISPLAY_PAD_Y_RATIO = 0.10


def detector_backend() -> str:
    return "onnx" if os.path.isfile(_DETECTOR_PATH) else "opencv-fallback"


def display_detector_backend() -> str:
    return "yolo-onnx" if os.path.isfile(_DISPLAY_DETECTOR_PATH) else "unavailable"


def detect_display(img: np.ndarray, confidence: float = 0.35,
                   iou_threshold: float = 0.45) -> list[tuple[int, int, int, int, float]]:
    """检测数字显示区，坐标基于原图。"""
    if not os.path.isfile(_DISPLAY_DETECTOR_PATH):
        return []
    return _detect_onnx(img, _DISPLAY_DETECTOR_PATH, confidence, iou_threshold)


def crop_display(img: np.ndarray,
                 confidence: float = DISPLAY_CONFIDENCE,
                 pad_x_ratio: float = DISPLAY_PAD_X_RATIO,
                 pad_y_ratio: float = DISPLAY_PAD_Y_RATIO,
                 min_height: int = 64,
                 ) -> tuple[np.ndarray, list[int], float] | None:
    """裁剪数字显示区，返回图片、位置和置信度。"""
    boxes = detect_display(img, confidence=confidence)
    if not boxes:
        return None

    x1, y1, x2, y2, score = boxes[0]
    pad_x = max(3, int(round((x2 - x1) * pad_x_ratio)))
    pad_y = max(3, int(round((y2 - y1) * pad_y_ratio)))
    x1 = max(0, x1 - pad_x)
    y1 = max(0, y1 - pad_y)
    x2 = min(img.shape[1], x2 + pad_x)
    y2 = min(img.shape[0], y2 + pad_y)
    if x2 <= x1 or y2 <= y1:
        return None

    crop = img[y1:y2, x1:x2].copy()
    if crop.shape[0] < min_height:
        scale = min_height / crop.shape[0]
        crop = cv2.resize(crop, None, fx=scale, fy=scale,
                          interpolation=cv2.INTER_CUBIC)
    return crop, [x1, y1, x2 - x1, y2 - y1], float(score)


def detect_dial(img: np.ndarray) -> tuple[tuple[int, int, int, int], float]:
    """返回 (表盘外接框 x1y1x2y2, 检测置信度)。"""
    if os.path.isfile(_DETECTOR_PATH):
        boxes = _detect_onnx(img, _DETECTOR_PATH)
        if boxes:
            return boxes[0][:4], float(boxes[0][4])
    return _detect_fallback(img)


@lru_cache(maxsize=2)
def _load_session(path: str):
    try:
        import onnxruntime as ort
    except ImportError:
        return None
    return ort.InferenceSession(path, providers=["CPUExecutionProvider"])


@lru_cache(maxsize=2)
def _load_cv_net(path: str):
    """使用 OpenCV DNN 加载 ONNX 模型。"""
    try:
        return cv2.dnn.readNetFromONNX(path)
    except cv2.error:
        return None


def _detect_onnx(img: np.ndarray, model_path: str,
                 confidence: float = 0.35,
                 iou_threshold: float = 0.45) -> list[tuple[int, int, int, int, float]]:
    """执行 YOLO ONNX 推理。"""
    sess = _load_session(model_path)
    h, w = img.shape[:2]
    if sess is not None:
        input_shape = sess.get_inputs()[0].shape
        input_h = input_shape[-2] if isinstance(input_shape[-2], int) else 640
        input_w = input_shape[-1] if isinstance(input_shape[-1], int) else 640
    else:
        # 显示区模型固定输入为 416×416。
        input_h = input_w = 416 if model_path == _DISPLAY_DETECTOR_PATH else 640
    scale = min(input_w / w, input_h / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    resized = cv2.resize(img, (nw, nh), interpolation=cv2.INTER_LINEAR)
    pad_x, pad_y = (input_w - nw) // 2, (input_h - nh) // 2
    canvas = np.full((input_h, input_w, 3), 114, dtype=np.uint8)
    canvas[pad_y:pad_y + nh, pad_x:pad_x + nw] = resized
    blob = cv2.dnn.blobFromImage(canvas, 1 / 255.0, (input_w, input_h),
                                 swapRB=True, crop=False)
    if sess is not None:
        out = sess.run(None, {sess.get_inputs()[0].name: blob})[0]
    else:
        net = _load_cv_net(model_path)
        if net is None:
            return []
        net.setInput(blob)
        out = net.forward()
    preds = np.squeeze(out)
    if preds.ndim != 2:
        return []
    if preds.shape[0] < preds.shape[1]:
        preds = preds.T
    candidates: list[tuple[int, int, int, int, float]] = []
    nms_boxes: list[list[int]] = []
    nms_scores: list[float] = []
    for row in preds:
        if row.shape[0] < 5:
            continue
        sc = float(np.max(row[4:]))
        if sc < confidence:
            continue
        cx, cy, bw, bh = map(float, row[:4])
        x1 = int(round((cx - bw / 2 - pad_x) / scale))
        y1 = int(round((cy - bh / 2 - pad_y) / scale))
        x2 = int(round((cx + bw / 2 - pad_x) / scale))
        y2 = int(round((cy + bh / 2 - pad_y) / scale))
        x1, y1 = max(0, min(w - 1, x1)), max(0, min(h - 1, y1))
        x2, y2 = max(0, min(w, x2)), max(0, min(h, y2))
        if x2 - x1 < 8 or y2 - y1 < 8:
            continue
        candidates.append((x1, y1, x2, y2, sc))
        nms_boxes.append([x1, y1, x2 - x1, y2 - y1])
        nms_scores.append(sc)
    if not candidates:
        return []
    indices = cv2.dnn.NMSBoxes(nms_boxes, nms_scores, confidence, iou_threshold)
    kept = [candidates[int(i)] for i in np.asarray(indices).reshape(-1)]
    return sorted(kept, key=lambda item: item[4], reverse=True)


def _detect_fallback(img: np.ndarray) -> tuple[tuple[int, int, int, int], float]:
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape
    edges = cv2.Canny(gray, 60, 160)
    circles = cv2.HoughCircles(
        cv2.GaussianBlur(gray, (9, 9), 2), cv2.HOUGH_GRADIENT, dp=1.5,
        minDist=min(h, w) // 2, param1=120, param2=55,
        minRadius=min(h, w) // 8, maxRadius=min(h, w) // 2)

    if circles is not None:
        for cx, cy, r in circles[0][:3]:
            support = _circle_edge_support(edges, cx, cy, r)
            if support < 0.45:
                continue
            rr = int(r * 1.12)
            box = (max(0, int(cx) - rr), max(0, int(cy) - rr),
                   min(w - 1, int(cx) + rr), min(h - 1, int(cy) + rr))
            return box, min(0.85, 0.35 + support * 0.5)

    # 圆形验证失败：矩形表盘 / 特写场景，整图兜底
    return (0, 0, w - 1, h - 1), 0.60


def _circle_edge_support(edges: np.ndarray, cx: float, cy: float, r: float,
                         samples: int = 180) -> float:
    """圆周落在 Canny 边缘（±2px）内的比例。"""
    hit = 0
    for t in np.linspace(0, 2 * np.pi, samples, endpoint=False):
        x = int(round(cx + r * np.cos(t)))
        y = int(round(cy + r * np.sin(t)))
        x1, x2 = max(0, x - 2), min(edges.shape[1], x + 3)
        y1, y2 = max(0, y - 2), min(edges.shape[0], y + 3)
        if x1 < x2 and y1 < y2 and edges[y1:y2, x1:x2].any():
            hit += 1
    return hit / samples
