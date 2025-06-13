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
    private List<SaleItemDTO> items;

    /**
     * Ahora es BigDecimal para reflejar exactamente el cálculo de precios
     * (evita el error de intentar asignar un BigDecimal a un double).
     */
    private BigDecimal totalAmount;

    /**
     * Si quieres almacenar el método de pago:
     * (puedes dejarlo null o eliminarlo si no lo usas)
     */
    private String paymentMethod;

    /**
     * Cambiamos de String a LocalDateTime, para poder hacer:
     *     dto.setDateTime(sale.getDateTime());
     */
    private LocalDateTime dateTime;

    private OrderStatus status;

    /**
     * Si tu entidad `Sale` incluye cuándo se envió a cocina:
     */
    private LocalDateTime kitchenSentAt;

    /**
     * Datos extra (opcionales) que podrías querer devolver:
     * - A qué sucursal pertenece la venta
     * - En qué caja se registró
     * - Qué usuario la generó
     */
    private Long branchId;
    private Long cashRegisterId;
    private Long userId;
}
