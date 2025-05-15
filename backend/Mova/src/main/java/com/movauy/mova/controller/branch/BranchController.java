package com.movauy.mova.controller.branch;

import com.movauy.mova.dto.BranchDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.company.Company;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.company.CompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final CompanyService companyService;

    /**
     * Crea una nueva sucursal.
     */
    @PostMapping
    public ResponseEntity<BranchDTO> createBranch(@RequestBody BranchDTO dto) {
        Company company = companyService.findById(dto.getCompanyId());
        Branch branch = Branch.builder()
                .company(company)
                .name(dto.getName())
                .username(dto.getUsername())
                .password(dto.getPassword())
                .mercadoPagoAccessToken(dto.getMercadoPagoAccessToken())
                .enableIngredients(dto.isEnableIngredients())
                .enableKitchenCommands(dto.isEnableKitchenCommands())
                .location(dto.getLocation())
                .phone(dto.getPhone())
                .build();
        Branch saved = branchService.registerBranch(branch);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToDto(saved));
    }

    /**
     * Obtiene todas las sucursales de una empresa.
     */
    @GetMapping
    public ResponseEntity<List<BranchDTO>> getBranchesByCompany(@RequestParam Long companyId) {
        List<BranchDTO> dtos = branchService.findByCompanyId(companyId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Actualiza una sucursal existente.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Branch> updateBranch(
            @PathVariable Long id,
            @RequestBody Branch updatedBranch
    ) {
        Branch result = branchService.updateBranch(id, updatedBranch);
        return ResponseEntity.ok(result);
    }

    /**
     * DELETE /api/branches/{id}?force={true|false}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBranch(
            @PathVariable Long id,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        try {
            branchService.deleteBranch(id, force);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            // devolvemos código 409 + payload con clave "error" y "message"
            String code = ex.getMessage().contains("usuarios") ? "TieneUsuarios" : "TieneIngredientes";
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", code,
                            "message", ex.getMessage()
                    ));
        }
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
                .password(b.getPassword())
                .mercadoPagoAccessToken(b.getMercadoPagoAccessToken())
                .enableIngredients(b.isEnableIngredients())
                .enableKitchenCommands(b.isEnableKitchenCommands())
                .location(b.getLocation())
                .phone(b.getPhone())
                .enabled(b.isEnabled())
                .build();
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<Branch> toggleEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        Branch b = branchService.setEnabled(id, enabled, force);
        return ResponseEntity.ok(b);
    }
}
