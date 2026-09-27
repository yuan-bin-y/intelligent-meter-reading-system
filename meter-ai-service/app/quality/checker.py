"""图片质量检测。"""

from __future__ import annotations

import cv2
import numpy as np

from ..core.config import settings
from ..schemas.recognition import QualityReport

# 每项权重：清晰度 0.4 + 亮度 0.3 + 分辨率 0.2 + 长宽比 0.1
_W_SHARP, _W_BRIGHT, _W_RES, _W_ASPECT = 0.4, 0.3, 0.2, 0.1


def check_quality(image: np.ndarray) -> QualityReport:
    """检查 BGR 图像质量。"""
    h, w = image.shape[:2]
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)

    sharpness = float(cv2.Laplacian(gray, cv2.CV_64F).var())
    brightness = float(np.mean(gray)) / 255.0
    aspect = w / max(1, h)

    issues: list[str] = []
    suggestions: list[str] = []

    # 分辨率
    res_score = 1.0
    if min(h, w) < settings.min_resolution:
        issues.append("E_LOW_RESOLUTION")
        suggestions.append(f"图片分辨率过低（{w}x{h}），请靠近拍摄")
        res_score = max(0.0, min(h, w) / settings.min_resolution)

    # 清晰度
    blur = sharpness < settings.blur_threshold
    sharp_score = 1.0 if not blur else max(0.1, sharpness / settings.blur_threshold)
    if blur:
        issues.append("E_BLURRY")
        suggestions.append("画面模糊，请持稳手机或使用对焦后拍摄")

    # 亮度
    bright_score = 1.0
    if brightness < settings.dark_brightness:
        issues.append("E_TOO_DARK")
        suggestions.append("光线过暗，请开启补光或到明亮处拍摄")
        bright_score = max(0.1, brightness / settings.dark_brightness)
    elif brightness > settings.bright_brightness:
        issues.append("E_OVER_EXPOSED")
        suggestions.append("画面过曝，请避免反光方向或关闭闪光灯")
        bright_score = max(0.1, (1.0 - brightness) / (1.0 - settings.bright_brightness))

    # 长宽比
    aspect_score = 1.0
    if not (settings.min_aspect <= aspect <= settings.max_aspect):
        issues.append("E_BAD_ASPECT")
        suggestions.append("图片长宽比例异常，请检查拍摄角度")
        aspect_score = 0.3

    score = round(
        _W_SHARP * sharp_score + _W_BRIGHT * bright_score
        + _W_RES * res_score + _W_ASPECT * aspect_score, 4)

    return QualityReport(
        passed=len(issues) == 0,
        quality_score=score,
        quality_issues=issues,
        suggestions=suggestions,
        width=w,
        height=h,
        brightness=round(brightness, 4),
        sharpness=round(sharpness, 2),
    )


def decode_image(data: bytes) -> np.ndarray:
    """将图片字节解码为 BGR 图像。"""
    buf = np.frombuffer(data, dtype=np.uint8)
    img = cv2.imdecode(buf, cv2.IMREAD_COLOR)
    if img is None:
        raise ValueError("图片无法解码，请上传 JPG/PNG 格式照片")
    return img
