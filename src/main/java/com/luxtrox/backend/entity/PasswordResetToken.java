package com.luxtrox.backend.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, unique = true)
    private UUID token;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_at")
    private OffsetDateTime usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    protected PasswordResetToken() {}

    public PasswordResetToken(User user) {
        this.user = user;
        this.token = UUID.randomUUID();
        this.expiresAt = OffsetDateTime.now().plusHours(24);
    }

    public UUID getId()                   { return id; }
    public User getUser()                  { return user; }
    public UUID getToken()                 { return token; }
    public OffsetDateTime getExpiresAt()   { return expiresAt; }
    public OffsetDateTime getUsedAt()      { return usedAt; }
    public boolean isExpired()             { return OffsetDateTime.now().isAfter(expiresAt); }
    public boolean isUsed()               { return usedAt != null; }
    public void markUsed()                 { this.usedAt = OffsetDateTime.now(); }
}
