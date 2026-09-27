FROM python:3.12-slim

WORKDIR /srv

RUN apt-get update \
    && apt-get install -y --no-install-recommends libglib2.0-0 libgl1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# PaddlePaddle 的 Linux 动态库依赖 OpenMP 运行时。
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY . .
RUN test -s models/display_detector.onnx \
    && test -s models/meter_rec_yolo_v1/inference.json \
    && test -s models/meter_rec_yolo_v1/inference.pdiparams \
    && test -s models/meter_rec_yolo_v1/inference.yml

ENV AI_RECOGNIZER=paddleocr \
    AI_PADDLE_MODEL_PATH=models/meter_rec_yolo_v1 \
    AI_PADDLE_DEVICE=cpu \
    AI_PADDLE_MODEL_SOURCE=BOS \
    AI_PORT=8001

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
