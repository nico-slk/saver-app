package com.saver.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpMailSendersTest {

    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newHttpClient();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private HttpServer server;
    private int status = 200;

    private final MailMessage message = new MailMessage("ana@example.com", "Ana", "Asunto", "texto", "<p>html</p>");

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            path.set(ex.getRequestURI().getPath());
            apiKey.set(ex.getRequestHeaders().getFirst("api-key"));
            body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = "{}".getBytes();
            ex.sendResponseHeaders(status, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Test
    void mailpitPostsToSendApi() throws Exception {
        new MailpitMailSender(http, json, base() + "/", "no-reply@saver.local", "Saver").send(message);

        assertThat(path.get()).isEqualTo("/api/v1/send");
        JsonNode b = json.readTree(body.get());
        assertThat(b.at("/From/Email").asText()).isEqualTo("no-reply@saver.local");
        assertThat(b.at("/To/0/Email").asText()).isEqualTo("ana@example.com");
        assertThat(b.get("Subject").asText()).isEqualTo("Asunto");
        assertThat(b.get("HTML").asText()).isEqualTo("<p>html</p>");
    }

    @Test
    void brevoSendsApiKeyAndPayload() throws Exception {
        new BrevoMailSender(http, json, URI.create(base() + "/v3/smtp/email"), "key-123", "me@saver.app", "Saver").send(message);

        assertThat(path.get()).isEqualTo("/v3/smtp/email");
        assertThat(apiKey.get()).isEqualTo("key-123");
        JsonNode b = json.readTree(body.get());
        assertThat(b.at("/sender/email").asText()).isEqualTo("me@saver.app");
        assertThat(b.at("/to/0/email").asText()).isEqualTo("ana@example.com");
        assertThat(b.get("htmlContent").asText()).isEqualTo("<p>html</p>");
        assertThat(b.get("textContent").asText()).isEqualTo("texto");
    }

    @Test
    void providerErrorsBecomeMailException() {
        status = 401;
        assertThatThrownBy(() -> new BrevoMailSender(http, json, URI.create(base() + "/x"), "bad", "a@b.c", "S").send(message))
                .isInstanceOf(MailException.class).hasMessageContaining("401");
    }
}
