package com.saver.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.saver.support.IntegrationTest;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Datos: sueldo 1.000.000; alquiler 400.000 que sube a 480.000 hace un mes; luz variable
 * (50.000, 60.000, 70.000, 80.000); 3 cuotas de 30.000 desde el mes pasado.
 */
class HistoryProjectionFlowTest extends IntegrationTest {

    private static final String RENT = "00000000-0000-0000-0000-000000000001";
    private static final String UTILITIES = "00000000-0000-0000-0000-000000000002";
    private static final String INSTALLMENTS = "00000000-0000-0000-0000-000000000003";

    private final YearMonth now = YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires"));
    private String t;
    private String rentId;
    private String lightId;

    private JsonNode call(MockHttpServletRequestBuilder req, Object body) throws Exception {
        req.header("Authorization", "Bearer " + t);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        String res = mvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return res.isEmpty() ? null : read(res);
    }

    private Map<String, Object> expense(String desc, String cat, String kind, String amount, YearMonth start) {
        Map<String, Object> m = new HashMap<>();
        m.put("description", desc);
        m.put("categoryId", cat);
        m.put("kind", kind);
        m.put("currency", "ARS");
        m.put("amount", amount);
        m.put("startMonth", start.toString());
        m.put("pastMonthsPaid", false); // como antes: los meses pasados quedan impagos
        return m;
    }

    @BeforeEach
    void seed() throws Exception {
        t = register();
        call(post("/api/incomes"), Map.of("description", "Sueldo", "kind", "FIXED", "currency", "ARS",
                "amount", "1000000", "startMonth", now.minusMonths(3).toString()));
        rentId = call(post("/api/expenses"), expense("Alquiler", RENT, "RECURRING", "400000", now.minusMonths(3))).get("id").asText();
        call(post("/api/expenses/" + rentId + "/amounts"), Map.of("effectiveMonth", now.minusMonths(1).toString(), "amount", "480000"));
        Map<String, Object> light = expense("Luz", UTILITIES, "RECURRING", "50000", now.minusMonths(3));
        light.put("variableAmount", true);
        lightId = call(post("/api/expenses"), light).get("id").asText();
        Map<String, Object> card = expense("Tarjeta", INSTALLMENTS, "INSTALLMENTS", "30000", now.minusMonths(1));
        card.put("installments", 3);
        call(post("/api/expenses"), card);

        // Ajusta el monto real de la luz de cada mes
        String[] real = {"60000", "70000", "80000"};
        for (int i = 0; i < 3; i++) {
            JsonNode month = call(get("/api/months/" + now.minusMonths(2 - i)), null);
            for (JsonNode o : month.get("occurrences")) {
                if (o.get("expenseId").asText().equals(lightId)) {
                    call(patch("/api/occurrences/" + o.get("id").asText()), Map.of("expectedAmount", real[i]));
                }
            }
        }
    }

    @Test
    void historyShowsMonthlySavingsAndChanges() throws Exception {
        JsonNode h = call(get("/api/history?from=" + now.minusMonths(3) + "&to=" + now), null);
        JsonNode months = h.get("months");
        assertThat(months).hasSize(4);
        assertThat(months.get(0).get("expenses").decimalValue()).isEqualByComparingTo("450000");
        assertThat(months.get(0).get("savings").decimalValue()).isEqualByComparingTo("550000");
        assertThat(months.get(2).get("expenses").decimalValue()).isEqualByComparingTo("580000");
        assertThat(months.get(3).get("savings").decimalValue()).isEqualByComparingTo("410000");

        JsonNode s = h.get("summary");
        assertThat(s.at("/savingsVsPreviousMonth/diff").decimalValue()).isEqualByComparingTo("-10000");
        assertThat(s.at("/savingsVsPreviousMonth/percent").decimalValue()).isEqualByComparingTo("-2.38");
        assertThat(s.get("averageSavings").decimalValue()).isEqualByComparingTo("480000");
        assertThat(s.get("totalSavings").decimalValue()).isEqualByComparingTo("1920000");

        // Filtro por categoría: solo servicios (la luz); el ahorro no cambia
        JsonNode f = call(get("/api/history?from=" + now.minusMonths(3) + "&to=" + now + "&categories=" + UTILITIES), null);
        assertThat(f.at("/months/3/expenses").decimalValue()).isEqualByComparingTo("80000");
        assertThat(f.at("/months/3/expensesTotal").decimalValue()).isEqualByComparingTo("590000");
        assertThat(f.at("/months/3/savings").decimalValue()).isEqualByComparingTo("410000");
        assertThat(f.at("/summary/expensesFirstToLast/percent").decimalValue()).isEqualByComparingTo("60");
    }

    @Test
    void compareListsWhatWentUpAndNewExpenses() throws Exception {
        JsonNode c = call(get("/api/history/compare?from=" + now.minusMonths(3)), null);
        JsonNode items = c.get("items");
        assertThat(items.get(0).get("description").asText()).isEqualTo("Luz");
        assertThat(items.get(0).get("percent").decimalValue()).isEqualByComparingTo("60");
        assertThat(items.get(0).get("status").asText()).isEqualTo("UP");
        assertThat(items.get(1).get("description").asText()).isEqualTo("Alquiler");
        assertThat(items.get(1).get("percent").decimalValue()).isEqualByComparingTo("20");
        assertThat(items.get(2).get("status").asText()).isEqualTo("NEW");
        assertThat(c.at("/expenses/from").decimalValue()).isEqualByComparingTo("450000");
        assertThat(c.at("/expenses/to").decimalValue()).isEqualByComparingTo("590000");
        assertThat(c.at("/savings/diff").decimalValue()).isEqualByComparingTo("-140000");
        assertThat(c.get("averageSavings").decimalValue()).isEqualByComparingTo("480000");
        assertThat(c.get("months").asInt()).isEqualTo(4);
    }

    @Test
    void projectionAccumulatesSavingsAndKnowsWhenInstallmentsEnd() throws Exception {
        JsonNode p = call(get("/api/projections?months=4&startBalance=100000&inflationPercent=0"), null);
        JsonNode series = p.get("series");
        assertThat(series).hasSize(4);
        assertThat(series.get(0).get("expenses").decimalValue()).isEqualByComparingTo("590000");
        // Mes siguiente: la luz usa el promedio de los últimos 3 meses (70.000)
        assertThat(series.get(1).get("expenses").decimalValue()).isEqualByComparingTo("580000");
        assertThat(series.get(1).get("events").get(0).get("type").asText()).isEqualTo("INSTALLMENTS_END");
        assertThat(series.get(3).get("cumulative").decimalValue()).isEqualByComparingTo("1830000");
        JsonNode s = p.get("summary");
        assertThat(s.get("installmentsEndMonth").asText()).isEqualTo(now.plusMonths(1).toString());
        assertThat(s.get("averageSavingsAfterInstallments").decimalValue()).isEqualByComparingTo("450000");
        assertThat(s.get("averageMonthlySavings").decimalValue()).isEqualByComparingTo("432500");

        // Con 10 % de inflación mensual, el saldo final en valores de hoy es menor
        JsonNode real = call(get("/api/projections?months=4&startBalance=100000&inflationPercent=10"), null);
        assertThat(real.at("/summary/finalBalance").decimalValue()).isEqualByComparingTo("1830000");
        assertThat(real.at("/summary/finalBalanceReal").decimalValue()).isEqualByComparingTo("1465282.42");
    }

    @Test
    void manualInflationIsSavedAndUsedByDefault() throws Exception {
        call(put("/api/inflation/ARS/manual"), Map.of("monthlyPercent", "2.5"));
        JsonNode p = call(get("/api/projections?months=2"), null);
        assertThat(p.at("/inflation/monthlyPercent").decimalValue()).isEqualByComparingTo("2.5");
        assertThat(p.at("/inflation/source").asText()).isEqualTo("MANUAL");
        JsonNode screen = call(get("/api/projections?months=2&inflationPercent=4"), null);
        assertThat(screen.at("/inflation/source").asText()).isEqualTo("SCREEN");
        assertThat(screen.at("/series/1/inflationPercent").decimalValue()).isEqualByComparingTo("4");

        // El mes proyectado de la vista mensual también usa el promedio de la luz
        JsonNode future = call(get("/api/months/" + now.plusMonths(2)), null);
        assertThat(future.at("/totals/0/expenses").decimalValue()).isEqualByComparingTo("550000");

        mvc.perform(get("/api/projections?months=0").header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("projection.invalid_months"));
        mvc.perform(get("/api/history?from=" + now + "&to=" + now.minusMonths(2)).header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("history.invalid_range"));
    }
}
