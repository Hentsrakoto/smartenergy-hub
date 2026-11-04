"""Enregistre les capteurs du simulateur sans écraser de configuration existante."""
import argparse
import json
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen


def request(url, payload=None):
    data = None if payload is None else json.dumps(payload).encode()
    try:
        with urlopen(Request(url, data=data, headers={"Content-Type": "application/json"}), timeout=15) as response:
            return response.status, json.loads(response.read())
    except HTTPError as error:
        if error.code in (404, 409):
            return error.code, None
        raise


def definitions(config):
    return [{"sensorId": item["sensor_id"], "name": item["description"],
             "building": "Démonstration du cours", "active": True, "tags": ["demo", "simulator"],
             "maxPowerKw": round((item["p_base"] + abs(item["amplitude"])) * 1.25, 3),
             "minVoltage": 210, "maxVoltage": 250, "maxTemperature": 45,
             "samplingIntervalSeconds": max(1, round(config["interval_seconds"]))}
            for item in config["sensors"]]


def prepare_demo(base, specs):
    results = []
    for spec in specs:
        url = base.rstrip("/") + "/api/sensors/" + spec["sensorId"]
        status, current = request(url)
        action = "preserved"
        if status == 404:
            status, current = request(base.rstrip("/") + "/api/sensors", spec)
            if status == 201:
                action = "created"
            elif status == 409:
                status, current = request(url)
            else:
                raise RuntimeError(f"Création inattendue : HTTP {status}")
        if not current:
            raise RuntimeError(f"Capteur inaccessible : {spec['sensorId']}")
        matches = all(current.get(key) == value for key, value in spec.items())
        results.append({"sensorId": spec["sensorId"], "action": action, "matches": matches})
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--storage-url", default="http://127.0.0.1:8083")
    parser.add_argument("--config", type=Path,
                        default=Path(__file__).resolve().parents[1] / "simulator/config.json")
    args = parser.parse_args()
    results = prepare_demo(args.storage_url, definitions(json.loads(args.config.read_text())))
    print(json.dumps(results, ensure_ascii=False, indent=2))
    if any(not result["matches"] for result in results):
        raise SystemExit("Configuration existante différente : conservée. Vérifier les seuils via l'API avant l'essai.")


if __name__ == "__main__":
    main()
