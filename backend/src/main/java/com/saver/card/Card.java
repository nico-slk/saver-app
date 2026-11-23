package com.saver.card;

import com.saver.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Tarjeta de crédito: solo agrupa gastos (sin resumen ni cierre). Se archiva en lugar de borrarse. */
@Entity
@Table(name = "cards")
public class Card extends BaseEntity {

    @Column(name = "user_id", nullable = false) private UUID userId;
    @Column(nullable = false, length = 60) private String name;
    @Column(nullable = false, length = 7) private String color;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected Card() {}

    public Card(UUID userId, String name, String color, Instant now) {
        this.userId = userId;
        this.name = name;
        this.color = color;
        this.createdAt = now;
    }

    public UUID getUserId() { return userId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public boolean isArchived() { return archivedAt != null; }
    public void setArchivedAt(Instant archivedAt) { this.archivedAt = archivedAt; }
}
