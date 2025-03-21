package com.movauy.mova.dto;

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
}
