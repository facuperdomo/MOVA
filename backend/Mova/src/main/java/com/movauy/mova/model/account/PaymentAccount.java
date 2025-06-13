package com.movauy.mova.model.account;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "payment_account")
public class PaymentAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;
    
    @Column(name = "payer_name", nullable = false)
    private String payerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    public enum Status {
        PARTIALLY_PAID,
        PAID_IN_FULL
    }

    // Constructor JPA
    public PaymentAccount() {}

    public PaymentAccount(Account account, BigDecimal amount, Status status) {
        this.account  = account;
        this.amount   = amount;
        this.paidAt   = LocalDateTime.now();
        this.status   = status;
    }

    @PrePersist
    public void prePersist() {
        if (paidAt == null) {
            paidAt = LocalDateTime.now();
        }
    }
}
