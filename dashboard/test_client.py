import unittest
from datetime import date
from io import BytesIO
from unittest.mock import patch
from urllib.error import HTTPError, URLError

from dashboard.client import ApiError, anomaly_rows, chart_sample, get_json, period, sensor_path, storage_url


class ClientTest(unittest.TestCase):
    def test_inclusive_31_days(self):
        result = period(date(2026, 1, 1), date(2026, 1, 31))
        self.assertEqual(result["from"], "2026-01-01T00:00:00Z")
        self.assertEqual(result["to"], "2026-01-31T23:59:59.999999Z")

    def test_invalid_periods(self):
        for start, end in [(date(2026, 2, 1), date(2026, 1, 1)),
                           (date(2026, 1, 1), date(2026, 2, 1))]:
            with self.assertRaises(ValueError):
                period(start, end)

    def test_path_and_query_encoded(self):
        with patch("dashboard.client.urlopen", return_value=BytesIO(b"[]")) as request:
            self.assertEqual(get_json(sensor_path("measurements", "a/b", "history"), {"from": "2026-01-01T00:00:00Z"}), [])
            self.assertIn("a%2Fb/history?from=2026-01-01T00%3A00%3A00Z", request.call_args.args[0])
            self.assertEqual(request.call_args.kwargs["timeout"], 10)

    def test_analytics_uses_its_own_url(self):
        with patch.dict("os.environ", {"SMARTENERGY_STORAGE_URL": "http://storage:8080", "SMARTENERGY_ANALYTICS_URL": "http://analytics:8080"}):
            for path in ["/api/analytics/sensor-001/summary", "/api/sensors/sensor-001/daily-summary", "/api/sensors/sensor-001/daily-report"]:
                with patch("dashboard.client.urlopen", return_value=BytesIO(b"{}")) as request:
                    get_json(path)
                    self.assertTrue(request.call_args.args[0].startswith("http://analytics:8080/"))
            with patch("dashboard.client.urlopen", return_value=BytesIO(b"[]")) as request:
                get_json("/api/sensors")
                self.assertEqual(request.call_args.args[0], "http://storage:8080/api/sensors")

    def test_failure_names_correct_service(self):
        with patch("dashboard.client.urlopen", side_effect=URLError("private")):
            with self.assertRaisesRegex(ApiError, "Analytics"):
                get_json("/api/analytics/sensor-001/summary")

    def test_failures_are_presentable(self):
        errors = [URLError("private detail"), TimeoutError(),
                  HTTPError("url", 500, "private detail", {}, None)]
        for error in errors:
            with patch("dashboard.client.urlopen", side_effect=error):
                with self.assertRaises(ApiError) as result:
                    get_json("/api/sensors")
                self.assertNotIn("private detail", str(result.exception))

    def test_invalid_json(self):
        with patch("dashboard.client.urlopen", return_value=BytesIO(b"not-json")):
            with self.assertRaises(ApiError):
                get_json("/api/sensors")

    def test_credentials_rejected(self):
        with patch.dict("os.environ", {"SMARTENERGY_STORAGE_URL": "http://user:secret@localhost:8083"}):
            with self.assertRaises(ApiError):
                storage_url()

    def test_anomaly_translation_and_null_temperature(self):
        result = anomaly_rows([{"measurement": {"measuredAt": "2026-11-01T00:00:00Z", "powerKw": 8, "voltage": 230, "temperature": None}, "reasons": ["POWER_ABOVE_MAX"]}])
        self.assertEqual(result[0]["Motifs"], "Puissance trop élevée")

    def test_chart_sample_keeps_extremes_and_endpoints(self):
        measures = [{"measuredAt": f"{index:06d}", "powerKw": 5} for index in range(10000)]
        measures[5123]["powerKw"] = 999
        measures[5124]["powerKw"] = 0
        sample = chart_sample(list(reversed(measures)))
        self.assertLessEqual(len(sample), 2002)
        self.assertEqual(sample[0], measures[0])
        self.assertEqual(sample[-1], measures[-1])
        self.assertIn(measures[5123], sample)
        self.assertIn(measures[5124], sample)


if __name__ == "__main__":
    unittest.main()
