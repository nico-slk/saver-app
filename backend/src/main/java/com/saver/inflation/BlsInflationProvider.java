package com.saver.inflation;

import com.saver.common.HttpJsonClient;
import com.saver.common.ExternalApiException;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Inflación mensual de EE.UU.: variación del IPC-U (serie CUUR0000SA0, sin desestacionalizar) de la
 * API pública de BLS. Sin clave permite 25 consultas por día (se hace una por día).
 * Si falta el índice de algún mes (ej. octubre 2025), la variación se reparte entre los meses del hueco.
 */
class BlsInflationProvider implements InflationProvider {

    static final String SERIES = "CUUR0000SA0";
    private static final int YEARS = 4;

    private final HttpJsonClient client;
    private final URI endpoint;
    private final String apiKey;
    private final Clock clock;

    BlsInflationProvider(HttpJsonClient client, String baseUrl, String apiKey, Clock clock) {
        this.client = client;
        this.endpoint = URI.create(ArgentinaDatosInflationProvider.trim(baseUrl) + "/publicAPI/v2/timeseries/data/");
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
        this.clock = clock;
    }

    @Override public String currency() { return "USD"; }
    @Override public String source() { return "BLS CPI-U"; }

    @Override
    public List<MonthlyRate> fetch() {
        int year = YearMonth.now(clock).getYear();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("seriesid", List.of(SERIES));
        body.put("startyear", String.valueOf(year - YEARS));
        body.put("endyear", String.valueOf(year));
        if (apiKey != null) body.put("registrationkey", apiKey);

        JsonNode res = client.post(endpoint, body);
        if (!"REQUEST_SUCCEEDED".equals(res.path("status").asText())) {
            throw new ExternalApiException("BLS: " + res.path("status").asText() + " " + res.path("message"));
        }
        TreeMap<YearMonth, BigDecimal> index = new TreeMap<>();
        for (JsonNode series : res.path("Results").path("series")) {
            for (JsonNode d : series.path("data")) {
                String period = d.path("period").asText();
                if (!period.matches("M(0[1-9]|1[0-2])")) continue; // M13 = promedio anual
                try {
                    index.put(YearMonth.of(d.path("year").asInt(), Integer.parseInt(period.substring(1))),
                            new BigDecimal(d.path("value").asText()));
                } catch (NumberFormatException ignored) {
                    // "-" = mes sin dato publicado
                }
            }
        }
        return rates(index);
    }

    /** Variación mensual a partir de los índices; reparte geométricamente los huecos. */
    static List<MonthlyRate> rates(TreeMap<YearMonth, BigDecimal> index) {
        List<MonthlyRate> result = new ArrayList<>();
        Map.Entry<YearMonth, BigDecimal> prev = null;
        for (Map.Entry<YearMonth, BigDecimal> cur : index.entrySet()) {
            if (prev != null) {
                long gap = java.time.temporal.ChronoUnit.MONTHS.between(prev.getKey(), cur.getKey());
                double ratio = cur.getValue().divide(prev.getValue(), MathContext.DECIMAL64).doubleValue();
                BigDecimal rate = BigDecimal.valueOf(Math.pow(ratio, 1.0 / gap) - 1).setScale(6, RoundingMode.HALF_UP);
                for (long k = 1; k <= gap; k++) result.add(new MonthlyRate(prev.getKey().plusMonths(k), rate));
            }
            prev = cur;
        }
        return result;
    }
}
