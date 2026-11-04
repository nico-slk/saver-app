package com.saver.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** URL pública del frontend (para armar links en los emails, ej. restablecer contraseña). */
@Validated
@ConfigurationProperties(prefix = "app.web")
public record WebProperties(@NotBlank String baseUrl) {

    /** Base sin barra final. */
    public String base() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
}
