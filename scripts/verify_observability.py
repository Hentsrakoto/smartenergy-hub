"""Contrôle Actuator local et évolution des métriques après ingestion réelle."""
import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen
from verify_analytics import main as verify_analytics


def get(url):
    with urlopen(url, timeout=15) as response:
        return json.load(response)


def metric(port, name, query=""):
    try:
        return get(f"http://127.0.0.1:{port}/actuator/metrics/{name}{query}")
    except HTTPError as error:
        if error.code == 404:
            return {"measurements": []}
        raise


def value(data, statistic):
    return next((item["value"] for item in data["measurements"] if item["statistic"] == statistic), 0)


def main():
    for port in (9081, 9083):
        health = get(f"http://127.0.0.1:{port}/actuator/health")
        assert health == {"status": "UP"}, health
        try:
            get(f"http://127.0.0.1:{port}/actuator/env")
            raise AssertionError("env ne doit pas être exposé")
        except HTTPError as error:
            assert error.code == 404
    tags = "?tag=uri:%2Fapi%2Fmeasurements&tag=status:"
    before_accepted = value(metric(9081, "http.server.requests", tags + "202"), "COUNT")
    before_rejected = value(metric(9081, "http.server.requests", tags + "400"), "COUNT")
    before_stored = value(metric(9083, "smartenergy.storage.persistence", "?tag=outcome:success"), "COUNT")
    verify_analytics()
    try:
        urlopen(Request("http://127.0.0.1:8081/api/measurements", data=b"{}",
                        headers={"Content-Type": "application/json"}), timeout=10)
        raise AssertionError("Mesure invalide acceptée")
    except HTTPError as error:
        assert error.code == 400
    accepted = value(metric(9081, "http.server.requests", tags + "202"), "COUNT") - before_accepted
    rejected = value(metric(9081, "http.server.requests", tags + "400"), "COUNT") - before_rejected
    stored = metric(9083, "smartenergy.storage.persistence", "?tag=outcome:success")
    assert accepted >= 3 and rejected >= 1
    assert value(stored, "COUNT") - before_stored >= 3
    assert value(stored, "TOTAL_TIME") > 0
    pending = metric(9083, "smartenergy.redis.pending")
    assert value(pending, "VALUE") == 0 and pending["measurements"]
    errors = metric(9083, "smartenergy.storage.persistence.errors")
    assert errors["measurements"]
    print(json.dumps({"acceptedDelta": accepted, "rejectedDelta": rejected,
                      "persistence": stored, "pending": pending, "errors": errors}))


if __name__ == "__main__":
    main()
