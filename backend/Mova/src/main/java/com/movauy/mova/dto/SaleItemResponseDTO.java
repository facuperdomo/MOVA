package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class SaleItemResponseDTO {
    private Long productId;
    private int quantity;
    private double unitPrice;
    private List<Long> ingredientIds;
}