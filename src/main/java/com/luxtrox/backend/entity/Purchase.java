package com.luxtrox.backend.entity;

import com.luxtrox.backend.entity.enums.PaymentMethod;
import com.luxtrox.backend.entity.enums.PlanType;
import com.luxtrox.backend.entity.enums.PurchaseStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una compra DRIVER siempre genera UNA sola posicion sin importar
 * cuantos paquetes contenga (ver docs/domain-model.md 2.3). Una
 * compra ZENITH NUNCA genera posicion -- genera una ZenithLicense en
 * su lugar (ver docs/domain-model.md 7.1, adenda de Fase 6).
 *
 * La relacion con InvestmentPosition es 1:1 con columnas FK fisicas en
 * AMBOS lados (purchases.position_id y investment_positions.purchase_id)
 * -- asi se diseno deliberadamente en las migraciones de Fase 3, por
 * eso aqui son dos asociaciones @OneToOne independientes, no una sola
 * con mappedBy.
 */
@Entity
@Table(name = "purchases")
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 10)
    private PlanType planType;

    @Column(name = "package_quantity", nullable = false)
    private Integer packageQuantity;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseStatus status = PurchaseStatus.PENDING;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private InvestmentPosition position;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    protected Purchase() {
        // JPA
    }

    public Purchase(User user, PlanType planType, Integer packageQuantity,
                     BigDecimal totalAmount, PaymentMethod paymentMethod) {
        this.user = user;
        this.planType = planType;
        this.packageQuantity = packageQuantity;
        this.totalAmount = totalAmount;
        this.paymentMethod = paymentMethod;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public PlanType getPlanType() {
        return planType;
    }

    public Integer getPackageQuantity() {
        return packageQuantity;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PurchaseStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseStatus status) {
        this.status = status;
    }

    public InvestmentPosition getPosition() {
        return position;
    }

    public void setPosition(InvestmentPosition position) {
        this.position = position;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
