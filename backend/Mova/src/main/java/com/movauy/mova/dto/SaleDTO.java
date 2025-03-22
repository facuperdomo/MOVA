package com.movauy.mova.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/**
 *
 * @author Facundo
 */
@Getter
@Setter
public class SaleDTO {
    private List<SaleItemDTO> items;
    private double totalAmount;
    private String paymentMethod;
    private String dateTime;
}
