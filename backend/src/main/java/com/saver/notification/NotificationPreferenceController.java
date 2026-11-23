package com.saver.notification;

import com.saver.notification.NotificationPreferenceDtos.PreferencesResponse;
import com.saver.notification.NotificationPreferenceDtos.UpdatePreferencesRequest;
import com.saver.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications/preferences")
public class NotificationPreferenceController {
    private final NotificationPreferenceService service;

    public NotificationPreferenceController(NotificationPreferenceService service) {
        this.service = service;
    }

    @GetMapping
    public PreferencesResponse get(@CurrentUser UUID userId) {
        return new PreferencesResponse(service.get(userId));
    }

    @PutMapping
    public PreferencesResponse update(@CurrentUser UUID userId,
                                      @Valid @RequestBody UpdatePreferencesRequest request) {
        return new PreferencesResponse(service.update(userId, request));
    }
}
