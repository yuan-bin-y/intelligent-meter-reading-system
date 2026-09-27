"""LCD、字轮和指针表读数识别。"""

from __future__ import annotations

import os

import cv2
import numpy as np

from ..core.debug import Tracer

_DIGIT_MODEL = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(
    os.path.abspath(__file__)))), "models", "digit_reader.onnx")



import cv2
import numpy as np

import os

DIGITS = "0123456789"

# 七段码段位定义（标准命名）：0=上 1=右上 2=右下 3=下 4=左下 5=左上 6=中
SEG_MAP = {
    "0": (0, 1, 2, 3, 4, 5),
    "1": (1, 2),
    "2": (0, 1, 6, 4, 3),
    "3": (0, 1, 6, 2, 3),
    "4": (5, 6, 1, 2),
    "5": (0, 5, 6, 2, 3),
    "6": (0, 5, 4, 3, 2, 6),
    "7": (0, 1, 2),
    "8": (0, 1, 2, 3, 4, 5, 6),
    "9": (0, 1, 2, 3, 5, 6),
}


class DialReader:
    def __init__(self) -> None:
        self.backend = "fallback"
        self._session = None
        onnx = _DIGIT_MODEL if os.path.isfile(_DIGIT_MODEL) else None
        if onnx:
            try:
                import onnxruntime as ort

                self._session = ort.InferenceSession(onnx, providers=["CPUExecutionProvider"])
                self.backend = "onnx"
            except Exception:
                self._session = None
        # 七段码匹配失败时使用通用字体模板。
        self._seg_templates = self._build_seven_seg_templates()
        self._alt_templates = self._build_font_templates()
        self._templates = self._seg_templates + self._alt_templates
        self._tracer = Tracer()      # 每次 read 可被覆盖
        self._fail_stage = ""

    def read(self, dial_crop: np.ndarray, meter_type: str,
             tracer: "Tracer | None" = None) -> tuple[str | None, float, str]:
        """返回读数、置信度和表盘类型。"""
        self._tracer = tracer or Tracer()
        self._fail_stage = ""
        digital = self._read_digit_row(dial_crop)
        if digital[0] is not None:
            return digital
        # 至少检测到三个子表盘才进入指针表分支。
        pointer = self._read_pointer_dials(dial_crop)
        if pointer[0] is not None:
            return pointer
        self._tracer.log(f"全部路径失败，最后阶段: {self._fail_stage or '未知'}")
        return None, 0.0, "digital"

    def find_display_strip(self, dial: np.ndarray) -> tuple[
            tuple[int, int, int, int], np.ndarray,
            list[tuple[int, int, int, int]], list[float]] | None:
        """定位数字条带和小数点候选区域。"""
        gray = cv2.cvtColor(dial, cv2.COLOR_BGR2GRAY) if dial.ndim == 3 else dial
        h, w = gray.shape
        _, otsu = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        binaries = [
            ("INV", cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                          cv2.THRESH_BINARY_INV, 41, 12)),
            ("BIN", cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                          cv2.THRESH_BINARY, 41, 12)),
            ("OTSU_BIN", otsu),
            ("OTSU_INV", 255 - otsu),
        ]
        klens = [("S", max(45, int(0.08 * max(h, w)))),
                 ("L", max(60, int(0.35 * max(h, w))))]
        best: tuple[float, list, np.ndarray] | None = None
        for _name, bw0 in binaries:
            for _kname, klen in klens:
                bw = bw0.copy()
                lines_h = cv2.morphologyEx(bw, cv2.MORPH_OPEN,
                                           cv2.getStructuringElement(cv2.MORPH_RECT, (klen, 1)))
                lines_v = cv2.morphologyEx(bw, cv2.MORPH_OPEN,
                                           cv2.getStructuringElement(cv2.MORPH_RECT, (1, klen)))
                bw = cv2.bitwise_and(bw, cv2.bitwise_not(cv2.bitwise_or(lines_h, lines_v)))
                bw = cv2.morphologyEx(bw, cv2.MORPH_CLOSE, np.ones((2, 2), np.uint8))
                bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
                for row in self._extract_rows(bw, h, w):
                    cells = row["cells"]
                    if not (3 <= len(cells) <= 12):
                        continue
                    heights = [b[3] for b in cells]
                    mean_h = float(np.mean(heights))
                    if mean_h < 12:
                        continue
                    uniformity = 1.0 - float(np.std(heights)) / mean_h
                    if uniformity < 0.5:
                        continue
                    total = (min(1.0, len(cells) / 8.0) * uniformity
                             * min(1.0, mean_h / (0.05 * h)))
                    if best is None or total > best[0]:
                        best = (total, cells, bw)

        if best is None:
            self._tracer.log("显示条带定位失败：无合格数字行")
            return None
        cells, bw = best[1], best[2]
        x1 = max(0, min(c[0] for c in cells) - 4)
        y1 = max(0, min(c[1] for c in cells) - 6)
        x2 = min(w, max(c[0] + c[2] for c in cells) + 4)
        y2 = min(h, max(c[1] + c[3] for c in cells) + 6)
        if x2 - x1 < 30 or y2 - y1 < 12:
            return None
        # 对比度增强后的灰度条带（CLAHE），供 OCR 引擎直接识别
        enhanced = cv2.createCLAHE(clipLimit=2.5, tileGridSize=(8, 8)).apply(gray[y1:y2, x1:x2])
        # 小数点候选：行基线附近的小连通域（紧贴行区域，供几何小数点插入）
        dots = []
        y_lo, y_hi = y1 - 4, y2 + 4
        for (x, y, dw, dh) in self._small_blobs(bw):
            if (x1 - 4 <= x and x + dw <= x2 + 4 and y_lo <= y and y + dh <= y_hi
                    and dw <= 0.4 * (x2 - x1) / max(1, len(cells))
                    and dh <= 0.4 * (y2 - y1)):
                dots.append((x, y, dw, dh))
        strip_w = x2 - x1
        centers_rel = [round(((c[0] + c[2] / 2) - x1) / strip_w, 4) for c in cells]
        return ((int(x1), int(y1), int(strip_w), int(y2 - y1)), enhanced, dots, centers_rel)

    def _read_digit_row(self, dial: np.ndarray) -> tuple[str | None, float, str]:
        gray = cv2.cvtColor(dial, cv2.COLOR_BGR2GRAY) if dial.ndim == 3 else dial
        h, w = gray.shape
        self._tracer.log(f"行定位开始：二值图 {w}x{h}")
        best: tuple[float, list, np.ndarray] | None = None  # (总分, cells, 二值图)

        # 多种阈值和去线尺度共同参与行评分。
        _, otsu = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        binaries = [
            ("INV", cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                          cv2.THRESH_BINARY_INV, 41, 12)),
            ("BIN", cv2.adaptiveThreshold(gray, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                          cv2.THRESH_BINARY, 41, 12)),
            ("OTSU_BIN", otsu),
            ("OTSU_INV", 255 - otsu),
        ]
        klens = [("S", max(45, int(0.08 * max(h, w)))),
                 ("L", max(60, int(0.35 * max(h, w))))]
        variants = [(f"{b}_{k}", bw0, kl) for (b, bw0) in binaries for (k, kl) in klens]
        for pol_name, bw0, klen in variants:
            bw = bw0.copy()
            self._tracer.save(f"binary_{pol_name}", bw)
            lines_h = cv2.morphologyEx(bw, cv2.MORPH_OPEN,
                                       cv2.getStructuringElement(cv2.MORPH_RECT, (klen, 1)))
            lines_v = cv2.morphologyEx(bw, cv2.MORPH_OPEN,
                                       cv2.getStructuringElement(cv2.MORPH_RECT, (1, klen)))
            bw = cv2.bitwise_and(bw, cv2.bitwise_not(cv2.bitwise_or(lines_h, lines_v)))
            # 闭运算连接断裂笔画，开运算去除噪点。
            bw = cv2.morphologyEx(bw, cv2.MORPH_CLOSE, np.ones((2, 2), np.uint8))
            bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
            self._tracer.save(f"lines_removed_{pol_name}", bw)

            rows = self._extract_rows(bw, h, w)
            self._tracer.log(f"[{pol_name}] 候选行数: {len(rows)}，"
                             f"各行列数: {[len(r['cells']) for r in rows]}")
            self._tracer.draw_boxes(f"rows_{pol_name}", bw,
                                    [b for r in rows for b in r["cells"]])

            for row in rows:
                cells = row["cells"]
                if not (3 <= len(cells) <= 10):
                    continue
                heights = [b[3] for b in cells]
                mean_h = float(np.mean(heights))
                if mean_h < 10:
                    continue
                uniformity = 1.0 - float(np.std(heights)) / mean_h
                if uniformity < 0.6:
                    continue
                chars, scores = self._recognize_row(bw, cells)
                self._tracer.log(f"[{pol_name}] 行 cy={row['cy']:.0f} n={len(cells)} "
                                 f"mh={mean_h:.0f} uni={uniformity:.2f} "
                                 f"chars={''.join(chars)}")
                # 行端部低分剔除（行首/尾的非数字粘连块，如“小数点+kWh”）
                while len(chars) > 3 and scores[0] < 0.55:
                    chars.pop(0); scores.pop(0); cells = cells[1:]
                while len(chars) > 3 and scores[-1] < 0.55:
                    chars.pop(); scores.pop(); cells = cells[:-1]
                if len(chars) < 3:
                    continue
                score = float(np.mean(scores))
                if score < 0.35:  # 相关性低 → 参数文字行/型号行等
                    continue
                total = (score * uniformity * min(1.0, len(cells) / 5.0)
                         * min(1.0, mean_h / (0.05 * h)))
                if best is None or total > best[0]:
                    best = (total, cells, bw)
                    self._tracer.log(f"[{pol_name}] 当前行入选 total={total:.3f} "
                                     f"reading={''.join(chars)}")

        if best is None:
            # 未找到字符行时改用整条数字带识别。
            self._fail_stage = "row_locate"
            self._tracer.log("逐字路径失败（无合格行）→ 回退整条数字带滑窗识别")
            reading, conf = self._fallback_holistic_anywhere(best and best[2], h, w)
            if reading:
                return reading, conf, "digital"
            return None, 0.0, "digital"

        cells, bw = best[1], best[2]
        chars, scores = self._recognize_row(bw, cells)
        self._tracer.save_chars("chars_cell_path", bw, cells, chars, scores)
        reading = self._join_with_decimal(self._dots_near(bw, cells), cells, chars)
        conf = max(0.0, min(1.0, float(np.mean(scores))))

        # 字间距一致性：主读数行数字等距，间距紊乱说明混入了杂质
        xs = [c[0] for c in cells]
        if len(xs) >= 3:
            gaps = [b - a for a, b in zip(xs, xs[1:])]
            spacing_cv = float(np.std(gaps)) / max(1.0, float(np.mean(gaps)))
            conf *= max(0.6, 1.0 - 0.5 * max(0.0, spacing_cv - 0.25))
            conf = round(conf, 4)
            self._tracer.log(f"字间距变异系数 {spacing_cv:.2f} → 置信度调整 {conf:.3f}")

        if not reading or not reading.replace(".", "").isdigit() or len(reading) < 3:
            self._fail_stage = "char_assemble"
            self._tracer.log(f"逐字读数非法（{reading!r}）→ 回退整条数字带滑窗识别")
            alt, alt_conf = self._fallback_holistic_anywhere(bw, h, w, prefer_cells=cells)
            if alt:
                return alt, alt_conf, "digital"
            return None, 0.0, "digital"
        if conf < 0.62:
            # 低置信度时比较整条数字带识别结果。
            alt, alt_conf = self._fallback_holistic_anywhere(bw, h, w, prefer_cells=cells)
            if alt and alt_conf > conf:
                self._tracer.log(f"滑窗整条识别更优: {alt}({alt_conf:.2f}) 替换 {reading}({conf:.2f})")
                return alt, alt_conf, "digital"
        self._tracer.log(f"逐字路径成功: {reading} conf={conf:.3f}")
        return reading, conf, "digital"

    def _fallback_holistic_anywhere(self, bw: np.ndarray | None, h: int, w: int,
                                    prefer_cells: list | None = None) -> tuple[str | None, float]:
        """使用滑窗模板识别整条数字带。"""
        if bw is None:
            return None, 0.0
        strip_box = None
        if prefer_cells:
            x1 = min(c[0] for c in prefer_cells) - 2
            y1 = min(c[1] for c in prefer_cells) - 2
            x2 = max(c[0] + c[2] for c in prefer_cells) + 2
            y2 = max(c[1] + c[3] for c in prefer_cells) + 2
            strip_box = (x1, y1, x2 - x1, y2 - y1)
        else:
            best_w = 0
            for (x, y, cw, ch) in self._small_blobs(bw):
                if 20 <= ch <= 0.2 * h and cw > best_w and cw > 2.0 * ch:
                    best_w, strip_box = cw, (x, y, cw, ch)
        if strip_box is None:
            self._tracer.log("滑窗回退：未找到数字带区域")
            return None, 0.0

        x, y, cw, ch = strip_box
        strip = bw[y:y + ch, x:x + cw]
        if strip.size == 0 or strip.shape[0] < 8:
            return None, 0.0
        scale = 28.0 / strip.shape[0]
        strip28 = cv2.resize(strip, (max(1, int(strip.shape[1] * scale)), 28),
                             interpolation=cv2.INTER_AREA)
        _, strip28 = cv2.threshold(strip28, 100, 255, cv2.THRESH_BINARY)
        self._tracer.save("holistic_strip", strip28)
        strip28 = strip28.astype(np.float32) / 255.0  # 与模板同类型（float 0-1）

        # 每个数字的得分图 = 该数字所有模板变体的逐元素最大值
        score_by_digit: dict[str, np.ndarray] = {}
        tmpl_w = 16
        for digit, t in self._seg_templates:
            res = cv2.matchTemplate(strip28, t, cv2.TM_CCOEFF_NORMED)
            if res.shape[1] == 0:
                continue
            tmpl_w = t.shape[1]
            prev = score_by_digit.get(digit)
            score_by_digit[digit] = res if prev is None else np.maximum(prev, res)

        if not score_by_digit:
            return None, 0.0
        # 合成总得分图与最优数字图
        stacked = np.stack(list(score_by_digit.values()))
        digits = list(score_by_digit.keys())
        best_map = stacked.max(axis=0)
        best_dig = stacked.argmax(axis=0)

        # 峰值提取 + 非极大值抑制
        peaks: list[tuple[int, float, str]] = []  # (x, score, digit)
        order = np.argsort(best_map.ravel())[::-1]
        min_dist = max(8, int(0.55 * tmpl_w))
        for flat in order[:60]:
            py, px = divmod(int(flat), best_map.shape[1])
            score = float(best_map[py, px])
            if score < 0.60:
                break
            if all(abs(px - pxi) >= min_dist for pxi, _, _ in peaks):
                peaks.append((px, score, digits[best_dig[py, px]]))
        peaks.sort(key=lambda p: p[0])
        self._tracer.log(f"滑窗识别峰位: {[(p[0], p[2], round(p[1], 2)) for p in peaks]}")
        if len(peaks) < 3:
            self._fail_stage = "holistic_fallback"
            return None, 0.0
        reading = "".join(p[2] for p in peaks)
        conf = round(float(np.mean([p[1] for p in peaks])) * 0.95, 4)  # 滑窗路径轻微折减
        self._tracer.log(f"滑窗整条识别: {reading} conf={conf}")
        return reading, conf

    def _dots_near(self, bw: np.ndarray, cells) -> list:
        """行基线附近的小连通域（小数点候选）。"""
        x1 = min(c[0] for c in cells) - 20
        x2 = max(c[0] + c[2] for c in cells) + 20
        y1 = min(c[1] for c in cells) - 10
        y2 = max(c[1] + c[3] for c in cells) + 10
        out = []
        for (x, y, cw, ch) in self._small_blobs(bw):
            if x1 <= x and x + cw <= x2 and y1 <= y and y + ch <= y2:
                out.append((x, y, cw, ch))
        return out

    def _extract_rows(self, bw: np.ndarray, h: int, w: int) -> list[dict]:
        """从二值图中提取数字行。"""
        contours, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        boxes = [cv2.boundingRect(c) for c in contours]
        # 预过滤：噪声不参与；过高的结构（边框残段/机身）丢弃
        boxes = [b for b in boxes if b[3] >= 4 and b[2] >= 2 and b[3] <= 0.2 * h]
        boxes.sort(key=lambda b: b[0])

        # 粘连数字带处理：残影使相邻 LCD 数字连成一个宽长条（w > 0.15*图宽）。
        # 对其做垂直投影切分成单字；同时剔除落在带内的重复单字框。
        normal: list[tuple[int, int, int, int]] = []
        band_cells: list[tuple[int, int, int, int]] = []
        band_boxes: list[tuple[int, int, int, int]] = []
        for b in boxes:
            x, y, cw, ch = b
            if cw > 0.15 * w and ch >= 10 and cw > 1.6 * ch:
                band_boxes.append(b)
            else:
                normal.append(b)
        for (x, y, cw, ch) in band_boxes:
            sub = bw[y:y + ch, x:x + cw]
            col = (sub > 0).sum(axis=0)
            segs, in_seg = [], False
            for i, v in enumerate(col):
                if v > 0 and not in_seg:
                    start, in_seg = i, True
                elif v == 0 and in_seg:
                    segs.append((start, i))
                    in_seg = False
            if in_seg:
                segs.append((start, len(col)))
            pieces: list[list[int]] = []
            for s, e in segs:
                if pieces and (e - s < 8 or s - pieces[-1][1] < 4):
                    pieces[-1][1] = e  # 窄间隙并入前段（同一字符的断裂）
                else:
                    pieces.append([s, e])
            for s, e in pieces:
                pw = e - s
                if pw >= 6 and 0.2 <= pw / max(ch, 1) <= 1.6:
                    band_cells.append((x + s, y, pw, ch))
        if band_boxes:
            def inside_band(b: tuple[int, int, int, int]) -> bool:
                x, y, cw, ch = b
                for (bx, by, bw_, bh) in band_boxes:
                    if (x >= bx - 2 and y >= by - 2
                            and x + cw <= bx + bw_ + 2 and y + ch <= by + bh + 2):
                        return True
                return False
            normal = [b for b in normal if not inside_band(b)]
        boxes = normal + band_cells
        boxes.sort(key=lambda b: b[0])

        # ① 全局配对预合并（不复用已合并结果，杜绝连锁）
        #    需同时满足：x 重叠 + 高度相近 + 垂直方向贴近（同一行文字）
        used = [False] * len(boxes)
        merged0: list[list[int]] = []
        for i, (x, y, cw, ch) in enumerate(boxes):
            if used[i]:
                continue
            for j in range(i + 1, len(boxes)):
                if used[j]:
                    continue
                x2, y2, cw2, ch2 = boxes[j]
                ox = min(x + cw, x2 + cw2) - max(x, x2)
                vy_gap = max(y, y2) - min(y + ch, y2 + ch2)  # 垂直间隙（重叠时为负）
                # 双窄条拼合：数字 1 由两条窄竖线构成（各自长宽比过小，
                # 会被格筛选丢弃），水平间隙小且高度相近时合并复原
                both_narrow = max(cw, cw2) <= 0.4 * max(ch, ch2)
                gap_x = max(x, x2) - min(x + cw, x2 + cw2)
                paired = (vy_gap <= 0.3 * max(ch, ch2) and both_narrow
                          and 0 <= gap_x <= 0.3 * max(ch, ch2))
                if ((ox > 0.35 * min(cw, cw2)
                        and max(ch, ch2) <= 2.5 * min(ch, ch2)
                        and vy_gap <= 0.3 * max(ch, ch2)) or paired):
                    nx, ny = min(x, x2), min(y, y2)
                    merged0.append([nx, ny, max(x + cw, x2 + cw2) - nx,
                                    max(y + ch, y2 + ch2) - ny])
                    used[i] = used[j] = True
                    break
            if not used[i]:
                merged0.append([x, y, cw, ch])
                used[i] = True

        # ② 行聚类（容差 ≈ 1.1×字高：让数字上/下半块与侧笔画聚进同一行，
        #    由行内合并与识别打分兜底剔除混入的杂片）
        rows: list[dict] = []
        for b in sorted(merged0, key=lambda b: b[1] + b[3] / 2):
            cy = b[1] + b[3] / 2
            for row in rows:
                if abs(cy - row["cy"]) <= max(14.0, 1.1 * max(row["maxh"], b[3])):
                    row["frags"].append(b)
                    row["maxh"] = max(row["maxh"], b[3])
                    row["cy"] = float(np.mean([f[1] + f[3] / 2 for f in row["frags"]]))
                    break
            else:
                rows.append({"cy": cy, "maxh": b[3], "frags": [b]})

        # ③ 行内合并 + 分类（同行的断裂碎片相连：x 重叠，或垂直重叠且水平
        #    间隙很小——仅限明显小于行高的“碎片”，避免相邻整字被粘成一长条）
        out = []
        for row in rows:
            merged: list[list[int]] = []
            for x, y, cw, ch in sorted(row["frags"], key=lambda b: b[0]):
                put = False
                for m in merged:
                    ox = min(m[0] + m[2], x + cw) - max(m[0], x)
                    vy = min(m[1] + m[3], y + ch) - max(m[1], y)
                    gap = max(m[0], x) - min(m[0] + m[2], x + cw)
                    small_frag = min(m[3], ch) < 0.6 * row["maxh"]
                    both_narrow = max(m[2], cw) <= 0.4 * max(m[3], ch)
                    near = ((ox > 0.3 * min(m[2], cw))
                            or (vy > 0 and small_frag and gap <= 0.3 * max(m[3], ch))
                            or (vy > 0 and both_narrow and 0 <= gap <= 0.3 * max(m[3], ch)))
                    if near and max(m[3], ch) <= 4.0 * min(m[3], ch):
                        nx, ny = min(m[0], x), min(m[1], y)
                        m[2] = max(m[0] + m[2], x + cw) - nx
                        m[3] = max(m[1] + m[3], y + ch) - ny
                        m[0], m[1] = nx, ny
                        put = True
                        break
                if not put:
                    merged.append([x, y, cw, ch])

            cells, dots = [], []
            for x, y, cw, ch in merged:
                ratio = cw / max(ch, 1)
                # 长宽比下限 0.12：容纳数字 1（单条竖线，w/h ≈ 0.125）
                if 10 <= ch <= 0.2 * h and 0.12 <= ratio <= 1.5:
                    cells.append((x, y, cw, ch))
                elif ch >= 3 and ratio < 0.12 and cw <= 0.03 * w:
                    dots.append((x, y, cw, ch))
            if cells:
                # 二次分离：行内明显小于中位尺寸的“格”其实是小数点/标点
                med_w = float(np.median([c[2] for c in cells]))
                med_h = float(np.median([c[3] for c in cells]))
                real_cells, tiny = [], []
                for c in cells:
                    if c[2] < 0.55 * med_w and c[3] < 0.55 * med_h:
                        tiny.append(c)
                    else:
                        real_cells.append(c)
                if len(real_cells) >= 3:
                    real_cells.sort(key=lambda b: b[0])
                    out.append({"cy": row["cy"], "cells": real_cells, "dots": dots + tiny})
        return out

    @staticmethod
    def _small_blobs(bw: np.ndarray) -> list[tuple[int, int, int, int]]:
        contours, _ = cv2.findContours(bw, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        return [cv2.boundingRect(c) for c in contours]

    def _recognize_row(self, bw: np.ndarray, cells) -> tuple[list[str], list[float]]:
        chars, scores = [], []
        for (x, y, cw, ch) in cells:
            pad = 2
            patch = bw[max(0, y - pad):y + ch + pad, max(0, x - pad):x + cw + pad]
            ch_, sc = self._recognize_digit(patch)
            chars.append(ch_)
            scores.append(sc)
        return chars, scores

    def _join_with_decimal(self, dots, cells, chars) -> str:
        """在相邻数字之间插入小数点：小圆点、贴近基线、位于两格之间。"""
        if len(cells) < 2 or not chars:
            return "".join(chars)
        med_w = float(np.median([c[2] for c in cells]))
        med_h = float(np.median([c[3] for c in cells]))
        row_bottom = float(np.median([c[1] + c[3] for c in cells]))
        out = list(chars)
        used: set[int] = set()
        for (x, y, cw, ch) in dots:
            if cw > 0.5 * med_w or ch > 0.5 * med_h:
                continue
            if y + ch / 2 < row_bottom - 0.6 * med_h:
                continue  # 小数点应贴近基线
            for i in range(len(cells) - 1):
                if i in used:
                    continue
                gx1, gx2 = cells[i][0] + cells[i][2], cells[i + 1][0]
                g = gx2 - gx1
                if g > 0 and gx1 - 0.3 * g <= x and x + cw <= gx2 + 0.3 * g:
                    out[i] = chars[i] + "."
                    used.add(i)
                    break
        return "".join(out)

    def _recognize_digit(self, patch: np.ndarray) -> tuple[str, float]:
        char = self._normalize_char(patch, 28)
        if self.backend == "onnx":
            logits = self._session.run(None, {self._session.get_inputs()[0].name: char[None]})[0][0]
            probs = np.exp(logits - logits.max())
            probs /= probs.sum()
            idx = int(np.argmax(probs))
            return DIGITS[idx], float(probs[idx])

        def best(templates):
            bd, bs = "0", -1.0
            for digit, t in templates:
                s = float(cv2.matchTemplate(char, t, cv2.TM_CCOEFF_NORMED).max())
                if s > bs:
                    bd, bs = digit, s
            return bd, bs

        seg_d, seg_s = best(self._seg_templates)
        if seg_s >= 0.62:  # 七段码族高置信：真实 LCD 主场景
            digit, score = seg_d, seg_s
        else:
            digit, score = best(self._templates)
        conf = max(0.0, min(1.0, (score - 0.2) / 0.75))
        return digit, conf

    @staticmethod
    def _normalize_char(patch: np.ndarray, size: int) -> np.ndarray:
        """裁剪字符有效区域并等比缩放居中到 size×size（前景统一为白色）。

        用边框颜色判断背景极性（比按白色占比判断更稳：笔画粗的字模
        自身白色占比就可能过半，会被误反相）。
        """
        _, binimg = cv2.threshold(patch, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
        border = np.concatenate([binimg[0, :], binimg[-1, :], binimg[:, 0], binimg[:, -1]])
        if np.mean(border > 0) > 0.5:  # 背景为白（白底黑字）→ 反相
            binimg = 255 - binimg
        ys, xs = np.where(binimg > 0)
        if len(xs) == 0:
            return np.zeros((size, size), np.float32)
        x1, x2, y1, y2 = xs.min(), xs.max(), ys.min(), ys.max()
        roi = binimg[y1:y2 + 1, x1:x2 + 1]
        s = size - 4
        scale = s / max(roi.shape)
        roi = cv2.resize(roi, (max(1, int(roi.shape[1] * scale)), max(1, int(roi.shape[0] * scale))),
                         interpolation=cv2.INTER_AREA)
        canvas = np.zeros((size, size), np.uint8)
        oy, ox = (size - roi.shape[0]) // 2, (size - roi.shape[1]) // 2
        canvas[oy:oy + roi.shape[0], ox:ox + roi.shape[1]] = roi
        return canvas.astype(np.float32) / 255.0

    @classmethod
    def _build_seven_seg_templates(cls) -> list[tuple[str, np.ndarray]]:
        """生成不同粗细和倾角的七段码模板。"""
        templates = []
        for thickness in (5, 7, 10):
            for shear in (0.0, 0.10, -0.10):
                for d, segs in SEG_MAP.items():
                    canvas = np.zeros((68, 40), np.uint8)
                    cls._draw_segments(canvas, segs, thickness, shear)
                    templates.append((d, cls._normalize_char(canvas, 28)))
        return templates

    @staticmethod
    def _build_font_templates() -> list[tuple[str, np.ndarray]]:
        """生成字轮数字使用的字体模板。"""
        out = []
        for d in DIGITS:
            canvas = np.zeros((64, 64), np.uint8)
            cv2.putText(canvas, d, (12, 52), cv2.FONT_HERSHEY_SIMPLEX, 2.0, 255, thickness=6)
            out.append((d, DialReader._normalize_char(canvas, 28)))
        return out

    @staticmethod
    def _draw_segments(canvas: np.ndarray, segs, t: int, shear: float) -> None:
        h, w = canvas.shape
        m = 6  # 边距
        top, mid, bot = m, h // 2, h - m
        xl, xr = m, w - m
        k = t // 2

        def shear_x(y: float, x: float) -> int:
            return int(x + shear * (h - y))

        def seg(p1, p2):
            cv2.line(canvas, (shear_x(p1[1], p1[0]), p1[1]), (shear_x(p2[1], p2[0]), p2[1]),
                     255, t)

        # 水平段（上/中/下）
        if 0 in segs: seg((xl + k, top), (xr - k, top))
        if 6 in segs: seg((xl + k, mid), (xr - k, mid))
        if 3 in segs: seg((xl + k, bot), (xr - k, bot))
        # 垂直段（左上/右上/左下/右下）
        if 5 in segs: seg((xl, top + k), (xl, mid - k))
        if 1 in segs: seg((xr, top + k), (xr, mid - k))
        if 4 in segs: seg((xl, mid + k), (xl, bot - k))
        if 2 in segs: seg((xr, mid + k), (xr, bot - k))

    def _read_pointer_dials(self, dial: np.ndarray) -> tuple[str | None, float, str]:
        gray = cv2.cvtColor(dial, cv2.COLOR_BGR2GRAY) if dial.ndim == 3 else dial
        h, w = gray.shape
        gray = cv2.GaussianBlur(gray, (5, 5), 1)
        circles = cv2.HoughCircles(gray, cv2.HOUGH_GRADIENT, dp=1.2,
                                   minDist=min(h, w) // 6,
                                   param1=110, param2=30,
                                   minRadius=max(8, min(h, w) // 22),
                                   maxRadius=min(h, w) // 5)
        if circles is None or len(circles[0]) < 3:
            return None, 0.0, "pointer"
        subs = sorted(circles[0][:6], key=lambda c: c[0])  # 从左到右
        r_med = float(np.median([c[2] for c in subs]))
        subs = [c for c in subs if 0.6 * r_med <= c[2] <= 1.6 * r_med][:5]
        # 门控：子表盘中心需大致水平排列，否则视为误检
        if len(subs) < 3:
            return None, 0.0, "pointer"
        ys = [c[1] for c in subs]
        if float(np.std(ys)) > 0.5 * r_med:
            return None, 0.0, "pointer"
        digits, confs = [], []
        for cx, cy, r in subs:
            d, cf = self._pointer_value(gray, cx, cy, r)
            digits.append(d)
            confs.append(cf)
        value = "".join(digits)
        if not value.isdigit():
            return None, 0.0, "pointer"
        return value, float(np.mean(confs)), "pointer"

    def _pointer_value(self, gray: np.ndarray, cx: float, cy: float, r: float) -> tuple[str, float]:
        """单个子表盘：中心到指针尖的直线角度 → 0-9。"""
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
            if cw > 0.9 * w or ch > 0.9 * h:  # 跳过外框
                continue
            M = cv2.moments(c)
            if M["m00"] == 0:
                continue
            # 只保留穿过中心附近的细长轮廓（指针）
            ccx, ccy = M["m10"] / M["m00"], M["m01"] / M["m00"]
            dist = np.hypot(ccx - w / 2, ccy - h / 2)
            if dist > 0.35 * r:
                continue
            rect = cv2.minAreaRect(c)
            (rcx, rcy), (rw, rh), ang = rect
            length = max(rw, rh)
            if length < 0.5 * r:
                continue
            # 指针角度：长边方向
            theta = np.deg2rad(ang if rw >= rh else ang + 90)
            dx, dy = np.cos(theta), np.sin(theta)
            # 方向修正：指针应指向偏离中心的一侧
            vx, vy = rcx - w / 2, rcy - h / 2
            if vx * dx + vy * dy < 0:
                theta += np.pi
            best_angle, best_len = theta, length
        if best_angle is None:
            return "0", 0.1
        # 表盘 0 在正上方，顺时针每 36° 一格
        deg = (np.rad2deg(best_angle) + 90) % 360
        value = int((deg + 18) // 36) % 10
        conf = min(0.9, 0.4 + best_len / (2 * r))
        return str(value), conf
