package com.luxtrox.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "system_config")
public class SystemConfig {

    @Id
    @Column(name = "key")
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    @Column(name = "description")
    private String description;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SystemConfig() {}

    public SystemConfig(String key, String value, String description) {
        this.key = key;
        this.value = value;
        this.description = description;
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    @PrePersist
    void touch() { this.updatedAt = OffsetDateTime.now(); }

    public String getKey()         { return key; }
    public String getValue()       { return value; }
    public String getDescription() { return description; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setValue(String value)   { this.value = value; }
}