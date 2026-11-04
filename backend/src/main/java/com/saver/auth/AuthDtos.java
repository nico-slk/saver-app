package com.saver.auth;

import com.saver.user.UserDtos.UserResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "validation.required") @Email(message = "validation.email") @Size(max = 320) String email,
            @NotBlank(message = "validation.required") @Size(min = 8, max = 72, message = "auth.password_length") String password,
            @NotBlank(message = "validation.required") @Size(max = 120) String displayName,
            @Pattern(regexp = "es|en") String locale) {}

    public record LoginRequest(
            @NotBlank(message = "validation.required") String email,
            @NotBlank(message = "validation.required") @Size(max = 200) String password) {}

    public record GoogleLoginRequest(
            @NotBlank(message = "validation.required") String idToken,
            @Pattern(regexp = "es|en") String locale) {}

    /** El refresh token viaja solo en cookie HttpOnly, nunca en el body. */
    public record AuthResponse(String accessToken, long expiresIn, UserResponse user) {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "validation.required") @Email(message = "validation.email") @Size(max = 320) String email) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "validation.required") @Size(max = 200) String token,
            @NotBlank(message = "validation.required") @Size(min = 8, max = 72, message = "auth.password_length") String password) {}

    public record ProvidersResponse(boolean password, String googleClientId) {}
}
