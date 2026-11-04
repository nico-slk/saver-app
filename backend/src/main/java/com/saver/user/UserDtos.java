package com.saver.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {}

    public record UserResponse(
            UUID id,
            String email,
            String displayName,
            String locale,
            String baseCurrency,
            String timezone,
            boolean hasPassword,
            boolean googleLinked) {

        public static UserResponse from(User u) {
            return new UserResponse(
                    u.getId(), u.getEmail(), u.getDisplayName(), u.getLocale(),
                    u.getBaseCurrency(), u.getTimezone(), u.hasPassword(), u.getGoogleSub() != null);
        }
    }

    /** Todos los campos son opcionales: solo se actualiza lo que viene. */
    public record UpdateUserRequest(
            @Size(min = 1, max = 120) String displayName,
            @Pattern(regexp = "es|en", message = "user.locale.unsupported") String locale,
            @Pattern(regexp = "ARS|USD", message = "user.currency.unsupported") String baseCurrency,
            @Size(max = 64) String timezone) {}
}
