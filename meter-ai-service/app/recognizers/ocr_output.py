"""适配 PaddleOCR 输出，选择候选读数并保存调试结果。"""

from __future__ import annotations

import json
from pathlib import Path

import cv2
import numpy as np

from ..core.debug import Tracer
from ..pipeline import rules
from ..schemas.recognition import RecognitionResult


def run_ocr(engine, image_bgr: np.ndarray) -> list[tuple[str, float, list[int] | None]]:
    """调用 PaddleOCR 并统一 3.x 返回结构。"""
    candidates: list[tuple[str, float, list[int] | None]] = []
    for result in engine.predict(image_bgr):
        data = result.json.get("res", result.json) if hasattr(result, "json") else {}
        texts = data.get("rec_texts")
        scores = data.get("rec_scores")
        boxes = data.get("rec_boxes")
        if texts is None:
            texts = [data["rec_text"]] if data.get("rec_text") else []
            scores = [data.get("rec_score", 0.0)]
            boxes = [data["rec_box"]] if data.get("rec_box") else [None]
        for index, text in enumerate(texts):
            score = float(scores[index]) if index < len(scores) else 0.0
            box = None
            if boxes and index < len(boxes) and boxes[index] is not None:
                try:
                    box = [int(value) for value in np.asarray(boxes[index]).flatten()[:4]]
                except Exception:  # noqa: BLE001
                    box = None
            candidates.append((text, score, box))
    return candidates


def visualize(ocr_input: np.ndarray, text: str | None, score: float) -> np.ndarray:
    visual = ocr_input.copy()
    pad = max(6, visual.shape[0] // 8)
    visual = cv2.copyMakeBorder(visual, pad, pad, 8, 8, cv2.BORDER_CONSTANT, value=(30, 30, 30))
    cv2.putText(
        visual,
        f"{text or '-'}  {score:.2f}",
        (8, visual.shape[0] - 6),
        cv2.FONT_HERSHEY_SIMPLEX,
        0.7,
        (0, 255, 120),
        2,
    )
    return visual


def dump_result_json(tracer: Tracer, result: RecognitionResult) -> None:
    if not tracer.enabled:
        return
    path = Path(tracer.dir) / "result.json"
    path.write_text(
        json.dumps(result.model_dump(by_alias=True), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    tracer.log(f"结果 JSON → {path}")


def select_candidate(candidates: list[dict], variant_records: list[dict]):
    # 无合法读数时保留得分最高的原文，供人工复核。
    best = rules.pick_best_candidate(candidates) if candidates else None
    if best is None and variant_records:
        best = rules.pick_best_candidate(variant_records)
    raw_text = rules.normalize_ocr_text(best["text"]) if best else None
    score = float(best["score"]) if best else 0.0
    return best, raw_text, score
