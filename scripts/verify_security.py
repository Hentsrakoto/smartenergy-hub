"""Vérification HTTP du profil demo sur les deux applications hôte."""
import json
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def response(url, method="GET", data=None):
    try:
        with urlopen(Request(url, method=method, data=data,
                             headers={"Content-Type": "application/json"}), timeout=15) as result:
            return result.status, result.read().decode()
    except HTTPError as error:
        return error.code, error.read().decode()


def main():
    for port in (8081, 8083):
        for path in ("/v3/api-docs", "/swagger-ui/index.html", "/actuator/health"):
            status, _ = response(f"http://127.0.0.1:{port}{path}")
            assert status == 404, (port, path, status)
    for path, method in (("status", "GET"), ("events", "GET"),
                         ("recover-pending", "POST"), ("simulate-failure?enabled=true", "POST")):
        status, _ = response(f"http://127.0.0.1:8083/api/storage/{path}", method)
        assert status == 404, (path, status)
    for port in (9081, 9083):
        status, body = response(f"http://127.0.0.1:{port}/actuator/health")
        assert status == 200 and json.loads(body) == {"status": "UP"}, body
        for path in ("env", "configprops", "heapdump", "shutdown"):
            status, _ = response(f"http://127.0.0.1:{port}/actuator/{path}")
            assert status == 404, (port, path, status)
    status, body = response("http://127.0.0.1:8081/api/measurements", "POST", b"{}")
    assert status == 400 and "stackTrace" not in body and "exception" not in body
    status, body = response("http://127.0.0.1:8083/api/measurements/sensor-001/history"
                            "?from=2026-01-01T00:00:00Z&to=2026-03-01T00:00:00Z")
    assert status == 400 and "31" in body, (status, body)
    print("Profil demo : Swagger/diagnostics/endpoints sensibles absents ; santé UP ; validations 400.")


if __name__ == "__main__":
    main()
