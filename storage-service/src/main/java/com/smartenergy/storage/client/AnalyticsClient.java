package com.smartenergy.storage.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectReader;
import com.smartenergy.storage.mongo.exception.SensorNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Relais borné : seul le code métier exact autorise à poursuivre sans résumé. */
@Component
public class AnalyticsClient {
    private final RestTemplate http;
    private final ObjectMapper mapper;
    private final ObjectReader strictJson;
    private final String base;

    @Autowired
    public AnalyticsClient(RestTemplateBuilder builder, ObjectMapper mapper,
                           @Value("${smartenergy.analytics.url}") String base) {
        this(builder.setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(5)).build(), mapper, base);
    }

    public AnalyticsClient(RestTemplate http, ObjectMapper mapper, String base) {
        this.http = http;
        this.mapper = mapper;
        this.strictJson = mapper.readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.base = base;
    }

    public <T> T get(String path, Map<String, ?> query, Class<T> type) {
        return request(HttpMethod.GET, path, query, type, false);
    }

    public <T> T recompute(String id, String date, Class<T> type) {
        T response = request(HttpMethod.POST, "/internal/sensors/" + id + "/daily-summary",
                Map.of("date", date), type, true);
        JsonNode node = mapper.valueToTree(response);
        if (!id.equals(node.path("sensorId").asText()) || !date.equals(node.path("date").asText())) {
            throw new AnalyticsHttpException(503, null);
        }
        return response;
    }

    private <T> T request(HttpMethod method, String path, Map<String, ?> query,
                          Class<T> type, boolean internal) {
        var uri = UriComponentsBuilder.fromHttpUrl(base).path(path);
        query.forEach(uri::queryParam);
        try {
            String body = http.exchange(uri.build().encode().toUri(), method,
                    HttpEntity.EMPTY, String.class).getBody();
            JsonNode node = strictJson.readValue(body == null ? "" : body);
            validateResponse(node, type);
            return mapper.treeToValue(node, type);
        } catch (HttpStatusCodeException ex) {
            JsonNode error = readError(ex.getResponseBodyAsString());
            if (internal && ex.getStatusCode().value() == 404 && error != null
                    && error.path("code").isTextual()
                    && "SENSOR_NOT_FOUND".equals(error.path("code").textValue())) {
                throw new SensorNotFoundException(path);
            }
            if (!internal && (ex.getStatusCode().value() == 400 || ex.getStatusCode().value() == 404)
                    && error != null) {
                throw new AnalyticsHttpException(ex.getStatusCode().value(), error);
            }
            throw new AnalyticsHttpException(503, null);
        } catch (AnalyticsHttpException ex) {
            throw ex;
        } catch (Exception ex) {
            // Aucun détail réseau ou corps invalide n'est exposé à l'appelant.
            throw new AnalyticsHttpException(503, null);
        }
    }

    private JsonNode readError(String body) {
        try {
            JsonNode node = strictJson.readValue(body);
            return node != null && node.isObject() ? node : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void validateResponse(JsonNode node, Class<?> type) {
        if (node == null || (type.isArray() ? !node.isArray() : !node.isObject())) {
            throw new IllegalStateException("Réponse Analytics invalide");
        }
        Class<?> shape = type.isArray() ? type.componentType() : type;
        if (shape.isRecord()) {
            Iterable<JsonNode> values = node.isArray() ? node : List.of(node);
            for (JsonNode value : values) {
                for (var component : shape.getRecordComponents()) {
                    // Les primitives des records ne doivent pas masquer un champ absent par zéro.
                    if (!value.hasNonNull(component.getName())) {
                        throw new IllegalStateException("Champ Analytics absent");
                    }
                }
            }
        }
    }
}
