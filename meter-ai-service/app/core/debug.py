"""识别流水线调试输出。"""

from __future__ import annotations

import logging
import os
from pathlib import Path

import cv2
import numpy as np

log = logging.getLogger("meter-debug")


class Tracer:
    """保存单次识别的中间图片和日志。"""

    def __init__(self) -> None:
        self.dir = os.environ.get("AI_DEBUG_DIR", "").strip()
        self.lines: list[str] = []
        self._index = 0
        if self.dir:
            Path(self.dir).mkdir(parents=True, exist_ok=True)

    @property
    def enabled(self) -> bool:
        return bool(self.dir)

    def save(self, tag: str, image: np.ndarray) -> None:
        """保存阶段图片。"""
        if not self.enabled or image is None:
            return
        self._index += 1
        path = Path(self.dir) / f"{self._index:02d}_{tag}.png"
        img = image
        if img.ndim == 2:  # 二值/灰度图转 3 通道便于叠加标注
            img = cv2.cvtColor(img, cv2.COLOR_GRAY2BGR)
        cv2.imwrite(str(path), img)
        self.log(f"图片 {path.name}")

    def draw_boxes(self, tag: str, image: np.ndarray,
                   boxes: list[tuple], color: tuple = (0, 200, 0)) -> None:
        """绘制候选框后保存，box 格式为 (x, y, w, h)。"""
        if not self.enabled or image is None:
            return
        vis = image.copy()
        if vis.ndim == 2:
            vis = cv2.cvtColor(vis, cv2.COLOR_GRAY2BGR)
        for (x, y, w, h) in boxes:
            cv2.rectangle(vis, (x, y), (x + w, y + h), color, 2)
        self.save(tag, vis)

    def save_chars(self, tag: str, binary: np.ndarray,
                   cells: list[tuple], chars: list[str], scores: list[float]) -> None:
        """保存字符切片和识别结果。"""
        if not self.enabled:
            return
        out_dir = Path(self.dir) / tag
        out_dir.mkdir(parents=True, exist_ok=True)
        for i, ((x, y, w, h), ch, sc) in enumerate(zip(cells, chars, scores)):
            patch = binary[max(0, y - 2):y + h + 2, max(0, x - 2):x + w + 2]
            if patch.size == 0:
                continue
            cv2.imwrite(str(out_dir / f"{i:02d}_x{x}_{ch}_{sc:.2f}.png"), patch)
        self.log(f"字符切片 {len(chars)} 个 → {tag}/")

    def log(self, message: str) -> None:
        self.lines.append(message)
        if self.enabled:
            log.info("[pipeline] %s", message)

    def dump(self) -> str:
        """保存并返回调试日志。"""
        if self.enabled:
            (Path(self.dir) / "trace.log").write_text(
                "\n".join(self.lines), encoding="utf-8")
        return "\n".join(self.lines)
