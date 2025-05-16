package com.movauy.mova.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleItemDTO {
    private Long productId;
    private String name;
    private int quantity;
    private double unitPrice;
    private List<Long> ingredientIds;
}
