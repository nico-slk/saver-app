package com.saver.notification;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public final class NotificationPreferenceDtos {
    private NotificationPreferenceDtos() {}

    public record PreferenceItem(
            @NotNull @Pattern(regexp = "DUE_SOON|DUE_TODAY|OVERDUE") String kind,
            boolean enabled,
            boolean emailEnabled,
            boolean pushEnabled) {}

    public record UpdatePreferencesRequest(@NotEmpty List<@Valid PreferenceItem> items) {}

    public record PreferencesResponse(List<PreferenceItem> items) {}
}
