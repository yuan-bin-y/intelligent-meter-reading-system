"""隔离环境的消息链路容量测试，不上传图片或加载真实模型。"""

import argparse
import base64
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
import math
import secrets
import uuid
import time
import urllib.error
import urllib.request

import pymysql
from pymysql.constants import CLIENT


API = "http://127.0.0.1:18070"
BROKER = "http://127.0.0.1:15681"


def sql(statement):
    connection = pymysql.connect(host="127.0.0.1", port=13306, user="root", password="test-only-root",
                                 database="meter_hardening", charset="utf8mb4", autocommit=True,
                                 client_flag=CLIENT.MULTI_STATEMENTS, connect_timeout=3, read_timeout=10)
    try:
        with connection.cursor() as cursor:
            cursor.execute(statement)
            result = list(cursor.fetchall())
            while cursor.nextset():
                result.extend(cursor.fetchall())
            return result
    finally:
        connection.close()


def request(path, data=None, token=None, base=API, basic=False):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if basic:
        headers["Authorization"] = "Basic " + base64.b64encode(b"test:test-only-password").decode()
    body = None if data is None else json.dumps(data).encode()
    if body is not None:
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(base + path, body, headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        raw = response.read().decode()
        return json.loads(raw) if not path.endswith("prometheus") else raw


def percentile(values, fraction):
    return round(sorted(values)[max(0, math.ceil(len(values) * fraction) - 1)], 2) if values else None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tasks", type=int, default=60, choices=range(1, 1001))
    parser.add_argument("--concurrency", type=int, default=8, choices=range(1, 65))
    parser.add_argument("--worker-concurrency", type=int, default=1)
    args = parser.parse_args()
    seed = int(time.time() * 1000)
    username = "load_" + str(seed)
    password = secrets.token_urlsafe(24)
    request("/api/v1/auth/register", {"username": username, "password": password, "displayName": "Load test"})
    user_id = int(sql(f"SELECT id FROM sys_user WHERE username='{username}';")[0][0])
    sql(f"INSERT INTO sys_user_role(user_id,role_id,created_at) SELECT {user_id},id,NOW() FROM sys_role WHERE role_code='ADMIN';")
    login = request("/api/v1/auth/login", {"username": username, "password": password})["data"]
    token = login["accessToken"]
    # 覆盖真正的HTTP设备写事务、详情缓存与幂等轮换，不把密钥写入报告。
    device = request("/api/v1/admin/devices", {"deviceNo": "FLOW-" + str(seed),
                     "deviceName": "Rotation smoke", "deviceType": "CAMERA"}, token)["data"]
    device_path = "/api/v1/admin/devices/" + str(device["deviceId"])
    before = request(device_path, token=token)["data"]
    rotation_request = {"version": before["version"], "requestId": str(uuid.uuid4())}
    rotation = request(device_path + "/secret/reset", rotation_request, token)["data"]
    replay = request(device_path + "/secret/reset", rotation_request, token)["data"]
    after = request(device_path, token=token)["data"]
    assert rotation["deviceSecret"] and not rotation["replayed"]
    assert replay["replayed"] and replay.get("deviceSecret") is None
    assert after["version"] == before["version"] + 1
    assert after["credentialVersion"] == before["credentialVersion"] + 1
    sql(f"""
        INSERT INTO meter(id,meter_no,meter_name,meter_type,display_type,unit,integer_digits,decimal_digits,created_by,updated_by)
        VALUES({seed},'LOAD-{seed}','Load meter','WATER','LCD','m3',6,3,{user_id},{user_id});
        INSERT INTO device(id,device_no,device_name,device_type,status,secret_hash,credential_version,secret_rotated_at,created_by,updated_by)
        VALUES({seed},'LOAD-{seed}','Load device','CAMERA',1,REPEAT('a',64),1,NOW(),{user_id},{user_id});
    """)
    statements = []
    for offset in range(args.tasks):
        resource_id = seed + offset
        statements.append(f"""
            INSERT INTO meter_reading_task(id,task_no,meter_id,executor_type,device_id,task_status,scheduled_at,started_at,created_by,updated_by)
            VALUES({resource_id},'LOAD-{resource_id}',{seed},'DEVICE',{seed},'PROCESSING',NOW(),NOW(),{user_id},{user_id});
            INSERT INTO meter_image(id,task_id,uploader_type,uploader_id,upload_request_id,image_type,original_name,bucket_name,object_key,content_type,file_size,sha256)
            VALUES({resource_id},{resource_id},'DEVICE',{seed},'load-{resource_id}','ORIGINAL','fixture.jpg','test-fixture','load/{resource_id}.jpg','image/jpeg',1,REPEAT('b',64));
        """)
    sql("\n".join(statements))
    samples = []
    failures = []
    starts = {}
    completed = {}
    http_latencies = []
    start = time.monotonic()

    def submit(image_id):
        began = time.monotonic()
        try:
            response = request(f"/api/v1/admin/meter-images/{image_id}/recognition-tasks", {}, token)
            return int(response["data"]["recognitionTaskId"]), began, (time.monotonic() - began) * 1000, None
        except (urllib.error.HTTPError, urllib.error.URLError, KeyError) as error:
            return None, began, (time.monotonic() - began) * 1000, str(error)

    with ThreadPoolExecutor(max_workers=args.concurrency) as executor:
        futures = [executor.submit(submit, seed + offset) for offset in range(args.tasks)]
        for future in as_completed(futures):
            task_id, began, duration, error = future.result()
            http_latencies.append(duration)
            if error:
                failures.append(error)
            else:
                starts[task_id] = began
    submitted_at = time.monotonic()
    states = {}
    while time.monotonic() - start < 180:
        rows = sql(f"SELECT id,status FROM ai_recognition_task WHERE reading_task_id BETWEEN {seed} AND {seed + args.tasks - 1};")
        states = {int(task_id): state for task_id, state in rows}
        now = time.monotonic()
        for task_id, state in states.items():
            if state in {"SUCCEEDED", "FAILED", "CANCELLED"}:
                completed.setdefault(task_id, now)
        queue = request("/api/queues/%2f/meter.recognition.v2.queue", base=BROKER, basic=True)
        metrics = request("/actuator/prometheus")
        active = [float(line.rsplit(" ", 1)[1]) for line in metrics.splitlines()
                  if line.startswith("hikaricp_connections_active{")]
        samples.append({"elapsedSeconds": round(now - start, 3), "ready": queue.get("messages_ready", 0),
                        "unacked": queue.get("messages_unacknowledged", 0), "dbActive": sum(active)})
        if len(completed) == len(starts):
            break
        time.sleep(0.3)
    elapsed = time.monotonic() - start
    result_count = int(sql(f"SELECT COUNT(*) FROM meter_reading_result WHERE task_id BETWEEN {seed} AND {seed + args.tasks - 1};")[0][0])
    pending_review = int(sql(f"SELECT COUNT(*) FROM meter_reading_task WHERE id BETWEEN {seed} AND {seed + args.tasks - 1} AND task_status='PENDING_REVIEW';")[0][0])
    latencies = [(completed[task_id] - began) * 1000 for task_id, began in starts.items() if task_id in completed]
    success = sum(state == "SUCCEEDED" for state in states.values())
    report = {
        "scenario": "HTTP create -> MySQL Outbox -> RabbitMQ quorum -> real Java Worker -> HMAC HTTP callback -> MySQL result",
        "recognizer": "SUCCESS test engine with 50ms controlled delay; excludes OSS and real model inference",
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "tasks": args.tasks,
        "httpConcurrency": args.concurrency, "workerConcurrency": args.worker_concurrency, "prefetch": 1,
        "deviceRotationAndCacheSmokePassed": True,
        "succeeded": success, "failedHttp": len(failures), "readingResultRows": result_count,
        "pendingReviewTasks": pending_review, "elapsedSeconds": round(elapsed, 3),
        "throughputTasksPerSecond": round(success / elapsed, 3),
        "httpSubmitSeconds": round(submitted_at - start, 3), "httpP95Ms": percentile(http_latencies, 0.95),
        "completionP50Ms": percentile(latencies, 0.5), "completionP95Ms": percentile(latencies, 0.95),
        "observedPeakReady": max((sample["ready"] for sample in samples), default=0),
        "observedPeakDbActive": max((sample["dbActive"] for sample in samples), default=0),
        "samplingNote": "completion includes polling delay; queue metrics may be stale; local smoke load, not production QPS",
        "passed": success == args.tasks and result_count == args.tasks and pending_review == args.tasks and not failures,
        "failures": failures[:10], "samples": samples,
    }
    # JSON输出到stdout，由调用方决定保存位置，便于CI重定向与归档。
    print(json.dumps(report, ensure_ascii=False))
    if not report["passed"]:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
