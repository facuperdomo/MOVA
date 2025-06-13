// src/main/java/com/movauy/mova/dto/ItemPaymentInfoDTO.java
package com.movauy.mova.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ItemPaymentInfoDTO {
    private Long itemId;       // ID del AccountItem
    private int quantity;      // cantidad total original de ese item
    private int paidQuantity;  // cuántas unidades ya fueron pagadas de ese item
}
