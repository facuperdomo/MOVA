package com.movauy.mova.model.product;

import com.movauy.mova.model.user.User;
import com.movauy.mova.model.ingredient.Ingredient;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "products",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "name"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Ya no es único globalmente; se hace único por empresa (user_id + name)
     */
    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private double price;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] image;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"mercadoPagoAccessToken", "password", "authorities"})
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;

    @Builder.Default
    @Column(name = "enable_ingredients", nullable = false)
    private boolean enableIngredients = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "product_ingredients",
            joinColumns = @JoinColumn(name = "product_id"),
            inverseJoinColumns = @JoinColumn(name = "ingredient_id")
    )
    @Builder.Default
    private Set<Ingredient> ingredients = new HashSet<>();

    @Column(name = "enable_kitchen_commands", nullable = false)
    private boolean enableKitchenCommands = false;
}
