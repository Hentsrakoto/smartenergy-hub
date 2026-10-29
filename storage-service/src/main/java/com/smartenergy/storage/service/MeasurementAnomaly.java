package com.smartenergy.storage.service;

import com.smartenergy.storage.cassandra.entity.MeasurementEntity;
import java.util.List;

public record MeasurementAnomaly(MeasurementEntity measurement, List<String> reasons) {
}
