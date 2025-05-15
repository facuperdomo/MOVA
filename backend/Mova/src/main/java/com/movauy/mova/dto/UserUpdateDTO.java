package com.movauy.mova.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UserUpdateDTO {
    @NotBlank
    private String username;

    // Password ya NO es @NotBlank: opcional en update
    private String password;

    @NotBlank
    private String role;

    @NotNull
    private Long branchId;
}