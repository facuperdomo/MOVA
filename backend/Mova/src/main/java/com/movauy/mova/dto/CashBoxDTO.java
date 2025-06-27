// src/main/java/com/movauy/mova/dto/CashBoxDTO.java
package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CashBoxDTO {
    private Long id;
    private String code;
    private String name;
    private Boolean isOpen;
    private Boolean enabled;
}
