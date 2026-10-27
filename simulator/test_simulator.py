#!/usr/bin/env python3
"""
Tests unitaires et d'intégration du simulateur IoT.
"""

import datetime
import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from sensor_simulator import AnomalyScenario, EnergySensorSimulator, SensorConfig


class MockCollectorHandler(BaseHTTPRequestHandler):
    received_requests = []

    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length).decode("utf-8")
        data = json.loads(body)
        MockCollectorHandler.received_requests.append(data)

        # Simulation de la validation du Collector :
        # Si powerKw < 0 ou voltage < 150 -> 400 Bad Request
        if data.get("powerKw", 0) < 0 or data.get("voltage", 0) < 150:
            self.send_response(400)
            self.send_header("Content-Type", "application/json")
            self.end_headers()
            self.wfile.write(b'{"status": 400, "error": "Bad Request"}')
        else:
            self.send_response(202)
            self.end_headers()

    def log_message(self, format, *args):
        # Silence logs
        pass


class TestSensorSimulator(unittest.TestCase):

    def setUp(self):
        self.sensor = SensorConfig(
            sensor_id="sensor-test-01",
            description="Capteur de test",
            p_base=30.0,
            amplitude=10.0,
            period=60.0,
            voltage_nominal=230.0,
            temp_base=22.0,
        )

    def test_measurement_structure_and_types(self):
        sim = EnergySensorSimulator(sensors=[self.sensor], dry_run=True)
        measurement = sim.generate_measurement(self.sensor, elapsed_seconds=15.0)

        self.assertEqual(measurement["sensorId"], "sensor-test-01")
        self.assertIsInstance(measurement["powerKw"], float)
        self.assertGreaterEqual(measurement["powerKw"], 0.0)
        self.assertIsInstance(measurement["voltage"], float)
        self.assertTrue(150.0 <= measurement["voltage"] <= 300.0)
        self.assertIsInstance(measurement["temperature"], float)

        # Vérification du format ISO-8601 UTC
        ts = measurement["timestamp"]
        self.assertTrue(ts.endswith("Z"))
        dt = datetime.datetime.fromisoformat(ts.replace("Z", "+00:00"))
        self.assertEqual(dt.tzinfo, datetime.timezone.utc)

    def test_power_sine_generation(self):
        sim = EnergySensorSimulator(sensors=[self.sensor], dry_run=True)

        # À t = 0 : sin(0) = 0 -> Pt ~ Pbase (30 kW)
        m0 = sim.generate_measurement(self.sensor, elapsed_seconds=0.0)
        self.assertAlmostEqual(m0["powerKw"], 30.0, delta=2.0)

        # À t = T/4 (15s) : sin(pi/2) = 1 -> Pt ~ Pbase + A (40 kW)
        m_quarter = sim.generate_measurement(self.sensor, elapsed_seconds=15.0)
        self.assertAlmostEqual(m_quarter["powerKw"], 40.0, delta=2.0)

        # À t = 3T/4 (45s) : sin(3pi/2) = -1 -> Pt ~ Pbase - A (20 kW)
        m_three_quarters = sim.generate_measurement(self.sensor, elapsed_seconds=45.0)
        self.assertAlmostEqual(m_three_quarters["powerKw"], 20.0, delta=2.0)

    def test_anomaly_injection_power_spike(self):
        anomaly = AnomalyScenario(
            target_sensor="sensor-test-01",
            anomaly_type="power_spike",
            start_second=10.0,
            duration_second=5.0,
        )
        sim = EnergySensorSimulator(sensors=[self.sensor], anomaly=anomaly, dry_run=True)

        # Avant l'anomalie (t=5s) -> normal (~30 kW)
        m_before = sim.generate_measurement(self.sensor, elapsed_seconds=5.0)
        self.assertTrue(m_before["powerKw"] < 50.0)

        # Pendant l'anomalie (t=12s) -> triplé (~90 kW)
        m_during = sim.generate_measurement(self.sensor, elapsed_seconds=12.0)
        self.assertTrue(m_during["powerKw"] > 70.0)

        # Après l'anomalie (t=20s) -> retour à la normale
        m_after = sim.generate_measurement(self.sensor, elapsed_seconds=20.0)
        self.assertTrue(m_after["powerKw"] < 50.0)

    def test_anomaly_injection_voltage_and_invalid(self):
        anomaly_voltage = AnomalyScenario("all", "voltage_drop", 0.0, 10.0)
        sim_v = EnergySensorSimulator(sensors=[self.sensor], anomaly=anomaly_voltage, dry_run=True)
        m_v = sim_v.generate_measurement(self.sensor, 5.0)
        self.assertEqual(m_v["voltage"], 195.0)

        anomaly_inv = AnomalyScenario("all", "invalid_negative", 0.0, 10.0)
        sim_inv = EnergySensorSimulator(sensors=[self.sensor], anomaly=anomaly_inv, dry_run=True)
        m_inv = sim_inv.generate_measurement(self.sensor, 5.0)
        self.assertEqual(m_inv["powerKw"], -10.0)

    def test_http_integration_with_mock_server(self):
        # Démarrage d'un serveur HTTP mock sur un port éphémère
        server = HTTPServer(("127.0.0.1", 0), MockCollectorHandler)
        port = server.server_port
        thread = threading.Thread(target=server.serve_forever)
        thread.daemon = True
        thread.start()

        MockCollectorHandler.received_requests.clear()

        try:
            sensors = [
                SensorConfig(f"sensor-00{i}", f"Capteur {i}", 20.0 + i, 5.0, 30.0)
                for i in range(1, 4)
            ]
            sim = EnergySensorSimulator(
                sensors=sensors,
                target_url=f"http://127.0.0.1:{port}/api/measurements",
                interval=0.05,
                dry_run=False,
            )

            stats = sim.run(duration_seconds=0.3)

            self.assertGreater(stats["total_sent"], 0)
            self.assertEqual(stats["status_202"], stats["total_sent"])
            self.assertEqual(stats["errors"], 0)
            self.assertEqual(len(MockCollectorHandler.received_requests), stats["total_sent"])

        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main()
