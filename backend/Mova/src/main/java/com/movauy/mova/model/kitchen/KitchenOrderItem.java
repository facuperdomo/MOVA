// src/main/java/com/movauy/mova/model/kitchen/KitchenOrderItem.java
package com.movauy.mova.model.kitchen;

import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.model.product.Product;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter
@Table(name = "kitchen_order_item")
public class KitchenOrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) private KitchenOrder order;
    @ManyToOne(fetch = FetchType.LAZY) private Product product;

    private Integer quantity;

    @ElementCollection
    @CollectionTable(
        name = "kitchen_order_item_ingredient",
        joinColumns = @JoinColumn(name = "kitchen_order_item_id")
    )
    @Column(name = "ingredient_id")
    private List<Long> ingredientIds = new ArrayList<>();
}
