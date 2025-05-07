package com.movauy.mova.model.product;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false)
    private String companyId; // Vincula la categoría con la empresa
    
    @Column(nullable = false)
    boolean hasIngredients;
    
    @Column(name = "enable_kitchen_commands", nullable = false)
    private boolean enableKitchenCommands = false;
}
