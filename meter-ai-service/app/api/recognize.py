"""识别和图片质量接口。"""

from __future__ import annotations

import uuid

from fastapi import APIRouter, File, Form, HTTPException, UploadFile
from fastapi.responses import JSONResponse

from ..core.config import settings
from ..core.debug import Tracer
from ..quality.checker import check_quality, decode_image
from ..registry import RecognizerInitError, get_recognizer, list_models, recognizer_kind
from ..schemas.recognition import ErrorResponse, QualityReport, RecognitionResult

router = APIRouter(prefix="/api/v1", tags=["recognition"])


@router.post("/recognize", response_model=RecognitionResult,
             responses={400: {"model": ErrorResponse}, 503: {"model": ErrorResponse}})
async def recognize(
    image: UploadFile = File(..., alias="image"),
    meter_type: str | None = Form(None, alias="meter_type"),
    dial_type: str | None = Form(None, alias="dial_type"),
    previous_reading: str | None = Form(None, alias="previous_reading"),
) -> RecognitionResult:
    """识别表盘图片。"""
    data = await image.read()
    if not data:
        raise HTTPException(400, "上传内容为空")
    if len(data) > 15 * 1024 * 1024:
        raise HTTPException(400, "图片超过 15MB 限制")

    try:
        img = decode_image(data)
    except ValueError as e:
        err = ErrorResponse(error_code="E_IMAGE_DECODE", message=str(e))
        return JSONResponse(status_code=200, content=err.model_dump(by_alias=True))

    hint = meter_type.upper() if meter_type else None
    dial_hint = dial_type.upper() if dial_type else None
    try:
        return get_recognizer().recognize(img, hint, dial_hint, previous_reading)
    except RecognizerInitError as e:
        # 初始化失败属于服务不可用。
        err = ErrorResponse(error_code="E_RECOGNIZER_INIT", message=str(e))
        return JSONResponse(status_code=503, content=err.model_dump(by_alias=True))
    except ValueError as e:
        # 图片无法识别属于业务失败。
        err = ErrorResponse(error_code=str(e), message="识别失败，请重拍或人工录入")
        return JSONResponse(status_code=200, content=err.model_dump(by_alias=True))
    except Exception as e:  # noqa: BLE001
        raise HTTPException(500, f"算法服务内部错误: {e}") from e


@router.post("/quality-check", response_model=QualityReport)
async def quality_check(image: UploadFile = File(..., alias="image")) -> QualityReport:
    """检查图片质量。"""
    data = await image.read()
    if not data:
        raise HTTPException(400, "上传内容为空")
    try:
        img = decode_image(data)
    except ValueError as e:
        report = QualityReport(passed=False, quality_score=0.0)
        report.quality_issues = ["E_IMAGE_DECODE"]
        report.suggestions = [str(e)]
        return report
    return check_quality(img)


@router.get("/models")
async def models() -> list[dict]:
    """返回当前模型信息。"""
    return list_models()


def new_task_id() -> str:
    """生成任务标识。"""
    return uuid.uuid4().hex


__all__ = ["router", "settings"]
