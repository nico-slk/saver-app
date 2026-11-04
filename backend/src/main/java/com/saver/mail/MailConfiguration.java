package com.saver.mail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfiguration {

    @Bean
    MailSender mailSender(MailProperties props, ObjectMapper json) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return switch (props.provider().trim().toLowerCase(Locale.ROOT)) {
            case "log" -> new LogMailSender();
            case "mailpit" -> new MailpitMailSender(http, json, required(props.mailpitUrl(), "MAIL_MAILPIT_URL"),
                    props.fromAddress(), props.fromName());
            case "brevo" -> new BrevoMailSender(http, json, BrevoMailSender.ENDPOINT,
                    required(props.brevoApiKey(), "MAIL_BREVO_API_KEY"), props.fromAddress(), props.fromName());
            default -> throw new IllegalStateException("Unsupported mail provider: " + props.provider());
        };
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
