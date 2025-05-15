package com.movauy.mova.dto;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyResponseDTO {
    private Long id;
    private String name;
    private String contactEmail;
    private String contactPhone;
    private String logoUrl;
    private boolean enabled;
}
