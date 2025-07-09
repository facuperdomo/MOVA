package com.movauy.mova.dto;

import java.math.BigDecimal;
import lombok.*;

@Getter
@Setter
public class UnitItemDTO {
    Long itemId; 
    Long productId; 
    String productName;
    BigDecimal unitPrice; 
    boolean paid;
    
    public UnitItemDTO(Long itemId,
                       Long productId,
                       String productName,
                       BigDecimal unitPrice,
                       boolean paid) {
        this.itemId = itemId;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.paid = paid;
    }
}
