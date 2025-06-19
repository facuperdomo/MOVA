package com.movauy.mova.model.sale;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Entity
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<SaleItem> items;

    private double totalAmount;
    private String paymentMethod;
    private LocalDateTime dateTime;

    @ManyToOne
    @JoinColumn(name = "cash_register_id", nullable = false)
    private CashRegister cashRegister;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private EstadoVenta estado = EstadoVenta.ACTIVA;

    public enum EstadoVenta {
        ACTIVA, CANCELADA
    }

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    public enum OrderStatus {
        PENDING, SENT_TO_KITCHEN, PREPARING, READY, COMPLETED
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus kitchenStatus = OrderStatus.PENDING;

    @Column(name = "kitchen_sent_at")
    private LocalDateTime kitchenSentAt;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = true)
    private Account account;
}
