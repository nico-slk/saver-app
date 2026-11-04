package com.saver.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.saver.support.IntegrationTest;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Inflación oficial de ARS cargada a mano: 3 % mensual de hace 12 a hace 4 meses y 2 % los últimos
 * 3 meses publicados (hasta el mes pasado) → inflación estimada 2 %. USD sin datos.
 */
class InflationFlowTest extends IntegrationTest {

    private static final String RENT = "00000000-0000-0000-0000-000000000001";

    @Autowired private JdbcTemplate jdbc;

    private final YearMonth now = YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    private String t;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("DELETE FROM inflation_rates");
        for (int k = 12; k >= 1; k--) {
            jdbc.update("INSERT INTO inflation_rates (currency, month, rate, source, fetched_at) VALUES ('ARS', ?, ?, 'test', ?)",
                    Date.valueOf(now.minusMonths(k).atDay(1)), new BigDecimal(k > 3 ? "0.03" : "0.02"),
                    Timestamp.from(Instant.now()));
        }
        t = register();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM inflation_rates");
    }

    private JsonNode call(MockHttpServletRequestBuilder req, Object body) throws Exception {
        req.header("Authorization", "Bearer " + t);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        String res = mvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return res.isEmpty() ? null : read(res);
    }

    private String rentLinkedToInflation(YearMonth start) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("description", "Alquiler");
        m.put("categoryId", RENT);
        m.put("kind", "RECURRING");
        m.put("currency", "ARS");
        m.put("amount", "100000");
        m.put("startMonth", start.toString());
        m.put("adjustEveryMonths", 3);
        m.put("adjustWithInflation", true);
        return call(post("/api/expenses"), m).get("id").asText();
    }

    @Test
    void overviewShowsOfficialDataEstimateAndManual() throws Exception {
        JsonNode o = call(get("/api/inflation"), null);
        JsonNode ars = o.at("/currencies/0");
        assertThat(ars.get("currency").asText()).isEqualTo("ARS");
        assertThat(ars.get("lastOfficialMonth").asText()).isEqualTo(now.minusMonths(1).toString());
        assertThat(ars.get("lastOfficialPercent").decimalValue()).isEqualByComparingTo("2");
        assertThat(ars.get("estimatedPercent").decimalValue()).isEqualByComparingTo("2");
        assertThat(ars.get("projectedSource").asText()).isEqualTo("ESTIMATED");
        assertThat(ars.get("recent")).hasSize(12);
        // 1,03^9 × 1,02^3 − 1 = 38,464 %
        assertThat(ars.get("last12MonthsPercent").decimalValue()).isEqualByComparingTo("38.464");
        JsonNode usd = o.at("/currencies/1");
        assertThat(usd.get("projectedSource").asText()).isEqualTo("NONE");
        assertThat(usd.get("projectedPercent").decimalValue()).isEqualByComparingTo("0");

        JsonNode after = call(put("/api/inflation/USD/manual"), Map.of("monthlyPercent", "0.3"));
        assertThat(after.at("/currencies/1/manualPercent").decimalValue()).isEqualByComparingTo("0.3");
        assertThat(after.at("/currencies/1/projectedSource").asText()).isEqualTo("MANUAL");
        call(delete("/api/inflation/USD/manual"), null);
        assertThat(call(get("/api/inflation"), null).at("/currencies/1/projectedSource").asText()).isEqualTo("NONE");

        mvc.perform(put("/api/inflation/EUR/manual").header("Authorization", "Bearer " + t)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlyPercent\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("inflation.invalid_currency"));
    }

    @Test
    void expensesAndIncomesLinkedToInflationAreProjected() throws Exception {
        rentLinkedToInflation(now.minusMonths(1));
        Map<String, Object> salary = new HashMap<>(Map.of("description", "Sueldo", "kind", "FIXED", "currency", "ARS",
                "amount", "1000000", "startMonth", now.minusMonths(1).toString()));
        salary.put("adjustEveryMonths", 3);
        salary.put("adjustPercent", "10");
        JsonNode income = call(post("/api/incomes"), salary);
        assertThat(income.get("nextAdjustmentMonth").asText()).isEqualTo(now.plusMonths(2).toString());

        JsonNode p = call(get("/api/projections?months=6"), null);
        JsonNode s = p.get("series");
        // Aumento a los 3 meses: inflación del mes pasado (oficial 2 %), este y el próximo (estimada 2 %)
        assertThat(s.at("/1/expenses").decimalValue()).isEqualByComparingTo("100000");
        assertThat(s.at("/2/expenses").decimalValue()).isEqualByComparingTo("106120.80");
        assertThat(s.at("/5/expenses").decimalValue()).isEqualByComparingTo("112616.24");
        assertThat(s.at("/2/income").decimalValue()).isEqualByComparingTo("1100000");
        assertThat(s.at("/2/events").findValuesAsText("type")).contains("INCREASE", "INCOME_INCREASE");
        assertThat(p.at("/inflation/source").asText()).isEqualTo("ESTIMATED");
        assertThat(p.at("/inflation/accumulatedPercent").decimalValue()).isEqualByComparingTo("12.616");
        assertThat(p.at("/summary/finalBalanceReal").decimalValue())
                .isLessThan(p.at("/summary/finalBalance").decimalValue());

        // La vista de un mes futuro usa la misma estimación
        JsonNode month = call(get("/api/months/" + now.plusMonths(2)), null);
        assertThat(month.at("/totals/0/expenses").decimalValue()).isEqualByComparingTo("106120.80");
        assertThat(month.at("/totals/0/income").decimalValue()).isEqualByComparingTo("1100000");

        // Cambiar la regla de un ingreso existente (y darlo de baja) responde con el ingreso completo
        String incomeId = income.get("id").asText();
        JsonNode linked = call(patch("/api/incomes/" + incomeId), Map.of("adjustWithInflation", true));
        assertThat(linked.get("adjustWithInflation").asBoolean()).isTrue();
        assertThat(linked.get("amounts")).hasSize(1);
        JsonNode ended = call(patch("/api/incomes/" + incomeId), Map.of("endMonth", now.plusMonths(12).toString()));
        assertThat(ended.get("endMonth").asText()).isEqualTo(now.plusMonths(12).toString());
        call(patch("/api/incomes/" + incomeId), Map.of("adjustPercent", "10", "adjustWithInflation", false));

        // Con inflación manual de 1 %: el mes pasado sigue siendo el oficial (2 %)
        call(put("/api/inflation/ARS/manual"), Map.of("monthlyPercent", "1"));
        JsonNode manual = call(get("/api/projections?months=3"), null);
        assertThat(manual.at("/series/2/expenses").decimalValue()).isEqualByComparingTo("104050.20");
        assertThat(manual.at("/inflation/source").asText()).isEqualTo("MANUAL");
    }

    @Test
    void compareDiscountsInflation() throws Exception {
        String id = rentLinkedToInflation(now.minusMonths(3));
        call(post("/api/expenses/" + id + "/amounts"), Map.of("effectiveMonth", now.toString(), "amount", "110000"));
        JsonNode c = call(get("/api/history/compare?from=" + now.minusMonths(3)), null);
        // Inflación de los 3 meses siguientes al inicial: 2 % + 2 % oficiales + 2 % estimado (mes actual)
        assertThat(c.get("inflationPercent").decimalValue()).isEqualByComparingTo("6.12");
        assertThat(c.get("inflationEstimated").asBoolean()).isTrue();
        assertThat(c.at("/items/0/percent").decimalValue()).isEqualByComparingTo("10");
        assertThat(c.at("/items/0/realPercent").decimalValue()).isEqualByComparingTo("3.66");

        JsonNode h = call(get("/api/history?from=" + now.minusMonths(3) + "&to=" + now), null);
        assertThat(h.at("/months/0/inflationOfficial").asBoolean()).isTrue();
        assertThat(h.at("/months/3/inflationOfficial").asBoolean()).isFalse();
        assertThat(h.at("/months/3/realFactor").decimalValue()).isEqualByComparingTo("1");
        assertThat(h.at("/months/0/realFactor").decimalValue()).isEqualByComparingTo("1.061208");
        assertThat(h.at("/summary/inflationPercent").decimalValue()).isEqualByComparingTo("6.12");
    }

    @Test
    void timelineGoesFromPastToFuture() throws Exception {
        rentLinkedToInflation(now.minusMonths(2));
        JsonNode d = call(get("/api/timeline"), null);
        assertThat(d.get("from").asText()).isEqualTo(now.minusMonths(6).toString());
        assertThat(d.get("to").asText()).isEqualTo(now.plusMonths(6).toString());
        JsonNode pts = d.get("points");
        assertThat(pts).hasSize(13);
        assertThat(pts.at("/0/type").asText()).isEqualTo("PAST");
        assertThat(pts.at("/6/type").asText()).isEqualTo("CURRENT");
        assertThat(pts.at("/6/month").asText()).isEqualTo(now.toString());
        assertThat(pts.at("/12/type").asText()).isEqualTo("FUTURE");
        assertThat(pts.at("/4/expenses").decimalValue()).isEqualByComparingTo("100000");
        assertThat(pts.at("/3/expenses").decimalValue()).isEqualByComparingTo("0");

        JsonNode custom = call(get("/api/timeline?from=" + now.minusMonths(2) + "&months=3"), null);
        assertThat(custom.get("points")).hasSize(3);
        assertThat(custom.at("/points/2/type").asText()).isEqualTo("CURRENT");
        JsonNode future = call(get("/api/timeline?from=" + now.plusMonths(2) + "&months=2"), null);
        assertThat(future.at("/points/0/month").asText()).isEqualTo(now.plusMonths(2).toString());
        assertThat(future.get("points")).hasSize(2);

        mvc.perform(get("/api/timeline?months=0").header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("timeline.invalid_months"));
    }
}
