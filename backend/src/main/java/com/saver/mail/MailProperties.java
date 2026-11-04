package com.saver.mail;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Envío de emails ({@code app.mail.*}).
 *
 * <ul>
 *   <li>{@code log}: no envía, escribe el email en el log (por defecto, útil sin Docker).</li>
 *   <li>{@code mailpit}: Mailpit en Docker; los emails se ven en http://localhost:8025 (sin internet).</li>
 *   <li>{@code brevo}: Brevo (ex Sendinblue), plan gratuito de 300 emails/día, envío real.</li>
 * </ul>
 * Para sumar un proveedor (ej. SES, SendGrid): implementar {@link MailSender} y registrarlo en {@link MailConfiguration}.
 */
@Validated
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        @NotBlank String provider,
        @NotBlank String fromAddress,
        @NotBlank String fromName,
        String mailpitUrl,
        String brevoApiKey) {}
