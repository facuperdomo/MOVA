// src/main/java/com/movauy/mova/dto/KitchenOrderDTO.java
package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KitchenOrderDTO {
    private Long accountId;
    private String accountName;
    private LocalDateTime dateTime;
    private OrderStatus kitchenStatus;
    private List<KitchenItemDTO> items;
    private boolean aggregated;

    // Compatibilidad hacia atrás: 5 parámetros => aggregated = false
    public KitchenOrderDTO(Long accountId,
                           String accountName,
                           LocalDateTime dateTime,
                           OrderStatus kitchenStatus,
                           List<KitchenItemDTO> items) {
        this(accountId, accountName, dateTime, kitchenStatus, items, false);
    }
}
