"""测试公共夹具。"""

from __future__ import annotations

import sys
from pathlib import Path

import os

os.environ.setdefault("AI_RECOGNIZER", "opencv")

import cv2
import numpy as np
import pytest
from fastapi.testclient import TestClient

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from app.main import app  # noqa: E402


def make_meter_image(reading: str = "003682", dark_digits: bool = False,
                     blur: bool = False, dark: bool = False,
                     size: tuple[int, int] = (640, 480)) -> np.ndarray:
    """生成七段码测试图片。"""
    bg, fg, win = (140, 140, 140) if not dark_digits else (190, 190, 60)
    if dark:
        bg = 15
    img = np.full((size[1], size[0], 3), bg, np.uint8)
    cv2.rectangle(img, (110, 150), (530, 270), win, -1)
    W, H, t = 40, 70, 9
    x0, y0, step = 145, 175, W + 16

    def seg(p1, p2, ox, oy):
        cv2.line(img, (p1[0] + ox, p1[1] + oy), (p2[0] + ox, p2[1] + oy), (fg,) * 3, t)

    from app.pipeline.reader import SEG_MAP

    for i, d in enumerate(reading.replace(".", "")):
        ox, oy = x0 + i * (W + 16), y0
        s = SEG_MAP[d]
        k = t // 2
        xl, xr = ox, ox + W
        top, mid, bot = oy, oy + H // 2, oy + H
        if 0 in s: seg((xl + k, top), (xr - k, top), 0, 0)
        if 6 in s: seg((xl + k, mid), (xr - k, mid), 0, 0)
        if 3 in s: seg((xl + k, bot), (xr - k, bot), 0, 0)
        if 5 in s: seg((xl, top + k), (xl, mid - k), 0, 0)
        if 1 in s: seg((xr, top + k), (xr, mid - k), 0, 0)
        if 4 in s: seg((xl, mid + k), (xl, bot - k), 0, 0)
        if 2 in s: seg((xr, mid + k), (xr, bot - k), 0, 0)
    if blur:
        img = cv2.GaussianBlur(img, (31, 31), 8)
    ok, buf = cv2.imencode(".jpg", img)
    assert ok
    return img


@pytest.fixture(name="client")
def fixture_client() -> TestClient:
    return TestClient(app)


@pytest.fixture(name="meter_png")
def fixture_meter_png() -> bytes:
    ok, buf = cv2.imencode(".png", make_meter_image())
    assert ok
    return buf.tobytes()


@pytest.fixture(name="blurry_jpg")
def fixture_blurry_jpg() -> bytes:
    ok, buf = cv2.imencode(".jpg", make_meter_image(blur=True))
    assert ok
    return buf.tobytes()


@pytest.fixture(name="dark_jpg")
def fixture_dark_jpg() -> bytes:
    ok, buf = cv2.imencode(".jpg", make_meter_image(dark=True))
    assert ok
    return buf.tobytes()
