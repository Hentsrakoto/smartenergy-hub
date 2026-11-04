import json
import threading
import unittest
from http.server import BaseHTTPRequestHandler, HTTPServer
from prepare_demo import prepare_demo, definitions


class DemoPreparationTest(unittest.TestCase):
    def test_threshold_and_interval_follow_simulator_configuration(self):
        specs = definitions({"interval_seconds": 2, "sensors": [{
            "sensor_id": "sensor-002", "description": "Atelier", "p_base": 55, "amplitude": 15}]})
        self.assertEqual(specs[0]["maxPowerKw"], 87.5)
        self.assertEqual(specs[0]["samplingIntervalSeconds"], 2)

    def test_second_run_does_not_overwrite_an_existing_sensor(self):
        documents = {}
        writes = []

        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                sensor = documents.get(self.path.rsplit("/", 1)[-1])
                self.send_response(200 if sensor else 404)
                self.end_headers()
                self.wfile.write(json.dumps(sensor or {}).encode())

            def do_POST(self):
                sensor = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                documents[sensor["sensorId"]] = sensor
                writes.append(sensor)
                self.send_response(201)
                self.end_headers()
                self.wfile.write(json.dumps(sensor).encode())

            def log_message(self, *args):
                pass

        server = HTTPServer(("127.0.0.1", 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f"http://127.0.0.1:{server.server_port}"
            specs = [{"sensorId": "sensor-001", "maxPowerKw": 40}]
            self.assertEqual(prepare_demo(url, specs)[0]["action"], "created")
            documents["sensor-001"]["maxPowerKw"] = 99
            result = prepare_demo(url, specs)[0]
            self.assertEqual(result["action"], "preserved")
            self.assertFalse(result["matches"])
            self.assertEqual(documents["sensor-001"]["maxPowerKw"], 99)
            self.assertEqual(len(writes), 1)
        finally:
            server.shutdown()
            server.server_close()
            thread.join()
