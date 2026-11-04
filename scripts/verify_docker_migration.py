#!/usr/bin/env python3
"""Contrôle E2E non destructif ; seuls les conteneurs applicatifs sont arrêtés.

Crée des capteurs/mesures de test distincts et conserve ces données pour audit.
Prérequis : les quatre applications Docker sont démarrées et healthy.
"""

import argparse
import json
import subprocess
import time
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


def docker(*args):
    return subprocess.check_output(["docker", "--context", "default", *args], text=True).strip()


def http(port, path, payload=None, method=None):
    body = json.dumps(payload).encode() if payload is not None else None
    request = Request(f"http://127.0.0.1:{port}{path}", data=body,
                      headers={"Content-Type": "application/json"}, method=method)
    try:
        with urlopen(request, timeout=15) as response:
            content = response.read()
            return response.status, json.loads(content) if content else None
    except HTTPError as error:
        raw = error.read()
        return error.code, json.loads(raw) if raw else None


def eventually(label, action, predicate, seconds=180):
    deadline = time.monotonic() + seconds
    last = None
    while time.monotonic() < deadline:
        try:
            last = action()
            if predicate(last):
                return last
        except (OSError, URLError, subprocess.CalledProcessError) as error:
            last = type(error).__name__
        time.sleep(.5)
    raise AssertionError(f"{label} : délai dépassé, dernier résultat={last}")


def pending():
    result = json.loads(docker("exec", "smartenergy-redis", "redis-cli", "--json",
                              "XPENDING", "energy-measurements", "storage-group"))
    return result[0]


def stream_length():
    return int(docker("exec", "smartenergy-redis", "redis-cli", "XLEN", "energy-measurements"))


def drained():
    groups = json.loads(docker("exec", "smartenergy-redis", "redis-cli", "--json", "XINFO", "GROUPS", "energy-measurements"))
    for row in groups:
        group = row if isinstance(row, dict) else dict(zip(row[::2], row[1::2]))
        if group["name"] == "storage-group":
            return group["pending"] == 0 and group["lag"] == 0
    return False


def identities():
    return json.loads(docker("inspect", "smartenergy-cassandra", "smartenergy-mongo", "smartenergy-redis"))


def base_identity(items):
    return {item["Name"]: {"id": item["Id"], "mounts": [(mount.get("Name"), mount["Destination"]) for mount in item["Mounts"]]} for item in items}


def run():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    proof = {"startedAt": datetime.now(timezone.utc).isoformat(), "checks": {}}
    checks = proof["checks"]
    baseline = base_identity(identities())
    proof["databaseIdentityBefore"] = baseline
    analytics_stopped = False
    try:
        for name in ["smartenergy-collector", "smartenergy-storage", "smartenergy-analytics", "smartenergy-dashboard"]:
            eventually(name, lambda n=name: docker("inspect", n, "--format", "{{.State.Health.Status}}"), lambda state: state == "healthy")
        checks["applicationsHealthy"] = True
        assert pending() == 0, "La PEL initiale doit être vide pour isoler ce scénario."
        original = http(8084, "/api/analytics/sensor-002/summary?" + urlencode({"from": "2026-11-01T00:00:00Z", "to": "2026-11-01T23:59:59.999999Z"}))
        proof["existingSensor002"] = original
        assert original[0] == 200 and original[1]["measurementCount"] == 4 and original[1]["anomalyCount"] == 2
        checks["existingDataPreserved"] = True

        sensor = "migration-" + uuid.uuid4().hex[:12]
        proof["sensorId"] = sensor
        instant = (datetime.now(timezone.utc) - timedelta(days=1)).replace(hour=12, minute=0, second=0, microsecond=0)
        day = instant.date().isoformat()
        summary_path = f"/api/sensors/{sensor}/daily-summary?date={day}"
        history_path = f"/api/measurements/{sensor}/day/{day}"
        sensor_payload = {"sensorId": sensor, "name": "Contrôle migration Docker", "maxPowerKw": 5,
                          "active": True, "samplingIntervalSeconds": 10}
        assert http(8083, "/api/sensors", sensor_payload)[0] == 201
        first = {"sensorId": sensor, "timestamp": instant.isoformat().replace("+00:00", "Z"),
                 "powerKw": 4, "voltage": 230, "temperature": 22}
        assert http(8081, "/api/measurements", first)[0] == 202
        first_summary = eventually("premier résumé", lambda: http(8084, summary_path), lambda result: result[0] == 200 and result[1]["measurementCount"] == 1)
        eventually("premier XACK", drained, bool)
        proof["firstSummary"] = first_summary[1]
        checks["acceptedPersistedAndAcknowledged"] = True

        before = stream_length()
        assert http(8081, "/api/measurements", dict(first, powerKw=-1))[0] == 400
        assert stream_length() == before
        checks["invalidDoesNotPublish"] = True

        docker("compose", "stop", "analytics-service")
        analytics_stopped = True
        second = dict(first, timestamp=(instant + timedelta(minutes=1)).isoformat().replace("+00:00", "Z"), powerKw=9)
        assert http(8081, "/api/measurements", second)[0] == 202
        eventually("Cassandra écrit malgré panne Analytics", lambda: http(8083, history_path), lambda result: result[0] == 200 and len(result[1]) == 2)
        proof["pendingDuringOutage"] = eventually("message non acquitté", pending, lambda value: value >= 1)
        unavailable = http(8083, summary_path)
        proof["compatibilityDuringOutage"] = unavailable[0]
        assert unavailable[0] == 503
        checks["analyticsOutageRetainsPending"] = True

        docker("start", "smartenergy-analytics")
        analytics_stopped = False
        eventually("Analytics redémarré", lambda: docker("inspect", "smartenergy-analytics", "--format", "{{.State.Health.Status}}"), lambda value: value == "healthy")
        final_summary = eventually("résumé après reprise", lambda: http(8084, summary_path), lambda result: result[0] == 200 and result[1]["measurementCount"] == 2)
        eventually("PEL vidée après reprise", drained, bool)
        assert final_summary[1]["anomalyCount"] == 1
        proof["recoveredSummary"] = final_summary[1]
        checks["recoveryWithoutDataLoss"] = True

        assert http(8081, "/api/measurements", second)[0] == 202
        eventually("rejeu acquitté", drained, bool)
        assert len(http(8083, history_path)[1]) == 2
        assert http(8084, summary_path)[1]["measurementCount"] == 2
        checks["duplicateDoesNotDoubleCount"] = True
        assert http(8083, summary_path) == http(8084, summary_path)
        checks["summaryCompatibility"] = True
        report_path = f"/api/sensors/{sensor}/daily-report?date={day}"
        report = http(8084, report_path)
        assert report[0] == 200 and report[1]["measurementCount"] == 2
        assert http(8083, report_path) == report
        proof["report"] = report[1]

        # Même sans métadonnées, une mesure brute doit être acquittée après le
        # seul code métier SENSOR_NOT_FOUND, jamais après un 404 quelconque.
        unknown = sensor + "-raw"
        assert http(8081, "/api/measurements", dict(first, sensorId=unknown))[0] == 202
        eventually("capteur non enregistré stocké", lambda: http(8083, f"/api/measurements/{unknown}/day/{day}"), lambda result: result[0] == 200 and len(result[1]) == 1)
        eventually("capteur non enregistré acquitté", drained, bool)
        missing = http(8084, f"/internal/sensors/{unknown}/daily-summary?date={day}", method="POST")
        assert missing[0] == 404 and missing[1]["code"] == "SENSOR_NOT_FOUND"
        checks["unknownSensorRawDataAllowed"] = True

        docker("restart", "smartenergy-collector", "smartenergy-storage", "smartenergy-analytics", "smartenergy-dashboard")
        for name in ["smartenergy-collector", "smartenergy-storage", "smartenergy-analytics", "smartenergy-dashboard"]:
            eventually(name, lambda n=name: docker("inspect", n, "--format", "{{.State.Health.Status}}"), lambda state: state == "healthy")
        assert len(http(8083, history_path)[1]) == 2
        assert http(8084, summary_path)[1]["measurementCount"] == 2
        assert http(8084, report_path) == report
        checks["applicationRestartPreservesDataAndReport"] = True
        after = base_identity(identities())
        assert after == baseline
        proof["databaseIdentityAfter"] = after
        checks["databaseContainersAndVolumesUnchanged"] = True
        proof["success"] = True
    finally:
        if analytics_stopped:
            docker("start", "smartenergy-analytics")
        proof["finishedAt"] = datetime.now(timezone.utc).isoformat()
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(proof, indent=2, ensure_ascii=False) + "\n")
    print(json.dumps({"success": proof["success"], "sensorId": proof["sensorId"], "checks": checks}, ensure_ascii=False))


if __name__ == "__main__":
    run()
