package com.movauy.mova.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author Facundo
 */
@Getter
@Setter
public class SaleItemDTO {
    private Long productId;
    private int quantity;
    private double unitPrice;
    private List<Long> ingredientIds;
}
