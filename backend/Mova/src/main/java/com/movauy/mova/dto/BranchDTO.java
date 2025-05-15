package com.movauy.mova.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BranchDTO {
    private Long    id;
    private Long    companyId;
    private String  name;
    private String  username;
    private String  password;
    private String  mercadoPagoAccessToken;
    private boolean enableIngredients;
    private boolean enableKitchenCommands;
    private String  location;
    private String  phone;
    private boolean enabled;
}
