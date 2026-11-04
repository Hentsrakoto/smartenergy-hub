"""Essai pédagogique borné : ingestion séquentielle/concurrente et lectures de 2 volumes.

Conserve deux capteurs uniques. Les temps de lecture incluent HTTP, Cassandra et JSON.
Les valeurs sont des observations locales ; aucune garantie de performance n'en découle.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta, timezone
import json
import math
import os
from pathlib import Path
import platform
import statistics
import time
from urllib.error import HTTPError
from urllib.request import Request, urlopen
import uuid


def request(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    with urlopen(Request(url, data=data, headers={"Content-Type": "application/json"}), timeout=20) as response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None


def distribution(seconds):
    ordered = sorted(value * 1000 for value in seconds)
    return {"samples": len(ordered), "mean_ms": round(statistics.mean(ordered), 3),
            "p50_ms": round(ordered[math.ceil(len(ordered) * .5) - 1], 3),
            "p95_ms": round(ordered[math.ceil(len(ordered) * .95) - 1], 3),
            "max_ms": round(max(ordered), 3)}


def snapshot(management):
    values = {}
    for metric, query in (("smartenergy.storage.persistence", "?tag=outcome:success"),
                          ("smartenergy.storage.persistence.errors", ""),
                          ("smartenergy.redis.pending", "")):
        _, result = request(management + "/actuator/metrics/" + metric + query)
        values[metric] = {entry["statistic"]: entry["value"] for entry in result["measurements"]}
    return values


def run_phase(args, sensor, count, workers, start):
    status, _ = request(args.storage + "/api/sensors", {"sensorId": sensor,
                        "name": "Essai pédagogique de performance", "maxPowerKw": 5,
                        "active": True, "samplingIntervalSeconds": 1})
    if status != 201:
        raise RuntimeError("Le capteur d'essai n'a pas été créé")
    before = snapshot(args.management)

    def send(index):
        stamp = (start + timedelta(seconds=index)).isoformat().replace("+00:00", "Z")
        tick = time.perf_counter()
        status, _ = request(args.collector + "/api/measurements", {
            "sensorId": sensor, "timestamp": stamp, "powerKw": 1 + index % 4,
            "voltage": 230, "temperature": 22})
        if status != 202:
            raise RuntimeError(f"Ingestion HTTP {status}")
        return time.perf_counter() - tick

    tick = time.perf_counter()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        times = list(pool.map(send, range(count)))
    ingest_duration = time.perf_counter() - tick
    deadline = time.monotonic() + args.deadline
    summary_url = args.storage + f"/api/sensors/{sensor}/daily-summary?date={start.date()}"
    while True:
        try:
            _, summary = request(summary_url)
            if summary["measurementCount"] == count:
                break
        except HTTPError as error:
            if error.code != 404:
                raise
        if time.monotonic() > deadline:
            raise TimeoutError(f"Persistance incomplète : {sensor}, attendu {count}")
        time.sleep(.5)
    completion = time.perf_counter() - tick
    history_url = args.storage + f"/api/measurements/{sensor}/day/{start.date()}"
    _, history = request(history_url)  # échauffement explicite, non mesuré
    if len(history) != count:
        raise RuntimeError("Compte Cassandra différent du compte accepté")
    reads = []
    for _ in range(args.reads):
        tick = time.perf_counter()
        _, history = request(history_url)
        reads.append(time.perf_counter() - tick)
        if len(history) != count:
            raise RuntimeError("Lecture Cassandra incomplète")
    after = snapshot(args.management)
    error_name = "smartenergy.storage.persistence.errors"
    if after[error_name]["COUNT"] != before[error_name]["COUNT"]:
        raise RuntimeError("Des erreurs de persistance sont survenues pendant l'essai")
    return {"sensorId": sensor, "measurements": count, "workers": workers,
            "accepted": count, "stored": len(history), "ingestion": distribution(times),
            "accepted_per_second": round(count / ingest_duration, 3),
            "ingestion_wall_seconds": round(ingest_duration, 3),
            "completion_wall_seconds": round(completion, 3),
            "history_via_http": distribution(reads), "metrics_before": before, "metrics_after": after}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--collector", default="http://127.0.0.1:8081")
    parser.add_argument("--storage", default="http://127.0.0.1:8083")
    parser.add_argument("--management", default="http://127.0.0.1:9083")
    parser.add_argument("--collector-management", default="http://127.0.0.1:9081")
    parser.add_argument("--small", type=int, default=30)
    parser.add_argument("--large", type=int, default=300)
    parser.add_argument("--workers", type=int, default=6)
    parser.add_argument("--reads", type=int, default=10)
    parser.add_argument("--deadline", type=int, default=120)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    if not (1 <= args.small < args.large <= 1000 and 1 <= args.workers <= 8
            and 1 <= args.reads <= 30 and 1 <= args.deadline <= 300):
        parser.error("Bornes : 1 <= small < large <= 1000, workers 1–8, reads 1–30, deadline 1–300 s")
    for management in (args.collector_management, args.management):
        _, health = request(management.rstrip("/") + "/actuator/health")
        if health.get("status") != "UP":
            raise RuntimeError("Les deux services doivent être UP")
    run = "perf-" + uuid.uuid4().hex[:12]
    start = (datetime.now(timezone.utc) - timedelta(days=1)).replace(hour=0, minute=0, second=0, microsecond=0)
    report = {"run": run, "executedAt": datetime.now(timezone.utc).isoformat(),
              "environment": {"os": platform.system(), "kernel": platform.release(),
                              "python": platform.python_version(), "logical_cpus": os.cpu_count()},
              "method": "Une exécution locale, petits puis grands volumes ; nouvelles connexions HTTP, lectures échauffées ; aucune garantie de SLA.",
              "phases": [run_phase(args, run + "-small", args.small, 1, start),
                         run_phase(args, run + "-large", args.large, args.workers, start)]}
    raw = json.dumps(report, ensure_ascii=False, indent=2, allow_nan=False)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(raw + "\n", encoding="utf-8")
    print(raw)


if __name__ == "__main__":
    main()
