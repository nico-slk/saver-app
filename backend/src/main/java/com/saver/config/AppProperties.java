package com.saver.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Configuración tipada de la aplicación (prefijo {@code app}). */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @Valid Jwt jwt,
        @Valid Google google,
        @Valid Cors cors,
        @Valid Cookie cookie,
        @Valid Storage storage,
        @Valid Defaults defaults) {

    public record Jwt(
            @NotBlank @Size(min = 32, message = "JWT secret must be at least 32 characters") String secret,
            @NotBlank String issuer,
            @Min(1) long accessTtlMinutes,
            @Min(1) long refreshTtlDays) {}

    /** clientId vacío = login con Google deshabilitado. */
    public record Google(String clientId) {
        public boolean enabled() {
            return clientId != null && !clientId.isBlank();
        }
    }

    public record Cors(List<String> allowedOrigins) {}

    public record Cookie(boolean secure, String sameSite) {}

    /** Configuración agnóstica de proveedor para objetos multimedia privados. */
    public record Storage(
            @NotBlank String provider,
            @NotBlank String environment,
            String endpoint,
            @NotBlank String bucket,
            @NotBlank String region,
            String accessKey,
            String secretKey,
            @Min(1) long maxFileSizeBytes) {}

    public record Defaults(String timezone, String currency, String locale) {}
}
