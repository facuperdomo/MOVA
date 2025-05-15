package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.OrderStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SaleDTO {
    private List<SaleItemDTO> items;
    private double totalAmount;
    private String paymentMethod;
    private String dateTime;
    private OrderStatus status;
    private LocalDateTime kitchenSentAt;
}
