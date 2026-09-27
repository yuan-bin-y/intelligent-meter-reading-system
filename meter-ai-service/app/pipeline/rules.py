"""读数规则校验与置信度融合。"""

from __future__ import annotations

from dataclasses import dataclass, field

# 表具类型与单位
UNIT_BY_TYPE = {"ELECTRIC": "kWh", "WATER": "m³", "GAS": "m³"}


@dataclass
class RuleCheckResult:
    ok: bool
    issues: list[str] = field(default_factory=list)
    reading: str | None = None


def validate_reading(raw: str | None, meter_type: str,
                     previous_reading: str | None = None) -> RuleCheckResult:
    """校验读数格式，并检查读数是否下降。"""
    if not raw:
        return RuleCheckResult(False, ["E_EMPTY_READING"], None)
    issues: list[str] = []
    if raw.count(".") > 1:
        issues.append("E_MULTIPLE_DOTS")
    body = raw.replace(".", "")
    if not body.isdigit():
        issues.append("E_INVALID_CHARS")
    if raw.startswith("-"):
        issues.append("E_NEGATIVE")

    reading = raw if not issues else None
    if reading and previous_reading:
        try:
            if float(reading) < float(previous_reading):
                issues.append("W_READING_DROP")  # 警告级：交由 Java 预警规则处理
        except ValueError:
            pass
    ok = not [i for i in issues if i.startswith("E_")]
    return RuleCheckResult(ok, issues, reading)


def unit_for(meter_type: str) -> str:
    return UNIT_BY_TYPE.get(meter_type, "kWh")


def fuse_confidence(detector_conf: float, char_conf: float, quality_score: float) -> float:
    """置信度融合：检测 × 识别字符均分，再按质量分轻微折减。"""
    return round(max(0.0, min(1.0, detector_conf * char_conf * (0.9 + 0.1 * quality_score))), 4)


import re as _re

# 合理读数格式：1-10 位整数 + 可选小数（1-4 位）
READING_PATTERN = _re.compile(r"^\d{1,10}([.,]\d{1,4})?$")


def normalize_ocr_text(text: str) -> str:
    """清理 OCR 文本，不替换字母。"""
    cleaned = (text or "").strip().replace(" ", "").replace(",", ".")
    cleaned = cleaned.strip(".·・_—-")  # 去首尾装饰符
    return cleaned


def is_valid_reading_format(text: str) -> bool:
    return bool(READING_PATTERN.match(text or ""))


def pick_best_candidate(candidates: list[dict]) -> dict | None:
    """按格式、长度、置信度和位置选择候选读数。"""
    def sort_key(c: dict) -> tuple:
        text = normalize_ocr_text(c.get("text", ""))
        fmt = 1 if is_valid_reading_format(text) else 0
        digit_len = len(text.replace(".", ""))  # 数字位数多的候选信息更完整
        score = float(c.get("score", 0.0))
        box = c.get("box")
        pos = 0.0
        if box and c.get("region"):
            rx, ry, rw, rh = c["region"]
            cx, cy = box[0] + box[2] / 2, box[1] + box[3] / 2
            centered = 1.0 - (abs(cx - (rx + rw / 2)) / rw + abs(cy - (ry + rh / 2)) / rh) / 2
            pos = max(0.0, centered)
        return (fmt, digit_len, round(score, 2), round(pos, 2))

    if not candidates:
        return None
    return sorted(candidates, key=sort_key, reverse=True)[0]
