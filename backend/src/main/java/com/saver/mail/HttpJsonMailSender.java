package com.saver.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Base para proveedores con API HTTP + JSON (sin dependencias extra: usa el HttpClient del JDK). */
abstract class HttpJsonMailSender implements MailSender {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final ObjectMapper json;
    private final URI endpoint;

    HttpJsonMailSender(HttpClient http, ObjectMapper json, URI endpoint) {
        this.http = http;
        this.json = json;
        this.endpoint = endpoint;
    }

    protected abstract Map<String, Object> body(MailMessage message);

    protected Map<String, String> headers() {
        return Map.of();
    }

    @Override
    public void send(MailMessage message) {
        try {
            HttpRequest.Builder req = HttpRequest.newBuilder(endpoint)
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body(message))));
            headers().forEach(req::header);
            HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new MailException(getClass().getSimpleName() + " responded " + res.statusCode() + ": " + res.body());
            }
        } catch (IOException e) {
            throw new MailException("Mail provider unreachable: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MailException("Interrupted while sending mail", e);
        }
    }
}
