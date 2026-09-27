"""指针式表盘读数。"""

from __future__ import annotations

import cv2
import numpy as np


def read_pointer_dials(dial: np.ndarray) -> tuple[str | None, float]:
    """识别水平排列的子表盘。"""
    gray = cv2.cvtColor(dial, cv2.COLOR_BGR2GRAY) if dial.ndim == 3 else dial
    h, w = gray.shape
    blur = cv2.GaussianBlur(gray, (5, 5), 1)
    circles = cv2.HoughCircles(blur, cv2.HOUGH_GRADIENT, dp=1.2,
                               minDist=min(h, w) // 6, param1=110, param2=30,
                               minRadius=max(8, min(h, w) // 22),
                               maxRadius=min(h, w) // 5)
    if circles is None or len(circles[0]) < 3:
        return None, 0.0
    subs = sorted(circles[0][:6], key=lambda c: c[0])
    r_med = float(np.median([c[2] for c in subs]))
    subs = [c for c in subs if 0.6 * r_med <= c[2] <= 1.6 * r_med][:5]
    if len(subs) < 3 or float(np.std([c[1] for c in subs])) > 0.5 * r_med:
        return None, 0.0

    digits, confs = [], []
    for cx, cy, r in subs:
        d, cf = _pointer_value(gray, cx, cy, r)
        digits.append(d)
        confs.append(cf)
    value = "".join(digits)
    if not value.isdigit():
        return None, 0.0
    return value, float(np.mean(confs))


def _pointer_value(gray: np.ndarray, cx: float, cy: float, r: float) -> tuple[str, float]:
    r = int(r)
    roi = gray[max(0, int(cy) - r):int(cy) + r, max(0, int(cx) - r):int(cx) + r]
    if roi.size == 0:
        return "0", 0.1
    _, bw = cv2.threshold(roi, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
    contours, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    h, w = roi.shape
    best_angle, best_len = None, 0.0
    for c in contours:
        x, y, cw, ch = cv2.boundingRect(c)
        if cw > 0.9 * w or ch > 0.9 * h:
            continue
        M = cv2.moments(c)
        if M["m00"] == 0:
            continue
        ccx, ccy = M["m10"] / M["m00"], M["m01"] / M["m00"]
        if np.hypot(ccx - w / 2, ccy - h / 2) > 0.35 * r:
            continue
        (rcx, rcy), (rw, rh), ang = cv2.minAreaRect(c)
        length = max(rw, rh)
        if length < 0.5 * r:
            continue
        theta = np.deg2rad(ang if rw >= rh else ang + 90)
        dx, dy = np.cos(theta), np.sin(theta)
        vx, vy = rcx - w / 2, rcy - h / 2
        if vx * dx + vy * dy < 0:
            theta += np.pi
        best_angle, best_len = theta, length
    if best_angle is None:
        return "0", 0.1
    deg = (np.rad2deg(best_angle) + 90) % 360
    value = int((deg + 18) // 36) % 10
    return str(value), min(0.9, 0.4 + best_len / (2 * r))
