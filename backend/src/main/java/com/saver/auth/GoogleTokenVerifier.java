package com.saver.auth;

import com.saver.common.ApiException;
import com.saver.config.AppProperties;
import java.util.Collection;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Verifica el ID token que entrega Google Identity Services en el frontend:
 * firma (JWKS de Google), expiración, emisor y audiencia (nuestro client id).
 */
@Component
public class GoogleTokenVerifier {

    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");

    private final AppProperties.Google config;
    private volatile NimbusJwtDecoder decoder;

    public GoogleTokenVerifier(AppProperties props) {
        this.config = props.google();
    }

    public record GoogleIdentity(String subject, String email, String name, String locale) {}

    public boolean isEnabled() {
        return config != null && config.enabled();
    }

    public String clientId() {
        return isEnabled() ? config.clientId() : null;
    }

    public GoogleIdentity verify(String idToken) {
        if (!isEnabled()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "auth.google_disabled");
        }
        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException e) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.google_invalid_token");
        }
        Object emailVerified = jwt.getClaims().get("email_verified");
        boolean verified = Boolean.TRUE.equals(emailVerified) || "true".equals(String.valueOf(emailVerified));
        String email = jwt.getClaimAsString("email");
        if (email == null || !verified) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "auth.google_email_not_verified");
        }
        return new GoogleIdentity(jwt.getSubject(), email, jwt.getClaimAsString("name"), jwt.getClaimAsString("locale"));
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder d = decoder;
        if (d == null) {
            synchronized (this) {
                d = decoder;
                if (d == null) {
                    d = NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build();
                    String clientId = config.clientId();
                    d.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            new JwtTimestampValidator(),
                            new JwtClaimValidator<Object>("iss", iss -> iss != null && ISSUERS.contains(iss.toString())),
                            new JwtClaimValidator<Object>("aud", aud -> audienceMatches(aud, clientId))));
                    decoder = d;
                }
            }
        }
        return d;
    }

    private static boolean audienceMatches(Object aud, String clientId) {
        if (aud instanceof Collection<?> c) return c.contains(clientId);
        return aud != null && aud.toString().equals(clientId);
    }
}
