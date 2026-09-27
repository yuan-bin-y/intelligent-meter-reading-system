"""识别接口测试。"""

from __future__ import annotations

from fastapi.testclient import TestClient


def test_health(client: TestClient):
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "up"
    assert data["recognizerKind"] in ("mock", "opencv", "paddleocr")


def test_models_endpoint(client: TestClient):
    res = client.get("/api/v1/models")
    assert res.status_code == 200
    models = res.json()
    assert isinstance(models, list) and models
    for m in models:
        assert {"name", "version", "meterTypes", "format", "status", "accuracyNote"} <= set(m)
    # 未评估模型不返回准确率。
    assert any("待" in m["accuracyNote"] for m in models)


def test_recognize_contract(client: TestClient, meter_png: bytes):
    res = client.post("/api/v1/recognize",
                      files={"image": ("meter.png", meter_png, "image/png")})
    assert res.status_code == 200
    data = res.json()
    for key in ("success", "meterType", "dialType", "reading", "unit", "confidence",
                "qualityScore", "qualityIssues", "needsReview", "modelName",
                "modelVersion", "inferenceTimeMs", "message"):
        assert key in data, f"缺少契约字段 {key}"
    assert data["success"] is True
    assert data["meterType"] in ("ELECTRIC", "WATER", "GAS")
    assert data["dialType"] in ("DIGITAL", "MECHANICAL", "POINTER")
    assert data["unit"] == "kWh"
    assert data["reading"] and data["reading"].replace(".", "").isdigit()


def test_recognize_invalid_image(client: TestClient):
    res = client.post("/api/v1/recognize",
                      files={"image": ("x.png", b"junk", "image/png")})
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is False
    assert data["errorCode"] == "E_IMAGE_DECODE"


def test_recognize_empty_upload(client: TestClient):
    res = client.post("/api/v1/recognize",
                      files={"image": ("x.png", b"", "image/png")})
    assert res.status_code == 400


def test_quality_check_endpoint(client: TestClient, blurry_jpg: bytes):
    res = client.post("/api/v1/quality-check",
                      files={"image": ("b.jpg", blurry_jpg, "image/jpeg")})
    assert res.status_code == 200
    data = res.json()
    assert data["passed"] is False
    assert "E_BLURRY" in data["qualityIssues"]


def test_quality_check_valid_image(client: TestClient, meter_png: bytes):
    res = client.post("/api/v1/quality-check",
                      files={"image": ("m.png", meter_png, "image/png")})
    assert res.status_code == 200
    assert res.json()["passed"] is True
