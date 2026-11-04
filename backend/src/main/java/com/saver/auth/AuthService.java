package com.saver.auth;

import com.saver.auth.AuthDtos.GoogleLoginRequest;
import com.saver.auth.AuthDtos.LoginRequest;
import com.saver.auth.AuthDtos.RegisterRequest;
import com.saver.auth.GoogleTokenVerifier.GoogleIdentity;
import com.saver.common.ApiException;
import com.saver.config.AppProperties;
import com.saver.user.User;
import com.saver.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_PASSWORD_BYTES = 72;
    private static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    /** Resultado interno: el refresh token en claro solo se usa para setear la cookie. */
    public record AuthResult(User user, String accessToken, long expiresIn, String refreshToken) {}

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    private final GoogleTokenVerifier google;
    private final AppProperties.Defaults defaults;
    private final Clock clock;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       TokenService tokens, GoogleTokenVerifier google, AppProperties props, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.google = google;
        this.defaults = props.defaults();
        this.clock = clock;
    }

    @Transactional
    public AuthResult register(RegisterRequest req, String userAgent) {
        String email = User.normalizeEmail(req.email());
        if (users.existsByEmail(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "auth.email_taken");
        }
        // BCrypt solo admite 72 *bytes*; con acentos/ñ, 72 caracteres pueden superar ese límite.
        if (req.password().getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "auth.password_length");
        }
        User user = new User(email, req.displayName().trim(), localeOrDefault(req.locale()),
                defaults.currency(), defaults.timezone());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user = users.save(user);
        return issue(user, userAgent);
    }

    @Transactional
    public AuthResult login(LoginRequest req, String userAgent) {
        User user = users.findByEmail(User.normalizeEmail(req.email()))
                .filter(User::hasPassword)
                .filter(u -> passwordMatches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.invalid_credentials"));
        return issue(user, userAgent);
    }

    /**
     * Login/registro con Google. Si existe un usuario con el mismo email verificado,
     * se vincula la cuenta de Google a ese usuario.
     */
    @Transactional
    public AuthResult loginWithGoogle(GoogleLoginRequest req, String userAgent) {
        GoogleIdentity id = google.verify(req.idToken());
        User user = users.findByGoogleSub(id.subject())
                .or(() -> users.findByEmail(User.normalizeEmail(id.email())).map(u -> {
                    u.setGoogleSub(id.subject());
                    return u;
                }))
                .orElseGet(() -> {
                    String name = id.name() != null && !id.name().isBlank() ? id.name() : id.email();
                    User u = new User(id.email(), name, localeOrDefault(req.locale()),
                            defaults.currency(), defaults.timezone());
                    u.setGoogleSub(id.subject());
                    return users.save(u);
                });
        return issue(user, userAgent);
    }

    /**
     * Rotación de refresh token. Si se presenta un token ya revocado (posible robo),
     * se revocan todas las sesiones del usuario.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawToken, String userAgent) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_missing");
        }
        Instant now = clock.instant();
        RefreshToken current = refreshTokens.findByTokenHash(TokenService.sha256(rawToken))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_invalid"));
        if (current.getRevokedAt() != null) {
            // Ventana de gracia: dos pestañas refrescando a la vez no es un robo de token.
            if (current.getRevokedAt().isAfter(now.minus(REUSE_GRACE))) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_invalid");
            }
            log.warn("Refresh token reuse detected for user {}", current.getUserId());
            refreshTokens.revokeAllForUser(current.getUserId(), now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_invalid");
        }
        if (!current.isActive(now)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.refresh_expired");
        }
        current.revoke(now);
        User user = users.findById(current.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "auth.user_not_found"));
        return issue(user, userAgent);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        refreshTokens.findByTokenHash(TokenService.sha256(rawToken)).ifPresent(t -> t.revoke(clock.instant()));
    }

    /** Limpieza diaria de refresh tokens vencidos hace más de 7 días. */
    @Scheduled(cron = "0 30 3 * * *", zone = "America/Argentina/Buenos_Aires")
    @Transactional
    public void purgeExpiredTokens() {
        int deleted = refreshTokens.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(7)));
        if (deleted > 0) log.info("Purged {} expired refresh tokens", deleted);
    }

    private AuthResult issue(User user, String userAgent) {
        String raw = tokens.newRefreshToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenService.sha256(raw),
                clock.instant().plus(tokens.refreshTtl()), userAgent));
        return new AuthResult(user, tokens.createAccessToken(user), tokens.accessTtlSeconds(), raw);
    }

    private boolean passwordMatches(String raw, String hash) {
        if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) return false;
        try {
            return passwordEncoder.matches(raw, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private String localeOrDefault(String locale) {
        return locale != null && !locale.isBlank() ? locale : defaults.locale();
    }
}
