package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SaleResponseDTO {
    private Long id;
    private double totalAmount;
    private String paymentMethod;
    private LocalDateTime dateTime;
    private String estado;
}
