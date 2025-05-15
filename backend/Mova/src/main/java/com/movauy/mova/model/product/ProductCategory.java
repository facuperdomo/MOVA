package com.movauy.mova.model.product;

import com.movauy.mova.model.branch.Branch;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
    name = "product_category",
    uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "name"})
)
@Getter
@Setter
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false)
    private boolean hasIngredients;

    @Column(name = "enable_kitchen_commands", nullable = false)
    private boolean enableKitchenCommands = false;
}
