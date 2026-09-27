# 模型文件说明

Git 不保存模型权重和测试图片，只保存这份放置说明。运行当前识别链路前，
需要在本机准备以下文件：

- `display_detector.onnx`：定位图片中的数字显示区域。
- `meter_rec_yolo_v1/`：PaddleOCR 读数识别模型。

其中 `meter_rec_yolo_v1/` 至少包含 `inference.json`、`inference.pdiparams`
和 `inference.yml`。这些文件目前仍在原开发机本地；新环境克隆仓库后，
需从独立的模型存储复制到相同路径，再启动 Python 服务或构建 Docker 镜像。
训练过程中的其他模型也留在本机，不进入 Git。
