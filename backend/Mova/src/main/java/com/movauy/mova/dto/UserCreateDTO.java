// src/main/java/com/movauy/mova/dto/UserCreateDTO.java
package com.movauy.mova.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserCreateDTO {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String role;

    @NotNull
    private Long branchId;
}
