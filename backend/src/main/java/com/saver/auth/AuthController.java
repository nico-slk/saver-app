package com.saver.auth;

import com.saver.auth.AuthDtos.AuthResponse;
import com.saver.auth.AuthDtos.ForgotPasswordRequest;
import com.saver.auth.AuthDtos.ResetPasswordRequest;
import com.saver.auth.AuthDtos.GoogleLoginRequest;
import com.saver.auth.AuthDtos.LoginRequest;
import com.saver.auth.AuthDtos.ProvidersResponse;
import com.saver.auth.AuthDtos.RegisterRequest;
import com.saver.auth.AuthService.AuthResult;
import com.saver.config.AppProperties;
import com.saver.user.UserDtos.UserResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Firebase Hosting solo reenvía a Cloud Run la cookie llamada "__session". */
    static final String REFRESH_COOKIE = "__session";
    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService auth;
    private final PasswordResetService passwordReset;
    private final GoogleTokenVerifier google;
    private final AppProperties.Cookie cookieProps;
    private final TokenService tokens;

    public AuthController(AuthService auth, PasswordResetService passwordReset, GoogleTokenVerifier google,
                          AppProperties props, TokenService tokens) {
        this.auth = auth;
        this.passwordReset = passwordReset;
        this.google = google;
        this.cookieProps = props.cookie();
        this.tokens = tokens;
    }

    /** Qué métodos de login están habilitados (el front muestra u oculta el botón de Google). */
    @GetMapping("/providers")
    public ProvidersResponse providers() {
        return new ProvidersResponse(true, google.clientId());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest req,
                                 @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String ua,
                                 HttpServletResponse response) {
        return respond(auth.register(req, ua), response);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req,
                              @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String ua,
                              HttpServletResponse response) {
        return respond(auth.login(req, ua), response);
    }

    @PostMapping("/google")
    public AuthResponse google(@Valid @RequestBody GoogleLoginRequest req,
                               @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String ua,
                               HttpServletResponse response) {
        return respond(auth.loginWithGoogle(req, ua), response);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                                @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String ua,
                                HttpServletResponse response) {
        try {
            return respond(auth.refresh(refreshToken, ua), response);
        } catch (RuntimeException e) {
            clearCookie(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken,
                       HttpServletResponse response) {
        auth.logout(refreshToken);
        clearCookie(response);
    }

    /** Siempre 204, exista o no la cuenta. Si existe, se envía un email con el link. */
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        passwordReset.requestReset(req.email());
    }

    /** Fija la nueva contraseña y cierra todas las sesiones abiertas. */
    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        passwordReset.resetPassword(req.token(), req.password());
    }

    private AuthResponse respond(AuthResult result, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(), tokens.refreshTtl()).toString());
        return new AuthResponse(result.accessToken(), result.expiresIn(), UserResponse.from(result.user()));
    }

    private void clearCookie(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieProps.secure())
                .sameSite(cookieProps.sameSite())
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
