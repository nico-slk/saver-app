package com.saver.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.saver.support.IntegrationTest;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** Recorrido de la etapa 2: categorías, ingresos, gastos y balance mensual. */
class MonthlyFlowTest extends IntegrationTest {

    private static final String RENT = "00000000-0000-0000-0000-000000000001";
    private static final String INSTALLMENTS = "00000000-0000-0000-0000-000000000003";

    private final YearMonth now = YearMonth.now(ZoneId.of("America/Argentina/Buenos_Aires"));

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String token, Object body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        return mvc.perform(req);
    }

    private JsonNode month(String token, YearMonth m) throws Exception {
        return read(call(get("/api/months/" + m), token, null).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private static Map<String, Object> expense(String desc, String category, String kind, String amount,
                                               YearMonth start, Integer installments) {
        Map<String, Object> m = new HashMap<>();
        m.put("description", desc);
        m.put("categoryId", category);
        m.put("kind", kind);
        m.put("currency", "ARS");
        m.put("amount", amount);
        m.put("startMonth", start.toString());
        if (installments != null) m.put("installments", installments);
        m.put("pastMonthsPaid", false); // como antes: los meses pasados quedan impagos
        return m;
    }

    private static Map<String, Object> income(String desc, String kind, String amount, YearMonth start, Integer months) {
        Map<String, Object> m = new HashMap<>();
        m.put("description", desc);
        m.put("kind", kind);
        m.put("currency", "ARS");
        m.put("amount", amount);
        m.put("startMonth", start.toString());
        if (months != null) m.put("months", months);
        return m;
    }

    @Test
    void monthlyBalanceWithIncomesInstallmentsAndRecurringExpenses() throws Exception {
        String t = register();

        call(post("/api/incomes"), t, income("Sueldo", "FIXED", "1000000", now.minusMonths(1), null)).andExpect(status().isCreated());
        call(post("/api/incomes"), t, income("Bono", "EXTRA", "50000", now, 1)).andExpect(status().isCreated());
        call(post("/api/incomes"), t, income("Trabajo en partes", "EXTRA", "100000", now, 3)).andExpect(status().isCreated());

        call(post("/api/expenses"), t, expense("Tarjeta A", INSTALLMENTS, "INSTALLMENTS", "15000", now, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.endMonth").value(now.plusMonths(2).toString()))
                .andExpect(jsonPath("$.remainingInstallments").value(3))
                .andExpect(jsonPath("$.totalAmount").value(45000.0));
        call(post("/api/expenses"), t, expense("Tarjeta B", INSTALLMENTS, "INSTALLMENTS", "20000", now, 6)).andExpect(status().isCreated());
        call(post("/api/expenses"), t, expense("Tarjeta C", INSTALLMENTS, "INSTALLMENTS", "10000", now, 2)).andExpect(status().isCreated());
        Map<String, Object> rent = expense("Alquiler", RENT, "RECURRING", "500000", now.minusMonths(2), null);
        rent.put("dueDay", 10);
        rent.put("adjustEveryMonths", 4);
        rent.put("adjustPercent", "10");
        call(post("/api/expenses"), t, rent).andExpect(status().isCreated())
                .andExpect(jsonPath("$.nextAdjustmentMonth").value(now.plusMonths(2).toString()));

        // Mes actual: 1.000.000 + 50.000 + 100.000 de ingresos; 15.000 + 20.000 + 10.000 + 500.000 de gastos
        JsonNode cur = month(t, now);
        assertThat(cur.get("projected").asBoolean()).isFalse();
        JsonNode ars = cur.get("totals").get(0);
        assertThat(ars.get("income").decimalValue()).isEqualByComparingTo("1150000");
        assertThat(ars.get("expenses").decimalValue()).isEqualByComparingTo("545000");
        assertThat(ars.get("balance").decimalValue()).isEqualByComparingTo("605000");
        assertThat(cur.get("occurrences")).hasSize(4);
        // Los alquileres de los 2 meses anteriores quedaron impagos → vencidos
        assertThat(cur.get("overdue")).hasSize(2);
        assertThat(cur.get("overdue").get(0).get("status").asText()).isEqualTo("OVERDUE");
        assertThat(ars.get("overdue").decimalValue()).isEqualByComparingTo("1000000");
        JsonNode cardA = find(cur.get("occurrences"), "Tarjeta A");
        assertThat(cardA.get("installmentNumber").asInt()).isEqualTo(1);
        assertThat(cardA.get("installments").asInt()).isEqualTo(3);
        assertThat(cardA.get("dueDate").asText()).isEqualTo(now.atEndOfMonth().toString());

        // Mes +3 (proyectado): terminaron las tarjetas A y C y el trabajo en partes; el alquiler ya subió 10 %
        JsonNode future = month(t, now.plusMonths(3));
        assertThat(future.get("projected").asBoolean()).isTrue();
        JsonNode f = future.get("totals").get(0);
        assertThat(f.get("income").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(f.get("expenses").decimalValue()).isEqualByComparingTo("570000"); // 20.000 + 550.000
        assertThat(find(future.get("occurrences"), "Tarjeta B").get("installmentNumber").asInt()).isEqualTo(4);

        // Mes +12: solo sueldo y alquiler (con 3 aumentos estimados: 500.000 × 1,1³)
        JsonNode y = month(t, now.plusMonths(12)).get("totals").get(0);
        assertThat(y.get("income").decimalValue()).isEqualByComparingTo("1000000");
        assertThat(y.get("expenses").decimalValue()).isEqualByComparingTo("665500");
    }

    @Test
    void rentIncreaseUpdatesPendingOccurrencesAndHistoryKeepsDeletedCategory() throws Exception {
        String t = register();
        String cat = read(call(post("/api/categories"), t, Map.of("name", "Comida", "color", "#22c55e"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        call(post("/api/categories"), t, Map.of("name", "comida", "color", "#22c55e"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("category.name_taken"));

        String rentId = read(call(post("/api/expenses"), t, expense("Alquiler", RENT, "RECURRING", "500000", now, null))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
        call(post("/api/expenses"), t, expense("Super", cat, "ONE_TIME", "80000", now, null)).andExpect(status().isCreated());
        month(t, now); // genera las ocurrencias del mes

        call(post("/api/expenses/" + rentId + "/amounts"), t, Map.of("effectiveMonth", now.toString(), "amount", "620000"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currentAmount").value(620000.0));
        assertThat(find(month(t, now).get("occurrences"), "Alquiler").get("expectedAmount").decimalValue())
                .isEqualByComparingTo("620000");

        // Borrar la categoría: ya no se ofrece, pero el historial la sigue mostrando
        call(delete("/api/categories/" + cat), t, null).andExpect(status().isNoContent());
        JsonNode active = read(call(get("/api/categories"), t, null).andReturn().getResponse().getContentAsString());
        assertThat(active.findValuesAsText("id")).doesNotContain(cat);
        JsonNode all = read(call(get("/api/categories?includeDeleted=true"), t, null).andReturn().getResponse().getContentAsString());
        assertThat(all.findValuesAsText("id")).contains(cat);
        assertThat(find(month(t, now).get("occurrences"), "Super").get("categoryId").asText()).isEqualTo(cat);
        call(post("/api/expenses"), t, expense("Otro", cat, "ONE_TIME", "1", now, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("category.deleted"));

        // Los de sistema no se pueden borrar
        call(delete("/api/categories/" + RENT), t, null).andExpect(status().isForbidden());

        // Ajuste puntual de una ocurrencia (ej. factura de luz)
        String occId = find(month(t, now).get("occurrences"), "Super").get("id").asText();
        call(patch("/api/occurrences/" + occId), t, Map.of("expectedAmount", "91234.50", "notes", "ticket"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.expectedAmount").value(91234.5));

        // Dar de baja el alquiler: desaparece de los meses siguientes
        call(patch("/api/expenses/" + rentId), t, Map.of("endMonth", now.toString())).andExpect(status().isOk());
        assertThat(month(t, now.plusMonths(1)).get("occurrences")).isEmpty();
    }

    @Test
    void usersCannotSeeOrTouchEachOthersData() throws Exception {
        String a = register();
        String b = register();
        String id = read(call(post("/api/expenses"), a, expense("Privado", RENT, "RECURRING", "1000", now, null))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        assertThat(read(call(get("/api/expenses"), b, null).andReturn().getResponse().getContentAsString())).isEmpty();
        assertThat(month(b, now).get("occurrences")).isEmpty();
        call(delete("/api/expenses/" + id), b, null).andExpect(status().isNotFound());
        call(patch("/api/expenses/" + id), b, Map.of("description", "hack")).andExpect(status().isNotFound());
    }

    @Test
    void validationErrorsReturnCodes() throws Exception {
        String t = register();
        call(post("/api/expenses"), t, expense("", RENT, "INSTALLMENTS", "-5", now, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation.failed"))
                .andExpect(jsonPath("$.fields.description").value("validation.required"))
                .andExpect(jsonPath("$.fields.amount").value("validation.positive"));
        call(post("/api/expenses"), t, expense("Cuotas", RENT, "INSTALLMENTS", "10", now, null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("expense.installments_required"));
        call(get("/api/months/2026-13"), t, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("month.invalid"));
        mvc.perform(get("/api/months/current")).andExpect(status().isUnauthorized());
    }

    private static JsonNode find(JsonNode list, String description) {
        for (JsonNode n : list) if (description.equals(n.get("description").asText())) return n;
        throw new AssertionError("No existe " + description + " en " + list);
    }
}
