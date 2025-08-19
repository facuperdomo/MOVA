package com.movauy.mova.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter @Setter
public class SaleItemResponseDTO {
    private Long productId;
    private String name; 
    private int quantity;
    private double unitPrice;
    private List<Long> ingredientIds;
    private Long categoryId;
    
    public SaleItemResponseDTO(Long productId,
                               String name,
                               Integer quantity,
                               Double unitPrice,
                               List<Long> ingredientIds
                              , Long categoryId
    ) {
        this.productId = productId;
        this.name = name;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.ingredientIds = ingredientIds;
       this.categoryId = categoryId;
    }
}
