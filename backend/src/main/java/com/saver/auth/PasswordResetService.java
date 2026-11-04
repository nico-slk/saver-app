package com.saver.auth;

import com.saver.common.ApiException;
import com.saver.config.WebProperties;
import com.saver.mail.MailMessage;
import com.saver.user.User;
import com.saver.user.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recuperación de contraseña por email.
 *
 * <ul>
 *   <li>La respuesta al pedido es siempre la misma, exista o no el email (no revela cuentas).</li>
 *   <li>Token aleatorio de un solo uso, válido {@value #TTL_MINUTES} minutos; solo se guarda su hash.</li>
 *   <li>Máximo {@value #MAX_REQUESTS} pedidos cada 15 minutos por usuario.</li>
 *   <li>Al restablecer se cierran todas las sesiones (refresh tokens) del usuario.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    static final long TTL_MINUTES = 30;
    static final int MAX_REQUESTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    /** Se publica dentro de la transacción y se envía después del commit (ver {@link PasswordResetMailer}). */
    public record ResetRequested(MailMessage message) {}

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final ApplicationEventPublisher events;
    private final WebProperties web;
    private final Clock clock;

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens,
                                RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                                TokenService tokenService, ApplicationEventPublisher events, WebProperties web,
                                Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.events = events;
        this.web = web;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String email) {
        Instant now = clock.instant();
        users.findByEmail(User.normalizeEmail(email)).ifPresentOrElse(user -> {
            if (tokens.countByUserIdAndCreatedAtAfter(user.getId(), now.minus(WINDOW)) >= MAX_REQUESTS) {
                log.info("Password reset rate-limited for user {}", user.getId());
                return;
            }
            tokens.invalidateAll(user.getId(), now);
            String raw = tokenService.newRefreshToken();
            tokens.save(new PasswordResetToken(user.getId(), TokenService.sha256(raw), now,
                    now.plus(Duration.ofMinutes(TTL_MINUTES))));
            String link = web.base() + "/reset-password?token=" + URLEncoder.encode(raw, StandardCharsets.UTF_8);
            events.publishEvent(new ResetRequested(
                    PasswordResetEmail.build(user.getEmail(), user.getDisplayName(), user.getLocale(), link, TTL_MINUTES)));
        }, () -> log.debug("Password reset requested for unknown email"));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void resetPassword(String rawToken, String newPassword) {
        Instant now = clock.instant();
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "auth.password_length");
        }
        PasswordResetToken token = tokens.findByTokenHash(TokenService.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "auth.reset_invalid"));
        if (token.getUsedAt() != null) throw new ApiException(HttpStatus.BAD_REQUEST, "auth.reset_invalid");
        if (!token.getExpiresAt().isAfter(now)) throw new ApiException(HttpStatus.BAD_REQUEST, "auth.reset_expired");

        User user = users.findById(token.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "auth.reset_invalid"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        tokens.invalidateAll(user.getId(), now);
        refreshTokens.revokeAllForUser(user.getId(), now);
    }

    @Scheduled(cron = "0 45 3 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
    }
}
