package com.smartenergy.storage.client;

import com.fasterxml.jackson.databind.JsonNode;

/** Erreur amont relayable (400/404) ou indisponibilité anonymisée (503). */
public class AnalyticsHttpException extends RuntimeException {
    private final int status;
    private final JsonNode body;

    public AnalyticsHttpException(int status, JsonNode body) {
        super("Service Analytics indisponible");
        this.status = status;
        this.body = body;
    }

    public int status() { return status; }
    public JsonNode body() { return body; }
}
