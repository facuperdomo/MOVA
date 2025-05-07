package com.movauy.mova.model.sale;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.user.User;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 * @author Facundo
 */
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
    @JoinColumn(name = "user_id", nullable = false)  // Relaciona la venta con un usuario
    @JsonIgnore
    private User user;  // Usuario dueño de la venta
    
    public enum OrderStatus { 
        PENDING, SENT_TO_KITCHEN, PREPARING, READY, COMPLETED 
    }
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus kitchenStatus = OrderStatus.PENDING;

    @Column(name = "kitchen_sent_at")
    private LocalDateTime kitchenSentAt;
}
