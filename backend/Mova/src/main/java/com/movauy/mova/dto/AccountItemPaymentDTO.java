// src/main/java/com/movauy/mova/dto/AccountItemPaymentDTO.java
package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountItemPaymentDTO {
    private Long itemId;         // el id de AccountItem
    private Long productId;      // id del producto
    private String productName;  // nombre
    private Integer quantity;    // cantidad total pedida
    private BigDecimal unitPrice;// precio unitario
    private Integer paidQty;     // cuántas unidades ya están pagadas
    private Boolean paid;        // true si (paidQty >= quantity)
}
