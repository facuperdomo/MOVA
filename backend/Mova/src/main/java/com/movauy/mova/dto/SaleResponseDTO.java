package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.EstadoVenta;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleResponseDTO {
    private Long id;
    private Long accountId;
    private double totalAmount;
    private String paymentMethod;
    private LocalDateTime dateTime;

    private EstadoVenta estado;
    private OrderStatus kitchenStatus;
    private LocalDateTime kitchenSentAt;

    private List<SaleItemResponseDTO> items;
}
