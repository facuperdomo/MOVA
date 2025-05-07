package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.OrderStatus;
import lombok.Data;

/**
 *
 * @author facue
 */
@Data
public class UpdateKitchenStatusDTO {
    private OrderStatus kitchenStatus;
}
