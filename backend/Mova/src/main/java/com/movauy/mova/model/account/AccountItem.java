package com.movauy.mova.model.account;

import com.movauy.mova.model.product.Product;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.movauy.mova.model.ingredient.Ingredient;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
@Table(name = "account_items")
public class AccountItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int quantity;

    private double unitPrice;

    @ManyToOne
    private Product product;

    @ManyToOne
    @JoinColumn(name = "account_id")
    @JsonBackReference
    private Account account;
    
    @Builder.Default
    @Column(nullable = false)
    private boolean paid = false;
    
    @ManyToMany
    @JoinTable(
            name = "account_item_ingredients",
            joinColumns = @JoinColumn(name = "account_item_id"),
            inverseJoinColumns = @JoinColumn(name = "ingredient_id")
    )
    private List<Ingredient> ingredients = new ArrayList<>();
}

