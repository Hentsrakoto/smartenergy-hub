import unittest
from measure_performance import distribution


class PerformanceStatisticsTest(unittest.TestCase):
    def test_statistics_are_in_milliseconds_with_nearest_rank_percentiles(self):
        result = distribution([.004, .001, .003, .002])
        self.assertEqual(result, {"samples": 4, "mean_ms": 2.5, "p50_ms": 2.0,
                                  "p95_ms": 4.0, "max_ms": 4.0})
