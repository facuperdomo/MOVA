package com.movauy.mova.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CompanyDTO {
    @NotBlank(message = "El nombre es obligatorio")
    private String name;
    
    @Email(message = "Email inválido")
    private String contactEmail;
    
    private String contactPhone;
    private String logoUrl;
    
    private boolean enabled;
}
