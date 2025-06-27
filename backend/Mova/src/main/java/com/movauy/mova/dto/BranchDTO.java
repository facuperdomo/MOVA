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
    private String  rut;
    private boolean enabled;

    // === Campos de Plan ===
    private Long    planId;            // id del plan asociado
    private String  planName;          // nombre del plan
    private Integer maxCashBoxes;      // tope de cajas del plan
    private Integer maxUsers;          // tope de usuarios del plan
}
