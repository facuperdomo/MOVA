package com.movauy.mova.model.plan;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nombre del plan (p.ej. "Básico", "Premium")
    private String name;

    // Máximo de cajas por sucursal
    private Integer maxCashBoxes;

    // Precio mensual
    private BigDecimal price;

    // Descripción del plan
    @Column(length = 1000)
    private String description;

    // Ciclo de facturación: MONTHLY, YEARLY, etc.
    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;

    // Divisa de facturación (USD, UYU, etc.)
    private String currency;

    // Días de prueba gratuitos
    private Integer trialDays;

    // Estado del plan: ACTIVE, INACTIVE, EXPIRED
    @Enumerated(EnumType.STRING)
    private PlanStatus status;

    // Límite de usuarios por sucursal
    private Integer maxUsers;

    // Límite de impresoras por sucursal
    private Integer maxPrinters;

    private Boolean kitchenPrinting;     // ¿incluye impresión en cocina?
    private Integer maxKitchenPrinters;  // tope de impresoras de cocina (si aplica)
    
    // Características adicionales del plan
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "plan_features", joinColumns = @JoinColumn(name = "plan_id"))
    @Column(name = "feature")
    private Set<String> features;

    // Timestamps de auditoría
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    // Enumeraciones internas o externas
    public enum BillingCycle {
        MONTHLY,
        YEARLY
    }

    public enum PlanStatus {
        ACTIVE,
        INACTIVE,
        EXPIRED
    }
}
