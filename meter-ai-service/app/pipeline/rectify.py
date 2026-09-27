"""表盘角度校正。"""

from __future__ import annotations

import cv2
import numpy as np


def deskew(img: np.ndarray, max_angle: float = 10.0) -> np.ndarray:
    """根据主方向校正小角度倾斜。"""
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY) if img.ndim == 3 else img
    edges = cv2.Canny(gray, 60, 160)
    lines = cv2.HoughLinesP(edges, 1, np.pi / 180, threshold=120,
                            minLineLength=min(img.shape[:2]) // 4, maxLineGap=12)
    if lines is None:
        return img
    angles = []
    for x1, y1, x2, y2 in lines.reshape(-1, 4):  # 兼容不同 OpenCV 版本的返回形状
        ang = np.degrees(np.arctan2(y2 - y1, x2 - x1))
        # 排除斜线对角度估计的干扰。
        ang = ((ang + 45) % 90) - 45
        angles.append(ang)
    if not angles:
        return img
    angle = float(np.median(angles))
    if abs(angle) < 0.3 or abs(angle) > max_angle:
        return img
    h, w = img.shape[:2]
    M = cv2.getRotationMatrix2D((w / 2, h / 2), angle, 1.0)
    return cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_CUBIC,
                          borderMode=cv2.BORDER_REPLICATE)


def perspective_correction(img: np.ndarray, quad_points: np.ndarray) -> np.ndarray:
    """根据四角坐标执行透视校正。"""
    pts = quad_points.astype(np.float32)
    w = int(max(np.linalg.norm(pts[0] - pts[1]), np.linalg.norm(pts[2] - pts[3])))
    h = int(max(np.linalg.norm(pts[0] - pts[3]), np.linalg.norm(pts[1] - pts[2])))
    dst = np.float32([[0, 0], [w, 0], [w, h], [0, h]])
    M = cv2.getPerspectiveTransform(pts, dst)
    return cv2.warpPerspective(img, M, (w, h))
