package com.smartenergy.analytics.service;

import com.smartenergy.analytics.cassandra.entity.MeasurementEntity;
import java.util.List;

public record MeasurementAnomaly(MeasurementEntity measurement, List<String> reasons) {
}
