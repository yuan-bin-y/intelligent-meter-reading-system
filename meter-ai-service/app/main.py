"""视觉识别服务入口。"""

from __future__ import annotations

import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from .api.recognize import router as recognize_router
from .core.config import settings
from .registry import get_recognizer, list_models, recognizer_kind

logging.basicConfig(level=settings.log_level,
                    format="%(asctime)s %(levelname)s [%(name)s] %(message)s")
log = logging.getLogger("ai-service")


@asynccontextmanager
async def lifespan(_: FastAPI):
    """启动时加载模型，加载失败则终止启动。"""
    recognizer = get_recognizer()
    log.info("AI 服务启动 mode=%s recognizer=%s", settings.mode,
             recognizer.name)
    yield


app = FastAPI(
    title=settings.app_name,
    version=settings.version,
    description="智能抄表算法服务：图片质量检测 / 表具分类 / 表盘检测 / 读数识别。"
                "仅由 Java 业务后端调用，不直接访问业务数据库。",
    lifespan=lifespan,
)

app.include_router(recognize_router)


@app.get("/health")
def health() -> dict:
    init_error = None
    try:
        recognizer = get_recognizer()
        recognizer_desc = f"{recognizer.name}@{recognizer.version}"
    except Exception as e:  # 健康检查需返回模型初始化错误
        recognizer_desc = f"INIT_FAILED: {e}"
        init_error = str(e)
    return {
        "status": "degraded" if init_error else "up",
        "mode": recognizer_kind(),
        "recognizerKind": recognizer_kind(),
        "version": settings.version,
        "recognizer": recognizer_desc,
        "initError": init_error,
        "models": len(list_models()),
    }
