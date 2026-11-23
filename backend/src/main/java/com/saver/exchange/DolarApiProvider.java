package com.saver.exchange;

import com.fasterxml.jackson.databind.JsonNode;
import com.saver.common.ExternalApiException;
import com.saver.common.HttpJsonClient;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Cotizaciones de dolarapi.com: {@code GET /v1/dolares} →
 * {@code [{"casa":"oficial","compra":1520,"venta":1570,"fechaActualizacion":"2026-11-04T18:55:00.000Z"}]}.
 */
public class DolarApiProvider {

    private static final ZoneId AR = ZoneId.of("America/Argentina/Buenos_Aires");

    private final HttpJsonClient client;
    private final URI endpoint;
    private final Clock clock;

    public DolarApiProvider(HttpJsonClient client, String baseUrl, Clock clock) {
        this.client = client;
        this.endpoint = URI.create((baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl) + "/v1/dolares");
        this.clock = clock;
    }

    public List<ExchangeRate> fetch() {
        JsonNode body = client.get(endpoint);
        if (!body.isArray()) throw new ExternalApiException("dolarapi: unexpected response");
        Instant now = clock.instant();
        List<ExchangeRate> result = new ArrayList<>();
        for (JsonNode n : body) {
            ExchangeRateType type = ExchangeRateType.fromDolarApi(n.path("casa").asText());
            if (type == null || !n.path("venta").isNumber()) continue;
            LocalDate day = n.hasNonNull("fechaActualizacion")
                    ? Instant.parse(n.get("fechaActualizacion").asText()).atZone(AR).toLocalDate()
                    : LocalDate.now(clock.withZone(AR));
            result.add(new ExchangeRate(day, type, n.path("compra").isNumber() ? n.get("compra").decimalValue() : null,
                    n.get("venta").decimalValue(), "dolarapi", now));
        }
        return result;
    }
}
