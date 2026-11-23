package com.saver.notification;

import com.saver.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences", uniqueConstraints =
        @UniqueConstraint(name = "uq_notification_preferences_user_kind", columnNames = {"user_id", "kind"}))
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String kind;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    protected NotificationPreference() {}

    public NotificationPreference(UUID userId, String kind, boolean enabled, boolean emailEnabled, boolean pushEnabled) {
        this.userId = userId;
        this.kind = kind;
        this.enabled = enabled;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
    }

    public UUID getUserId() { return userId; }
    public String getKind() { return kind; }
    public boolean isEnabled() { return enabled; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public boolean isPushEnabled() { return pushEnabled; }
    public void update(boolean enabled, boolean emailEnabled, boolean pushEnabled) {
        this.enabled = enabled;
        this.emailEnabled = emailEnabled;
        this.pushEnabled = pushEnabled;
    }
}
