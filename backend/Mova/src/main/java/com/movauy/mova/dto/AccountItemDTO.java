package com.movauy.mova.dto;

import java.util.List;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountItemDTO {
    private Long id;
    private Long productId;
    private int quantity;
    private List<Long> ingredientIds;
}
