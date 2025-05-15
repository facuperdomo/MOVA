package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItemResponseDTO {
    private Long productId;
    private int quantity;
    private double unitPrice;
    private List<Long> ingredientIds;
}
