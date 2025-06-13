package com.movauy.mova.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class AccountCreateDTO {
    private Long branchId;
    private String name;
}
