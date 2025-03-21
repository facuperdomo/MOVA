package com.movauy.mova.model.sale;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.movauy.mova.model.finance.CashRegister;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

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
    @JsonManagedReference // ✅ Controla la serialización de los items
    private List<SaleItem> items;

    // ✅ Campos que faltaban
    private double totalAmount;      // 💰 Total de la venta
    private String paymentMethod;    // 💳 Método de pago ("CASH" o "QR")
    private LocalDateTime dateTime;  // 🕒 Fecha y hora de la venta

    // ✅ Relación con la caja registradora
    @ManyToOne
    @JoinColumn(name = "cash_register_id", nullable = false)
    private CashRegister cashRegister;
}
