package com.saver.inflation;

import com.saver.common.HttpJsonClient;
import com.saver.common.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Inflación mensual de Argentina (IPC del INDEC) publicada por ArgentinaDatos, sin clave.
 * {@code GET /v1/finanzas/indices/inflacion} → {@code [{"fecha":"2026-09-30","valor":1.9}]} (valor en %).
 */
class ArgentinaDatosInflationProvider implements InflationProvider {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final HttpJsonClient client;
    private final URI endpoint;

    ArgentinaDatosInflationProvider(HttpJsonClient client, String baseUrl) {
        this.client = client;
        this.endpoint = URI.create(trim(baseUrl) + "/v1/finanzas/indices/inflacion");
    }

    @Override public String currency() { return "ARS"; }
    @Override public String source() { return "INDEC/ArgentinaDatos"; }

    @Override
    public List<MonthlyRate> fetch() {
        JsonNode body = client.get(endpoint);
        if (!body.isArray()) throw new ExternalApiException("ArgentinaDatos: unexpected response");
        List<MonthlyRate> result = new ArrayList<>();
        for (JsonNode n : body) {
            if (!n.hasNonNull("fecha") || !n.hasNonNull("valor") || !n.get("valor").isNumber()) continue;
            YearMonth month = YearMonth.from(LocalDate.parse(n.get("fecha").asText()));
            result.add(new MonthlyRate(month, n.get("valor").decimalValue().divide(HUNDRED, MathContext.DECIMAL64)));
        }
        return result;
    }

    static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
