"""Tests de l'interface avec réponses API simulées ; aucune écriture en base."""

import unittest
from datetime import date
from pathlib import Path
from unittest.mock import patch

from streamlit.testing.v1 import AppTest
from dashboard.client import ApiError


SENSOR = {"sensorId": "sensor-001", "name": "Salle informatique", "maxPowerKw": 10,
          "active": True, "samplingIntervalSeconds": 2}
MEASURE = {"measuredAt": "2026-11-01T12:00:00Z", "powerKw": 12, "voltage": 230, "temperature": None}
SUMMARY = {"measurementCount": 1, "energyKwh": .01, "avgPowerKw": 12,
           "maxPowerKw": 12, "minPowerKw": 12, "anomalyCount": 1}


def response(path, params=None):
    if path == "/api/sensors":
        return [SENSOR]
    if path.endswith("summary"):
        return SUMMARY
    if path.endswith("history"):
        return [MEASURE]
    if path.endswith("anomalies"):
        return [{"measurement": MEASURE, "reasons": ["POWER_ABOVE_MAX"]}]
    raise AssertionError(path)


class AppTests(unittest.TestCase):
    def app(self):
        return AppTest.from_file(str(Path(__file__).with_name("app.py")), default_timeout=15)

    def test_success_refresh_and_anomaly(self):
        with patch("dashboard.client.get_json", side_effect=response) as api:
            app = self.app().run()
            self.assertEqual(len(app.exception), 0)
            self.assertEqual(len(app.metric), 4)
            self.assertIn("dépassent", app.warning[0].value)
            self.assertEqual(app.dataframe[0].value.iloc[0]["Motifs"], "Puissance trop élevée")
            count = api.call_count
            app.button[0].click().run()
            self.assertGreater(api.call_count, count)

    def test_empty_sensors(self):
        with patch("dashboard.client.get_json", return_value=[]):
            app = self.app().run()
            self.assertFalse(app.exception)
            self.assertIn("Aucun capteur", app.info[0].value)

    def test_service_unavailable(self):
        with patch("dashboard.client.get_json", side_effect=ApiError("Service inaccessible")):
            app = self.app().run()
            self.assertFalse(app.exception)
            self.assertEqual(app.error[0].value, "Service inaccessible")
            self.assertFalse(app.metric)

    def test_empty_history(self):
        def empty(path, params=None):
            if path.endswith("history"):
                return []
            return response(path, params)
        with patch("dashboard.client.get_json", side_effect=empty):
            app = self.app().run()
            self.assertFalse(app.exception)
            self.assertTrue(any("Aucune mesure" in item.value for item in app.info))

    def test_invalid_period_does_not_query_measurements(self):
        with patch("dashboard.client.get_json", side_effect=response) as api:
            app = self.app().run()
            api.reset_mock()
            app.date_input[0].set_value(date(2026, 1, 1)).run()
            self.assertFalse(app.exception)
            self.assertTrue(any("31 jours" in item.value for item in app.warning))
            self.assertEqual(api.call_count, 1)

    def test_anomalies_failure_keeps_history(self):
        def unavailable(path, params=None):
            if path.endswith("anomalies"):
                raise ApiError("Anomalies indisponibles")
            return response(path, params)
        with patch("dashboard.client.get_json", side_effect=unavailable):
            app = self.app().run()
            self.assertFalse(app.exception)
            self.assertEqual(len(app.metric), 4)
            self.assertEqual(app.error[0].value, "Anomalies indisponibles")
            self.assertEqual(len(app.dataframe), 1)

    def test_table_pagination(self):
        def many(path, params=None):
            if path.endswith("history"):
                return [dict(MEASURE, powerKw=value) for value in range(105)]
            if path.endswith("summary"):
                return dict(SUMMARY, measurementCount=105)
            return response(path, params)
        with patch("dashboard.client.get_json", side_effect=many):
            app = self.app().run()
            self.assertFalse(app.exception)
            self.assertEqual(len(app.dataframe[1].value), 50)
            app.number_input[1].set_value(3).run()
            self.assertFalse(app.exception)
            self.assertEqual(len(app.dataframe[1].value), 5)


if __name__ == "__main__":
    unittest.main()
