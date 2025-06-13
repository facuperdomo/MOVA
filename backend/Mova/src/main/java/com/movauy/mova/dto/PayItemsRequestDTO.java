// src/main/java/com/movauy/mova/dto/PayItemsRequestDTO.java
package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayItemsRequestDTO {
    private List<Long> itemIds;   // IDs de AccountItem que queremos marcar como pagados
    private String payerName;      // nombre del pagador
}
