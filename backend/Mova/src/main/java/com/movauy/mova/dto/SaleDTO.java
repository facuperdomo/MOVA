package com.movauy.mova.dto;

import com.movauy.mova.model.sale.Sale.OrderStatus;
import java.time.LocalDateTime;
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
    private String paymentMethod;
    private String dateTime;
    
    private OrderStatus status;
    private LocalDateTime kitchenSentAt;
}
