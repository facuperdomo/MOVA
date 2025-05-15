package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.OrderStatus;
import lombok.Data;

@Data
public class UpdateKitchenStatusDTO {
    private OrderStatus kitchenStatus;
}
