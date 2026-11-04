package com.saver.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saver.config.AppProperties;
import com.saver.security.JwtConfig;
import com.saver.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

    private static AppProperties props(String secret) {
        return new AppProperties(
                new AppProperties.Jwt(secret, "saver-api", 15, 30),
                new AppProperties.Google(""),
                new AppProperties.Cors(List.of("http://localhost:5173")),
                new AppProperties.Cookie(false, "Strict"),
                new AppProperties.Storage("s3-compatible", "development", "http://localhost:8333", "saver-dev-media", "us-east-1", "saver-dev", "saver-dev-secret", 10 * 1024 * 1024),
                new AppProperties.Defaults("America/Argentina/Buenos_Aires", "ARS", "es"));
    }

    private static final String SECRET = "test-secret-test-secret-test-secret-123";

    @Test
    void accessTokenRoundTrip() {
        AppProperties p = props(SECRET);
        JwtConfig cfg = new JwtConfig(p);
        TokenService service = new TokenService(cfg.jwtEncoder(), p, Clock.systemUTC());
        User user = new User("Nico@Example.com", "Nico", "es", "ARS", "America/Argentina/Buenos_Aires");

        String token = service.createAccessToken(user);
        Jwt jwt = cfg.jwtDecoder().decode(token);

        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("nico@example.com");
        assertThat(service.accessTtlSeconds()).isEqualTo(15 * 60);
    }

    @Test
    void tokenSignedWithOtherSecretIsRejected() {
        AppProperties p = props(SECRET);
        TokenService other = new TokenService(new JwtConfig(props("another-secret-another-secret-another-1")).jwtEncoder(),
                p, Clock.systemUTC());
        String token = other.createAccessToken(new User("a@b.com", "A", "es", "ARS", "UTC"));
        JwtDecoder decoder = new JwtConfig(p).jwtDecoder();

        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void expiredTokenIsRejected() {
        AppProperties p = props(SECRET);
        JwtConfig cfg = new JwtConfig(p);
        Clock past = Clock.fixed(Instant.now().minusSeconds(3600), ZoneOffset.UTC);
        String token = new TokenService(cfg.jwtEncoder(), p, past)
                .createAccessToken(new User("a@b.com", "A", "es", "ARS", "UTC"));

        assertThatThrownBy(() -> cfg.jwtDecoder().decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokensAreRandomAndHashStable() {
        AppProperties p = props(SECRET);
        TokenService service = new TokenService(new JwtConfig(p).jwtEncoder(), p, Clock.systemUTC());
        String a = service.newRefreshToken();
        String b = service.newRefreshToken();

        assertThat(a).isNotEqualTo(b).hasSizeGreaterThanOrEqualTo(43);
        assertThat(TokenService.sha256(a)).isEqualTo(TokenService.sha256(a)).hasSize(64);
    }
}
