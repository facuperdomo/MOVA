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
    private String paymentMethod; // "Efectivo" o "QR"
    private String dateTime; // Formato ISO 8601 (ej. "2024-02-18T15:30:00")
}
