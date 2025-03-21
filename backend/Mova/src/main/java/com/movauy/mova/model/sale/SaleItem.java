package com.movauy.mova.model.sale;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.movauy.mova.model.product.Product;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

/**
 *
 * @author Facundo
 */
@Getter
@Setter
@Entity
public class SaleItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "sale_id")
    @JsonBackReference // ✅ Evita la referencia circular
    private Sale sale;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private int quantity;
    private double unitPrice;
}
