package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.EstadoVenta;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
public class SaleResponseDTO {
    private Long id;
    private double totalAmount;
    private String paymentMethod;
    private LocalDateTime dateTime;

    // tu estado de venta original
    private EstadoVenta estado;

    // nuevo estado en cocina
    private OrderStatus kitchenStatus;
    private LocalDateTime kitchenSentAt;

    private List<SaleItemResponseDTO> items;
}
