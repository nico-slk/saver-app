package com.saver.notification;

import com.saver.notification.NotificationPreferenceDtos.PreferenceItem;
import com.saver.notification.NotificationPreferenceDtos.UpdatePreferencesRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {
    private static final List<String> KINDS = List.of("DUE_SOON", "DUE_TODAY", "OVERDUE");
    private final NotificationPreferenceRepository preferences;

    public NotificationPreferenceService(NotificationPreferenceRepository preferences) {
        this.preferences = preferences;
    }

    @Transactional(readOnly = true)
    public List<PreferenceItem> get(UUID userId) {
        Map<String, NotificationPreference> saved = new LinkedHashMap<>();
        preferences.findAllByUserId(userId).forEach(p -> saved.put(p.getKind(), p));
        return KINDS.stream().map(kind -> {
            NotificationPreference p = saved.get(kind);
            return p == null ? new PreferenceItem(kind, false, false, false)
                    : new PreferenceItem(kind, p.isEnabled(), p.isEmailEnabled(), p.isPushEnabled());
        }).toList();
    }

    @Transactional
    public List<PreferenceItem> update(UUID userId, UpdatePreferencesRequest request) {
        Map<String, PreferenceItem> requested = new LinkedHashMap<>();
        for (PreferenceItem item : request.items()) requested.put(item.kind(), item);
        for (String kind : KINDS) {
            PreferenceItem item = requested.getOrDefault(kind, new PreferenceItem(kind, false, false, false));
            NotificationPreference current = preferences.findByUserIdAndKind(userId, kind).orElse(null);
            if (current == null) {
                preferences.save(new NotificationPreference(userId, kind, item.enabled(), item.emailEnabled(), item.pushEnabled()));
            } else {
                current.update(item.enabled(), item.emailEnabled(), item.pushEnabled());
            }
        }
        return get(userId);
    }
}
