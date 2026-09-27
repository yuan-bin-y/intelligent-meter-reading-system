"""YOLO 显示区定位与 PP-OCRv5 读数识别。"""

from __future__ import annotations

import json
import os
from pathlib import Path

import cv2
import numpy as np

from ..core.config import settings
from ..core.debug import Tracer
from ..pipeline import detect, rules
from ..pipeline.preprocess import preprocess
from ..pipeline.rectify import deskew
from ..quality.checker import check_quality
from ..schemas.recognition import RecognitionResult
from .base import MeterRecognizer


class RecognizerInitError(RuntimeError):
    """PaddleOCR 初始化失败。"""


class PaddleOCRMeterRecognizer(MeterRecognizer):
    """PP-OCRv5 识别器。"""

    name = "paddleocr"
    version = "pp-ocrv5-mobile-yolo-crop-v1"

    def __init__(self) -> None:
        self._engine = None
        self._init_error: str | None = None
        self._loaded_model_ref = settings.paddle_model_name

    def _ensure_engine(self):
        """延迟加载 PaddleOCR 模型。"""
        if self._engine is not None:
            return self._engine
        if self._init_error:
            raise RecognizerInitError(self._init_error)
        try:
            os.environ.setdefault("PADDLE_PDX_MODEL_SOURCE",
                                  settings.paddle_model_source)
            os.environ.setdefault("PADDLE_DEVICE", settings.paddle_device)
            site_dir = Path(__file__).resolve().parent.parent.parent
            for bin_dir in sorted(site_dir.glob("nvidia/*/bin")):
                os.add_dll_directory(str(bin_dir))
                os.environ["PATH"] = str(bin_dir) + os.pathsep + os.environ["PATH"]

            from paddleocr import TextRecognition

            model_path = settings.paddle_model_path.strip()
            if model_path:
                model_name = settings.paddle_model_name
                for config_name in ("config.yml", "inference.yml"):
                    config_path = Path(model_path) / config_name
                    if not config_path.exists():
                        continue
                    import yaml

                    config = yaml.safe_load(
                        config_path.read_text(encoding="utf-8")) or {}
                    model_name = (
                        config.get("Global", {}).get("model_name")
                        or config.get("Model", {}).get("name")
                        or model_name
                    )
                self._engine = TextRecognition(
                    model_name=model_name,
                    model_dir=model_path,
                    device=settings.paddle_device,
                )
                self._loaded_model_ref = model_path
            else:
                self._engine = TextRecognition(
                    model_name=settings.paddle_model_name,
                    device=settings.paddle_device,
                )
                self._loaded_model_ref = settings.paddle_model_name
            return self._engine
        except Exception as exc:  # noqa: BLE001
            self._init_error = (
                f"PaddleOCR 初始化失败: {exc}。请确认已安装 paddlepaddle 与 "
                f"paddleocr，且模型路径/名称有效。"
            )
            raise RecognizerInitError(self._init_error) from exc

    @staticmethod
    def _strip_ocr_inputs(crop: np.ndarray, _located=None,
                          _tracer: Tracer | None = None
                          ) -> list[tuple[str, np.ndarray]]:
        """生成与训练阶段一致的 OCR 输入图。"""
        base = crop
        if base.shape[0] < 64:
            scale = 64.0 / base.shape[0]
            base = cv2.resize(base, None, fx=scale, fy=scale,
                              interpolation=cv2.INTER_CUBIC)
        return [("color", base)]

    def recognize(self, image: np.ndarray,
                  meter_type_hint: str | None = None,
                  dial_type_hint: str | None = None,
                  previous_reading: str | None = None) -> RecognitionResult:
        start_ms = self.now_ms()
        tracer = Tracer()
        warnings: list[str] = []
        candidates: list[dict] = []
        variant_records: list[dict] = []
        chosen_input: np.ndarray | None = None
        display_box: list[int] | None = None

        engine = self._ensure_engine()
        quality = check_quality(image)
        tracer.save("01_original", image)

        processed = deskew(preprocess(image))
        tracer.save("03_perspective_corrected", processed)
        detected = detect.crop_display(processed)
        display_found = detected is not None

        if detected is None:
            warnings.append("W_DISPLAY_NOT_FOUND")
            tracer.log("YOLO 数字区定位失败；不把整图交给 PP-OCRv5")
        else:
            crop, processed_box, detector_confidence = detected
            x, y, width, height = processed_box
            scale_x = image.shape[1] / processed.shape[1]
            scale_y = image.shape[0] / processed.shape[0]
            display_box = [
                int(round(x * scale_x)), int(round(y * scale_y)),
                int(round(width * scale_x)), int(round(height * scale_y)),
            ]
            tracer.save("02_meter_roi", crop)
            tracer.log(
                f"YOLO 数字区 processedBox={processed_box} "
                f"displayBox={display_box} conf={detector_confidence:.3f}"
            )

            variants = self._strip_ocr_inputs(crop)
            chosen_input = variants[0][1]
            tracer.save("04_contrast_enhanced", chosen_input)
            tracer.save("05_ocr_input", chosen_input)
            for variant_name, variant_image in variants:
                tracer.save(f"05_ocr_input_{variant_name}", variant_image)
                try:
                    outputs = self._run_ocr(engine, variant_image)
                except Exception as exc:  # noqa: BLE001
                    tracer.log(f"裁剪图 OCR 变体 {variant_name} 异常: {exc}")
                    continue
                for text, score, candidate_box in outputs:
                    record = {
                        "variant": f"yolo_crop_{variant_name}",
                        "text": text,
                        "score": round(float(score), 4),
                    }
                    variant_records.append(record)
                    normalized = rules.normalize_ocr_text(text)
                    if rules.is_valid_reading_format(normalized):
                        candidates.append({
                            "variant": record["variant"], "text": text,
                            "score": float(score), "box": candidate_box,
                        })
            tracer.log(
                f"裁剪图候选: {json.dumps(variant_records, ensure_ascii=False)}")

        # 无合法读数时保留得分最高的原文，供人工复核。
        best = rules.pick_best_candidate(candidates) if candidates else None
        if best is None and variant_records:
            best = rules.pick_best_candidate(variant_records)
        raw_text = rules.normalize_ocr_text(best["text"]) if best else None
        ocr_score = float(best["score"]) if best else 0.0
        tracer.log(
            f"选中候选: {raw_text!r} score={ocr_score:.2f} "
            f"variant={best.get('variant') if best else None}"
        )

        meter_type = (meter_type_hint or "ELECTRIC").upper()
        if meter_type not in ("ELECTRIC", "WATER", "GAS"):
            meter_type = "ELECTRIC"
        dial_type = (dial_type_hint or "DIGITAL").upper()
        if dial_type not in ("DIGITAL", "MECHANICAL", "POINTER"):
            dial_type = "DIGITAL"

        check = rules.validate_reading(raw_text, meter_type, previous_reading)
        reading = check.reading
        issues = quality.quality_issues + [
            issue for issue in check.issues if issue.startswith("E_")]
        if not raw_text:
            warnings.append("W_EMPTY_TEXT")
        elif reading is None:
            warnings.append("W_INVALID_FORMAT")
        if ocr_score and ocr_score < settings.ocr_confidence_threshold:
            warnings.append("W_LOW_CONFIDENCE")
        if "W_READING_DROP" in check.issues:
            warnings.append("W_READING_DROP")

        confidence = (
            round(ocr_score * (0.9 + 0.1 * quality.quality_score), 4)
            if raw_text else 0.0
        )
        needs_review = (
            reading is None
            or confidence < settings.review_threshold
            or ocr_score < settings.ocr_confidence_threshold
            or any(code in ("W_DISPLAY_NOT_FOUND", "W_INVALID_FORMAT")
                   for code in warnings)
        )

        fail_stage = None
        if reading is None:
            if not display_found:
                fail_stage = "DISPLAY_LOCALIZATION_FAILED"
            elif not raw_text:
                fail_stage = "OCR_EMPTY"
            elif "W_INVALID_FORMAT" in warnings:
                fail_stage = "INVALID_FORMAT"
            else:
                fail_stage = "LOW_CONFIDENCE"

        result = RecognitionResult(
            success=bool(reading) or bool(raw_text),
            meter_type=meter_type,
            dial_type=dial_type,
            reading=reading,
            unit=rules.unit_for(meter_type),
            confidence=confidence,
            quality_score=quality.quality_score,
            quality_issues=issues,
            needs_review=needs_review,
            model_name=f"paddleocr-{Path(self._loaded_model_ref).name}",
            model_version=self.version,
            inference_time_ms=self.now_ms() - start_ms,
            message="识别成功" if reading else (
                "未得到有效读数（" + "、".join(warnings) + "）"),
            raw_text=raw_text,
            recognizer="paddleocr",
            display_box=display_box,
            warnings=warnings,
            fail_stage=fail_stage,
            ocr_variants=variant_records,
        )
        if chosen_input is not None:
            tracer.save(
                "06_result_visualized",
                self._visualize(chosen_input, raw_text, ocr_score),
            )
        tracer.log(
            f"最终结果 reading={reading} conf={confidence} "
            f"warnings={warnings} failStage={fail_stage}"
        )
        self._dump_result_json(tracer, result)
        return result

    @staticmethod
    def _run_ocr(engine, image_bgr: np.ndarray
                 ) -> list[tuple[str, float, list[int] | None]]:
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
                        box = [int(value) for value in
                               np.asarray(boxes[index]).flatten()[:4]]
                    except Exception:  # noqa: BLE001
                        box = None
                candidates.append((text, score, box))
        return candidates

    @staticmethod
    def _visualize(ocr_input: np.ndarray, text: str | None,
                   score: float) -> np.ndarray:
        visual = ocr_input.copy()
        pad = max(6, visual.shape[0] // 8)
        visual = cv2.copyMakeBorder(
            visual, pad, pad, 8, 8, cv2.BORDER_CONSTANT, value=(30, 30, 30))
        cv2.putText(visual, f"{text or '-'}  {score:.2f}",
                    (8, visual.shape[0] - 6), cv2.FONT_HERSHEY_SIMPLEX,
                    0.7, (0, 255, 120), 2)
        return visual

    @staticmethod
    def _dump_result_json(tracer: Tracer, result: RecognitionResult) -> None:
        if not tracer.enabled:
            return
        path = Path(tracer.dir) / "result.json"
        path.write_text(
            json.dumps(result.model_dump(by_alias=True), ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        tracer.log(f"结果 JSON → {path}")
