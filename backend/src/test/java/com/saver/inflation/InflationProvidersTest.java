package com.saver.inflation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InflationProvidersTest {

    private HttpServer server;
    private final AtomicReference<String> response = new AtomicReference<>();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpJsonClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            requestBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = response.get().getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(response.get().startsWith("!") ? 500 : 200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        client = new HttpJsonClient(HttpClient.newHttpClient(), new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @Test
    void argentinaDatosPercentagesBecomeFractions() {
        response.set("[{\"fecha\":\"2026-08-31\",\"valor\":1.7},{\"fecha\":\"2026-09-30\",\"valor\":1.9}]");
        var rates = new ArgentinaDatosInflationProvider(client, base()).fetch();
        assertThat(rates).hasSize(2);
        assertThat(rates.get(1).month()).isEqualTo(YearMonth.of(2026, 9));
        assertThat(rates.get(1).rate()).isEqualByComparingTo("0.019");
    }

    @Test
    void blsIndexBecomesMonthlyRatesAndSkipsAnnualAverage() {
        response.set("""
                {"status":"REQUEST_SUCCEEDED","Results":{"series":[{"seriesID":"CUUR0000SA0","data":[
                 {"year":"2026","period":"M13","value":"999"},
                 {"year":"2026","period":"M02","value":"202"},
                 {"year":"2026","period":"M01","value":"200"}]}]}}""");
        Clock clock = Clock.fixed(Instant.parse("2026-11-01T00:00:00Z"), ZoneOffset.UTC);
        var rates = new BlsInflationProvider(client, base(), "", clock).fetch();
        assertThat(rates).hasSize(1);
        assertThat(rates.getFirst().month()).isEqualTo(YearMonth.of(2026, 2));
        assertThat(rates.getFirst().rate()).isEqualByComparingTo("0.01");
        assertThat(requestBody.get()).contains("\"startyear\":\"2022\"").doesNotContain("registrationkey");
    }

    @Test
    void errorsAreReportedAsFetchExceptions() {
        response.set("!boom");
        assertThatThrownBy(() -> new ArgentinaDatosInflationProvider(client, base()).fetch())
                .isInstanceOf(InflationFetchException.class).hasMessageContaining("500");
    }
}
