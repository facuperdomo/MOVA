// src/main/java/com/movauy/mova/dto/SaleDTO.java
package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleDTO {
    private Long id;

    /** Línea de detalle de productos vendidos */
    private List<SaleItemDTO> items;

    /** Monto total de la venta */
    private BigDecimal totalAmount;

    /** Método de pago (opcional) */
    private String paymentMethod;

    /** Fecha/hora de la venta (se llena en el servicio) */
    private LocalDateTime dateTime;

    /** Estado de la orden (opcional para crear, útil en respuestas) */
    private OrderStatus status;

    /** Fecha/hora de envío a cocina (para respuesta) */
    private LocalDateTime kitchenSentAt;

    /** Sucursal donde se realiza la venta */
    private Long branchId;

    /** Caja en la que se registra la venta (antes cashRegisterId) */
    private Long cashBoxId;

    /** Usuario que genera la venta */
    private Long userId;
}
