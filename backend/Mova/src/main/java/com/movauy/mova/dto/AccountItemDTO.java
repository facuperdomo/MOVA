package com.movauy.mova.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountItemDTO {
    private Long id;
    private Long productId;
    private int quantity;
}
