package com.saver.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.saver.support.InMemoryStorage;
import com.saver.support.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/** Cotizaciones del día: oficial 1450/1500, MEP 1500/1520, tarjeta 2000. */
@Import(InMemoryStorage.class)
class AccountsPaymentsFlowTest extends IntegrationTest {

    private static final String RENT = "00000000-0000-0000-0000-000000000001";
    private static final String OTHER = "00000000-0000-0000-0000-000000000004";

    @Autowired private JdbcTemplate jdbc;

    private final ZoneId ar = ZoneId.of("America/Argentina/Buenos_Aires");
    private final YearMonth now = YearMonth.now(ar);
    private final LocalDate today = LocalDate.now(ar);
    private String t;
    private String pesos;
    private String dollars;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("DELETE FROM exchange_rates");
        rate("OFICIAL", "1450", "1500");
        rate("MEP", "1500", "1520");
        rate("TARJETA", null, "2000");
        t = register();
        pesos = call(post("/api/accounts"), Map.of("name", "Banco", "kind", "CHECKING", "currency", "ARS",
                "openingBalance", "1000000")).get("id").asText();
        dollars = call(post("/api/accounts"), Map.of("name", "Dólares", "kind", "SAVINGS", "currency", "USD",
                "openingBalance", "500")).get("id").asText();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM exchange_rates");
    }

    private void rate(String type, String buy, String sell) {
        jdbc.update("INSERT INTO exchange_rates (day, type, buy, sell, source, fetched_at) VALUES (?, ?, ?, ?, 'test', ?)",
                Date.valueOf(today), type, buy == null ? null : new java.math.BigDecimal(buy), new java.math.BigDecimal(sell),
                Timestamp.from(Instant.now()));
    }

    private JsonNode call(MockHttpServletRequestBuilder req, Object body) throws Exception {
        req.header("Authorization", "Bearer " + t);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        String res = mvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
        return res.isEmpty() ? null : read(res);
    }

    private void fails(MockHttpServletRequestBuilder req, Object body, int status, String code) throws Exception {
        req.header("Authorization", "Bearer " + t);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(toJson(body));
        mvc.perform(req).andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code));
    }

    private Map<String, Object> expense(String desc, String currency, String amount, YearMonth start) {
        Map<String, Object> m = new HashMap<>();
        m.put("description", desc);
        m.put("categoryId", desc.equals("Alquiler") ? RENT : OTHER);
        m.put("kind", "RECURRING");
        m.put("currency", currency);
        m.put("amount", amount);
        m.put("startMonth", start.toString());
        return m;
    }

    private String occurrenceOf(String expenseId, YearMonth month) throws Exception {
        for (JsonNode o : call(get("/api/months/" + month), null).get("occurrences")) {
            if (o.get("expenseId").asText().equals(expenseId)) return o.get("id").asText();
        }
        throw new AssertionError("no occurrence");
    }

    private JsonNode account(String id) throws Exception {
        for (JsonNode a : call(get("/api/accounts"), null).get("accounts")) if (a.get("id").asText().equals(id)) return a;
        throw new AssertionError("no account");
    }

    @Test
    void netWorthValuesDollarsAtMep() throws Exception {
        JsonNode o = call(get("/api/accounts"), null);
        assertThat(o.get("netWorth").decimalValue()).isEqualByComparingTo("1750000");      // 1.000.000 + 500 × 1.500
        assertThat(o.get("netWorthSavings").decimalValue()).isEqualByComparingTo("750000");
        assertThat(o.get("valuationType").asText()).isEqualTo("MEP");

        // Comprar dólares: 152.000 pesos a MEP venta 1.520 = 100 USD
        JsonNode tr = call(post("/api/transfers"), Map.of("fromAccountId", pesos, "toAccountId", dollars, "amount", "152000"));
        assertThat(tr.get("toAmount").decimalValue()).isEqualByComparingTo("100");
        assertThat(account(pesos).get("balance").decimalValue()).isEqualByComparingTo("848000");
        assertThat(account(dollars).get("balance").decimalValue()).isEqualByComparingTo("600");

        // Ajuste: el saldo real del banco es 850.000
        call(post("/api/accounts/" + pesos + "/adjustments"), Map.of("targetBalance", "850000", "notes", "Intereses"));
        assertThat(account(pesos).get("balance").decimalValue()).isEqualByComparingTo("850000");
        JsonNode mv = call(get("/api/accounts/" + pesos + "/movements"), null);
        assertThat(mv.findValuesAsText("kind")).containsExactlyInAnyOrder("TRANSFER_OUT", "ADJUSTMENT");

        fails(delete("/api/accounts/" + pesos), null, 409, "account.has_movements");
        fails(post("/api/transfers"), Map.of("fromAccountId", pesos, "toAccountId", pesos, "amount", "1"), 400, "transfer.same_account");
    }

    @Test
    void pastMonthsChoiceIsRequiredAndAssumedPaymentsDoNotMoveBalances() throws Exception {
        fails(post("/api/expenses"), expense("Alquiler", "ARS", "400000", now.minusMonths(3)), 400,
                "expense.past_months_choice_required");

        Map<String, Object> rent = expense("Alquiler", "ARS", "400000", now.minusMonths(3));
        rent.put("pastMonthsPaid", true);
        String id = call(post("/api/expenses"), rent).get("id").asText();
        JsonNode month = call(get("/api/months/current"), null);
        assertThat(month.get("overdue")).isEmpty();
        JsonNode past = call(get("/api/months/" + now.minusMonths(2)), null);
        assertThat(past.at("/occurrences/0/status").asText()).isEqualTo("PAID");
        assertThat(account(pesos).get("balance").decimalValue()).isEqualByComparingTo("1000000");

        // Solo pagos asumidos: se puede borrar
        call(delete("/api/expenses/" + id), null);

        Map<String, Object> unpaid = expense("Alquiler", "ARS", "400000", now.minusMonths(2));
        unpaid.put("pastMonthsPaid", false);
        call(post("/api/expenses"), unpaid);
        assertThat(call(get("/api/months/current"), null).get("overdue")).hasSize(2);
    }

    @Test
    void partialPaymentsWithAttachments() throws Exception {
        String id = call(post("/api/expenses"), expense("Alquiler", "ARS", "400000", now)).get("id").asText();
        String occ = occurrenceOf(id, now);

        JsonNode first = call(post("/api/occurrences/" + occ + "/payments"), Map.of("accountId", pesos, "amount", "150000",
                "notes", "Transferencia"));
        assertThat(first.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(first.get("remaining").decimalValue()).isEqualByComparingTo("250000");
        JsonNode second = call(post("/api/occurrences/" + occ + "/payments"), Map.of("accountId", pesos, "amount", "250000"));
        assertThat(second.get("status").asText()).isEqualTo("PAID");
        assertThat(account(pesos).get("balance").decimalValue()).isEqualByComparingTo("600000");

        String paymentId = second.at("/payments/0/id").asText();
        MockMultipartFile pdf = new MockMultipartFile("files", "recibo.pdf", "application/pdf",
                "%PDF-1.4 recibo".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile png = new MockMultipartFile("files", "C:\\fotos\\foto.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2});
        String res = mvc.perform(multipart("/api/payments/" + paymentId + "/attachments").file(pdf).file(png)
                        .header("Authorization", "Bearer " + t))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode files = read(res);
        assertThat(files.findValuesAsText("fileName")).containsExactly("recibo.pdf", "foto.png");
        assertThat(files.findValuesAsText("contentType")).containsExactly("application/pdf", "image/png");

        MockMultipartFile fake = new MockMultipartFile("files", "virus.pdf", "application/pdf", "MZ...".getBytes());
        mvc.perform(multipart("/api/payments/" + paymentId + "/attachments").file(fake).header("Authorization", "Bearer " + t))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("attachment.invalid_type"));

        String attachmentId = files.get(0).get("id").asText();
        mvc.perform(get("/api/attachments/" + attachmentId).header("Authorization", "Bearer " + t))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().contentType("application/pdf"))
                .andExpect(MockMvcResultMatchers.content().string("%PDF-1.4 recibo"));

        int stored = InMemoryStorage.FILES.size();
        JsonNode after = call(delete("/api/payments/" + paymentId), null);
        assertThat(after.get("status").asText()).isEqualTo("PARTIAL");
        assertThat(InMemoryStorage.FILES.size()).isEqualTo(stored - 2);
        // Se borró el pago de 150.000 (el de los comprobantes): queda el de 250.000
        assertThat(account(pesos).get("balance").decimalValue()).isEqualByComparingTo("750000");
        fails(delete("/api/expenses/" + id), null, 409, "expense.has_payments");
    }

    @Test
    void dollarExpensesUseCardOrOfficialRate() throws Exception {
        String card = call(post("/api/cards"), Map.of("name", "Visa")).get("id").asText();
        Map<String, Object> netflix = expense("Netflix", "USD", "10", now);
        netflix.put("cardId", card);
        String withCard = call(post("/api/expenses"), netflix).get("id").asText();
        JsonNode p = call(post("/api/occurrences/" + occurrenceOf(withCard, now) + "/payments"),
                Map.of("accountId", pesos, "amount", "10"));
        assertThat(p.at("/payments/0/accountAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(p.at("/payments/0/rateType").asText()).isEqualTo("TARJETA");
        assertThat(p.get("cardId").asText()).isEqualTo(card);

        String plain = call(post("/api/expenses"), expense("Hosting", "USD", "10", now)).get("id").asText();
        String occ = occurrenceOf(plain, now);
        JsonNode official = call(post("/api/occurrences/" + occ + "/payments"), Map.of("accountId", pesos, "amount", "5"));
        assertThat(official.at("/payments/0/accountAmount").decimalValue()).isEqualByComparingTo("7500");
        JsonNode manual = call(post("/api/occurrences/" + occ + "/payments"), Map.of("accountId", pesos, "amount", "5", "rate", "1600"));
        assertThat(manual.at("/payments/1/accountAmount").decimalValue()).isEqualByComparingTo("8000");
        assertThat(manual.at("/payments/1/rateType").asText()).isEqualTo("MANUAL");

        // Pagar un gasto en pesos desde la cuenta en dólares: se venden dólares a oficial compra (1.450)
        String ars = call(post("/api/expenses"), expense("Gimnasio", "ARS", "29000", now)).get("id").asText();
        JsonNode fromUsd = call(post("/api/occurrences/" + occurrenceOf(ars, now) + "/payments"),
                Map.of("accountId", dollars, "amount", "29000"));
        assertThat(fromUsd.at("/payments/0/accountAmount").decimalValue()).isEqualByComparingTo("20");

        JsonNode preview = call(get("/api/exchange-rates/convert?amount=10&from=USD&to=ARS&purpose=PAYMENT_CARD"), null);
        assertThat(preview.get("amount").decimalValue()).isEqualByComparingTo("20000");

        call(delete("/api/cards/" + card), null);
        Map<String, Object> other = expense("Spotify", "USD", "5", now);
        other.put("cardId", card);
        fails(post("/api/expenses"), other, 400, "card.not_found");

        call(patch("/api/accounts/" + pesos), Map.of("archived", true));
        fails(post("/api/occurrences/" + occ + "/payments"), Map.of("accountId", pesos, "amount", "1"), 400, "account.archived");
    }

    @Test
    void incomeReceiptsMoveMoneyIn() throws Exception {
        String salary = call(post("/api/incomes"), Map.of("description", "Sueldo", "kind", "FIXED", "currency", "ARS",
                "amount", "900000", "startMonth", now.minusMonths(1).toString())).get("id").asText();
        JsonNode list = call(post("/api/incomes/" + salary + "/receipts"),
                Map.of("month", now.toString(), "accountId", pesos, "amount", "900000"));
        assertThat(list).hasSize(1);
        assertThat(account(pesos).get("balance").decimalValue()).isEqualByComparingTo("1900000");
        JsonNode month = call(get("/api/months/current"), null);
        assertThat(month.at("/incomes/0/received").decimalValue()).isEqualByComparingTo("900000");

        // Ingreso en dólares cobrado en pesos: oficial compra
        String usd = call(post("/api/incomes"), Map.of("description", "Freelance", "kind", "EXTRA", "currency", "USD",
                "amount", "100", "startMonth", now.toString(), "months", 1)).get("id").asText();
        JsonNode r = call(post("/api/incomes/" + usd + "/receipts"), Map.of("month", now.toString(), "accountId", pesos, "amount", "100"));
        assertThat(r.at("/0/accountAmount").decimalValue()).isEqualByComparingTo("145000");

        fails(post("/api/incomes/" + usd + "/receipts"), Map.of("month", now.plusMonths(1).toString(), "accountId", pesos, "amount", "1"),
                400, "receipt.month_inactive");
        fails(delete("/api/incomes/" + salary), null, 409, "income.has_receipts");
        call(delete("/api/incomes/receipts/" + list.get(0).get("id").asText()), null);
        call(delete("/api/incomes/" + salary), null);
    }

    @Test
    void movementsBeforeTheOpeningDateDoNotChangeTheBalance() throws Exception {
        String id = call(post("/api/expenses"), expense("Alquiler", "ARS", "100000", now)).get("id").asText();
        call(post("/api/occurrences/" + occurrenceOf(id, now) + "/payments"),
                Map.of("accountId", pesos, "amount", "100000", "date", today.minusDays(40).toString()));
        assertThat(account(pesos).get("balance").decimalValue()).isEqualByComparingTo("1000000");
        JsonNode mv = call(get("/api/accounts/" + pesos + "/movements"), null);
        assertThat(mv.at("/0/counted").asBoolean()).isFalse();
    }
}
