package com.saver.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

/** Mailpit (Docker, desarrollo): API {@code POST /api/v1/send}. Bandeja en http://localhost:8025. */
public class MailpitMailSender extends HttpJsonMailSender {

    private final String fromAddress;
    private final String fromName;

    public MailpitMailSender(HttpClient http, ObjectMapper json, String baseUrl, String fromAddress, String fromName) {
        super(http, json, URI.create(trim(baseUrl) + "/api/v1/send"));
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    protected Map<String, Object> body(MailMessage m) {
        return Map.of(
                "From", Map.of("Email", fromAddress, "Name", fromName),
                "To", List.of(Map.of("Email", m.to(), "Name", m.toName() == null ? "" : m.toName())),
                "Subject", m.subject(),
                "Text", m.text(),
                "HTML", m.html());
    }

    private static String trim(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
