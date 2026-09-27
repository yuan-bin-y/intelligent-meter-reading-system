"""推理图片预处理。"""

from __future__ import annotations

import cv2
import numpy as np


def resize_max_side(img: np.ndarray, max_side: int = 1280) -> np.ndarray:
    h, w = img.shape[:2]
    scale = max_side / max(h, w)
    if scale < 1.0:
        img = cv2.resize(img, (int(w * scale), int(h * scale)), interpolation=cv2.INTER_AREA)
    return img


def denoise(img: np.ndarray) -> np.ndarray:
    return cv2.medianBlur(img, 3)


def apply_clahe(img: np.ndarray) -> np.ndarray:
    """使用 CLAHE 增强亮度通道。"""
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    l = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8)).apply(l)
    return cv2.cvtColor(cv2.merge([l, a, b]), cv2.COLOR_LAB2BGR)


def preprocess(img: np.ndarray) -> np.ndarray:
    """缩放、去噪并增强暗光图片。"""
    img = resize_max_side(img)
    img = denoise(img)
    brightness = float(np.mean(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY))) / 255.0
    if brightness < 0.35:
        img = apply_clahe(img)
    return img
