package com.saver.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

/**
 * Brevo (plan gratuito: 300 emails/día). API {@code POST /v3/smtp/email} con header {@code api-key}.
 * El remitente ({@code app.mail.from-address}) debe estar verificado en la cuenta de Brevo.
 */
public class BrevoMailSender extends HttpJsonMailSender {

    static final URI ENDPOINT = URI.create("https://api.brevo.com/v3/smtp/email");

    private final String apiKey;
    private final String fromAddress;
    private final String fromName;

    public BrevoMailSender(HttpClient http, ObjectMapper json, URI endpoint, String apiKey, String fromAddress, String fromName) {
        super(http, json, endpoint);
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    protected Map<String, Object> body(MailMessage m) {
        return Map.of(
                "sender", Map.of("email", fromAddress, "name", fromName),
                "to", List.of(Map.of("email", m.to(), "name", m.toName() == null ? m.to() : m.toName())),
                "subject", m.subject(),
                "textContent", m.text(),
                "htmlContent", m.html());
    }

    @Override
    protected Map<String, String> headers() {
        return Map.of("api-key", apiKey);
    }
}
