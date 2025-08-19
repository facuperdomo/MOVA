// src/main/java/com/movauy/mova/dto/KitchenItemDTO.java
package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class KitchenItemDTO {
    private Long productId;
    private String productName;
    private Integer quantity;
    private List<Long> ingredientIds;
}
