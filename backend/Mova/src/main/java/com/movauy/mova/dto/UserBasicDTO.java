package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserBasicDTO {
    private Long id;
    private String username;
    private Long branchId;
    private Long companyId;
    private String role;
    private boolean enableIngredients;
    private boolean enableKitchenCommands;
    private Long assignedBoxId;
    
    public UserBasicDTO(Long id, String username) {
        this.id = id;
        this.username = username;
    }
    public UserBasicDTO(Long id, String username, String role) {
        this.id       = id;
        this.username = username;
        this.role     = role;
    }
    public UserBasicDTO(Long id, String username, String role, Long assignedBoxId) {
        this.id       = id;
        this.username = username;
        this.role     = role;
        this.assignedBoxId = assignedBoxId;
    }
}
