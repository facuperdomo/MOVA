package com.movauy.mova.model.sale;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.movauy.mova.model.product.Product;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

/**
 * Representa un ítem dentro de una venta
 * Relación con Sale y Product
 * 
 * @author Facundo
 */
@Getter
@Setter
@Entity
@Table(name = "sale_item")
public class SaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sale_id", nullable = false)
    @JsonBackReference
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private int quantity;
    private double unitPrice;

    public double getSubtotal() {
        return unitPrice * quantity;
    }
}
