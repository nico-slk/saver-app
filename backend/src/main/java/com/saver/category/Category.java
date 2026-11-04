package com.saver.category;

import com.saver.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Categoría de gasto. Las de sistema (userId null) tienen {@code systemKey} y el nombre lo traduce
 * el frontend. Las del usuario se borran de forma lógica para conservar el historial.
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {


    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "system_key", length = 30)
    private String systemKey;

    @Column(length = 60)
    private String name;

    @Column(nullable = false, length = 7)
    private String color;

    @Column(length = 30)
    private String icon;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Category() {}

    public Category(UUID userId, String name, String color, String icon, Instant now) {
        this.userId = userId;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.createdAt = now;
    }

    public boolean isSystem() { return userId == null; }
    public boolean isDeleted() { return deletedAt != null; }

    public UUID getUserId() { return userId; }
    public String getSystemKey() { return systemKey; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public Instant getDeletedAt() { return deletedAt; }
    public void markDeleted(Instant now) { this.deletedAt = now; }
}
