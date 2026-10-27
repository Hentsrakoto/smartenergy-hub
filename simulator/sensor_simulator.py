#!/usr/bin/env python3
"""
Simulateur de capteurs IoT pour SmartEnergy Hub.

Génère des séries temporelles de mesures électriques réalistes basées sur un modèle sinusoïdal
avec bruit gaussien, conformément aux spécifications du guide du projet (Section 10.1).
Supporte l'injection d'anomalies (pics de puissance x3, chutes de tension, surchauffe, formats invalides).
"""

import argparse
import datetime
import json
import math
import random
import sys
import time
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional
import urllib.error
import urllib.request


@dataclass
class SensorConfig:
    sensor_id: str
    description: str
    p_base: float
    amplitude: float
    period: float
    voltage_nominal: float = 230.0
    temp_base: float = 22.0


@dataclass
class AnomalyScenario:
    target_sensor: str  # sensorId ou "all"
    anomaly_type: str   # "power_spike", "voltage_drop", "overheating", "invalid_negative", "invalid_voltage"
    start_second: float
    duration_second: float

    def is_active(self, elapsed_seconds: float, sensor_id: str) -> bool:
        if self.target_sensor != "all" and self.target_sensor != sensor_id:
            return False
        return self.start_second <= elapsed_seconds < (self.start_second + self.duration_second)


class EnergySensorSimulator:

    def __init__(
        self,
        sensors: List[SensorConfig],
        target_url: str = "http://localhost:8081/api/measurements",
        interval: float = 2.0,
        anomaly: Optional[AnomalyScenario] = None,
        dry_run: bool = False,
    ):
        self.sensors = sensors
        self.target_url = target_url
        self.interval = interval
        self.anomaly = anomaly
        self.dry_run = dry_run

        # Statistiques d'exécution
        self.stats: Dict[str, Any] = {
            "total_sent": 0,
            "status_202": 0,
            "status_400": 0,
            "errors": 0,
            "anomalies_injected": 0,
            "per_sensor": {s.sensor_id: {"sent": 0, "status_202": 0, "status_400": 0, "errors": 0} for s in sensors},
        }

    def generate_measurement(self, sensor: SensorConfig, elapsed_seconds: float) -> Dict[str, Any]:
        """
        Formule : Pt = Pbase + A * sin(2*pi*t / T) + epsilon_t
        """
        # 1. Calcul de base de la puissance active
        sine_val = math.sin((2.0 * math.pi * elapsed_seconds) / sensor.period)
        noise_p = random.gauss(0.0, 0.25)
        power_kw = round(max(0.0, sensor.p_base + (sensor.amplitude * sine_val) + noise_p), 3)

        # 2. Calcul de la tension efficace (nominal ~230V avec bruit léger)
        noise_v = random.gauss(0.0, 0.7)
        voltage = round(sensor.voltage_nominal + noise_v, 2)

        # 3. Calcul de la température (corrélée légèrement à la puissance)
        noise_t = random.gauss(0.0, 0.2)
        temperature = round(sensor.temp_base + (0.08 * power_kw) + noise_t, 2)

        # 4. Horodatage universel UTC au standard ISO-8601
        now_utc = datetime.datetime.now(datetime.timezone.utc)
        timestamp_str = now_utc.strftime("%Y-%m-%dT%H:%M:%SZ")

        # 5. Application des scénarios d'anomalies
        if self.anomaly and self.anomaly.is_active(elapsed_seconds, sensor.sensor_id):
            self.stats["anomalies_injected"] += 1
            if self.anomaly.anomaly_type == "power_spike":
                # Triplement de la puissance (Guide 10.1)
                power_kw = round(power_kw * 3.0, 3)
            elif self.anomaly.anomaly_type == "voltage_drop":
                voltage = 195.0
            elif self.anomaly.anomaly_type == "overheating":
                temperature = 82.5
            elif self.anomaly.anomaly_type == "invalid_negative":
                power_kw = -10.0  # Test rejet HTTP 400
            elif self.anomaly.anomaly_type == "invalid_voltage":
                voltage = 100.0   # Test rejet HTTP 400

        return {
            "sensorId": sensor.sensor_id,
            "timestamp": timestamp_str,
            "powerKw": power_kw,
            "voltage": voltage,
            "temperature": temperature,
        }

    def send_measurement(self, payload: Dict[str, Any]) -> int:
        """Envoie la mesure par HTTP POST et retourne le code de statut HTTP."""
        if self.dry_run:
            print(f"[DRY-RUN] {payload}")
            return 202

        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            self.target_url,
            data=data,
            headers={"Content-Type": "application/json"},
            method="POST",
        )

        try:
            with urllib.request.urlopen(req, timeout=3.0) as response:
                return response.status
        except urllib.error.HTTPError as e:
            return e.code
        except Exception as e:
            # Erreur de connexion (serveur non démarré, timeout, etc.)
            return -1

    def run(self, duration_seconds: float = 120.0) -> Dict[str, Any]:
        """Exécute la simulation multi-capteurs pendant la durée spécifiée."""
        print("=" * 70)
        print("  Démarrage du Simulateur IoT — SmartEnergy Hub")
        print(f"  Capteurs surveillés : {len(self.sensors)}")
        print(f"  Intervalle d'émission : {self.interval} s")
        print(f"  Durée de simulation : {duration_seconds} s")
        print(f"  Cible HTTP : {self.target_url} (dry_run={self.dry_run})")
        if self.anomaly:
            print(f"  Scénario anomalie : type={self.anomaly.anomaly_type}, cible={self.anomaly.target_sensor}, "
                  f"début={self.anomaly.start_second}s, durée={self.anomaly.duration_second}s")
        print("=" * 70)

        start_time = time.time()
        cycle = 0

        try:
            while True:
                elapsed = time.time() - start_time
                if duration_seconds > 0 and elapsed >= duration_seconds:
                    break

                cycle += 1
                for sensor in self.sensors:
                    payload = self.generate_measurement(sensor, elapsed)
                    status_code = self.send_measurement(payload)

                    self.stats["total_sent"] += 1
                    sensor_stat = self.stats["per_sensor"][sensor.sensor_id]
                    sensor_stat["sent"] += 1

                    if status_code == 202:
                        self.stats["status_202"] += 1
                        sensor_stat["status_202"] += 1
                        tag = "OK 202"
                    elif status_code == 400:
                        self.stats["status_400"] += 1
                        sensor_stat["status_400"] += 1
                        tag = "REJET 400"
                    else:
                        self.stats["errors"] += 1
                        sensor_stat["errors"] += 1
                        tag = f"ERR {status_code}"

                    anomaly_flag = " [ANOMALIE]" if (self.anomaly and self.anomaly.is_active(elapsed, sensor.sensor_id)) else ""
                    print(f"[{elapsed:6.1f}s] {sensor.sensor_id:15} | P={payload['powerKw']:6.2f} kW | "
                          f"V={payload['voltage']:5.1f} V | T={payload['temperature']:4.1f} °C | "
                          f"HTTP: {tag}{anomaly_flag}")

                time.sleep(self.interval)

        except KeyboardInterrupt:
            print("\nArrêt anticipé par l'utilisateur.")

        self.print_summary(time.time() - start_time)
        return self.stats

    def print_summary(self, real_duration: float):
        print("\n" + "=" * 70)
        print("  RÉSUMÉ DU TEST DE SIMULATION")
        print(f"  Durée réelle : {real_duration:.1f} secondes")
        print(f"  Total requêtes émises : {self.stats['total_sent']}")
        print(f"  Succès HTTP 202       : {self.stats['status_202']}")
        print(f"  Rejets HTTP 400       : {self.stats['status_400']}")
        print(f"  Erreurs de transport  : {self.stats['errors']}")
        print(f"  Anomalies injectées   : {self.stats['anomalies_injected']}")
        print("-" * 70)
        for sid, sstat in self.stats["per_sensor"].items():
            print(f"  {sid:15} -> Émises: {sstat['sent']}, 202: {sstat['status_202']}, "
                  f"400: {sstat['status_400']}, Err: {sstat['errors']}")
        print("=" * 70)


def load_config(config_path: str) -> Dict[str, Any]:
    with open(config_path, "r", encoding="utf-8") as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser(description="Simulateur de capteurs énergétiques IoT pour SmartEnergy Hub")
    parser.add_argument("--config", default="simulator/config.json", help="Chemin vers le fichier de configuration")
    parser.add_argument("--url", default=None, help="URL cible (écrase la valeur du config.json)")
    parser.add_argument("--interval", type=float, default=None, help="Intervalle entre émissions en secondes")
    parser.add_argument("--duration", type=float, default=120.0, help="Durée du test en secondes (défaut: 120s)")
    parser.add_argument("--anomaly-type", choices=["none", "power_spike", "voltage_drop", "overheating", "invalid_negative", "invalid_voltage"],
                        default="power_spike", help="Type d'anomalie à injecter")
    parser.add_argument("--anomaly-sensor", default="sensor-002", help="Capteur cible pour l'anomalie (ou 'all')")
    parser.add_argument("--anomaly-start", type=float, default=30.0, help="Secondes écoulées avant le déclenchement de l'anomalie")
    parser.add_argument("--anomaly-duration", type=float, default=20.0, help="Durée de l'anomalie en secondes")
    parser.add_argument("--dry-run", action="store_true", help="Génère et affiche les mesures sans envoyer de requêtes HTTP")
    parser.add_argument("--stats-file", default=None, help="Fichier de sortie JSON pour exporter les statistiques")

    args = parser.parse_args()

    # Chargement de la configuration
    try:
        cfg = load_config(args.config)
    except FileNotFoundError:
        # Fallback si exécuté depuis l'intérieur du dossier simulator
        cfg = load_config("config.json")

    target_url = args.url or cfg.get("target_url", "http://localhost:8081/api/measurements")
    interval = args.interval or cfg.get("interval_seconds", 2.0)

    sensors = [
        SensorConfig(
            sensor_id=s["sensor_id"],
            description=s["description"],
            p_base=s["p_base"],
            amplitude=s["amplitude"],
            period=s["period"],
            voltage_nominal=s.get("voltage_nominal", 230.0),
            temp_base=s.get("temp_base", 22.0),
        )
        for s in cfg["sensors"]
    ]

    anomaly = None
    if args.anomaly_type != "none":
        anomaly = AnomalyScenario(
            target_sensor=args.anomaly_sensor,
            anomaly_type=args.anomaly_type,
            start_second=args.anomaly_start,
            duration_second=args.anomaly_duration,
        )

    simulator = EnergySensorSimulator(
        sensors=sensors,
        target_url=target_url,
        interval=interval,
        anomaly=anomaly,
        dry_run=args.dry_run,
    )

    stats = simulator.run(duration_seconds=args.duration)

    if args.stats_file:
        with open(args.stats_file, "w", encoding="utf-8") as f:
            json.dump(stats, f, indent=2)
        print(f"Statistiques exportées dans {args.stats_file}")


if __name__ == "__main__":
    main()
