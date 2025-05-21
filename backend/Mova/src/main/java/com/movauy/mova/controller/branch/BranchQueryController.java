package com.movauy.mova.controller.branch;

import com.movauy.mova.dto.BranchDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.service.branch.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchQueryController {
    private final BranchService branchService;

    @GetMapping("/{id}")
    public ResponseEntity<BranchDTO> getBranchById(@PathVariable Long id) {
        Branch b = branchService.findById(id);
        return ResponseEntity.ok(mapToDto(b));
    }

    /**
     * Utilidad para convertir entidad a DTO.
     */
    private BranchDTO mapToDto(Branch b) {
        return BranchDTO.builder()
                .id(b.getId())
                .companyId(b.getCompany().getId())
                .name(b.getName())
                .username(b.getUsername())
                .mercadoPagoAccessToken(b.getMercadoPagoAccessToken())
                .enableIngredients(b.isEnableIngredients())
                .enableKitchenCommands(b.isEnableKitchenCommands())
                .location(b.getLocation())
                .phone(b.getPhone())
                .rut(b.getRut())
                .enabled(b.isEnabled())
                .build();
    }
}

