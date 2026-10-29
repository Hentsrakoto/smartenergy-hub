"""Scénario HTTP étape 9 ; conserve un capteur de preuve unique et trois mesures."""
import json
import time
import uuid
from urllib.request import Request, urlopen
from urllib.error import HTTPError


def request(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    with urlopen(Request(url, data=data, headers={"Content-Type": "application/json"}), timeout=10) as response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None


def main():
    sensor = "stage9-" + uuid.uuid4().hex[:12]
    base = "http://localhost:8083"
    status, _ = request(base + "/api/sensors", {
        "sensorId": sensor, "name": "Preuve Analytics étape 9", "active": True,
        "maxPowerKw": 5, "samplingIntervalSeconds": 60,
    })
    assert status == 201
    for minute, power in enumerate((1, 3, 6)):
        status, _ = request("http://localhost:8081/api/measurements", {
            "sensorId": sensor, "timestamp": f"2026-10-29T00:0{minute}:00Z",
            "powerKw": power, "voltage": 230, "temperature": None,
        })
        assert status == 202
    query = "?from=2026-10-29T00:00:00Z&to=2026-10-29T00:03:00Z"
    deadline = time.monotonic() + 30
    while True:
        _, summary = request(base + f"/api/analytics/{sensor}/summary" + query)
        if summary["measurementCount"] == 3:
            break
        assert time.monotonic() < deadline, summary
        time.sleep(0.5)
    expected = {"measurementCount": 3, "minPowerKw": 1, "avgPowerKw": 3.333333,
                "maxPowerKw": 6, "energyKwh": 0.166667, "anomalyCount": 1}
    for key, value in expected.items():
        assert summary[key] == value, (key, summary)
    _, anomalies = request(base + f"/api/analytics/{sensor}/anomalies" + query)
    assert len(anomalies) == 1 and anomalies[0]["reasons"] == ["POWER_ABOVE_MAX"]
    _, daily = request(base + f"/api/sensors/{sensor}/daily-summary?date=2026-10-29")
    assert daily["measurementCount"] == 3 and daily["energyKwh"] == 0.166667, daily
    try:
        request(base + f"/api/analytics/{sensor}/summary?from=2026-10-30T00:00:00Z&to=2026-10-29T00:00:00Z")
        raise AssertionError("Une période inversée doit être rejetée")
    except HTTPError as error:
        assert error.code == 400
    print(json.dumps({"sensorId": sensor, "summary": summary, "anomalies": anomalies}, ensure_ascii=False))


if __name__ == "__main__":
    main()
