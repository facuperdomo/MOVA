package com.movauy.mova.controller.branch;

import com.movauy.mova.dto.BranchDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.company.Company;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.company.CompanyService;
import com.movauy.mova.service.print.PrinterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/companies/{companyId}/branches")
@RequiredArgsConstructor
public class BranchController {

    private final BranchService branchService;
    private final CompanyService companyService;
    private final PrinterService printerService;

    /**
     * Crea una nueva sucursal bajo la empresa {companyId}
     */
    @PostMapping
    public ResponseEntity<BranchDTO> createBranch(
            @PathVariable Long companyId,
            @Valid @RequestBody BranchDTO dto // ya no llevas companyId en el body
    ) {
        Company company = companyService.findById(companyId);
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
                .rut(dto.getRut())
                .build();

        Branch saved = branchService.registerBranch(branch);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(mapToDto(saved));
    }

    /**
     * Lista todas las sucursales de la empresa {companyId}
     */
    @GetMapping
    public ResponseEntity<List<BranchDTO>> getBranchesByCompany(
            @PathVariable Long companyId
    ) {
        List<BranchDTO> dtos = branchService.findByCompanyId(companyId)
                .stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Actualiza la sucursal {id} (no necesita el companyId en el body)
     */
    @PutMapping("/{id}")
    public ResponseEntity<BranchDTO> updateBranch(
            @PathVariable Long companyId, // opcional si lo necesitas para validar 
            @PathVariable Long id,
            @Valid @RequestBody BranchDTO dto
    ) {
        Branch updated = branchService.updateBranch(
                id,
                dto.getName(),
                dto.getUsername(),
                dto.getPassword(),
                dto.getMercadoPagoAccessToken(),
                dto.isEnableIngredients(),
                dto.isEnableKitchenCommands(),
                dto.getLocation(),
                dto.getPhone(),
                dto.getRut()
        );
        return ResponseEntity.ok(mapToDto(updated));
    }

    /**
     * Elimina la sucursal {id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBranch(
            @PathVariable Long companyId,
            @PathVariable Long id,
            @RequestParam(name = "force", defaultValue = "false") boolean force
    ) {
        try {
            branchService.deleteBranch(id, force);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            String code = ex.getMessage().contains("usuarios") ? "TieneUsuarios" : "TieneIngredientes";
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", code, "message", ex.getMessage()));
        }
    }

    /**
     * Conversión de entidad a DTO, incluyendo datos de Plan
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
                // campos de Plan:
                .planId(b.getPlan() != null ? b.getPlan().getId() : null)
                .planName(b.getPlan() != null ? b.getPlan().getName() : null)
                .maxCashBoxes(b.getPlan() != null ? b.getPlan().getMaxCashBoxes() : null)
                .maxUsers(b.getPlan() != null ? b.getPlan().getMaxUsers() : null)
                .build();
    }

    /**
     * Convierte un BranchDTO en la entidad Branch (sin id ni company seteados).
     */
    private Branch toEntity(BranchDTO dto) {
        // Carga la compañía asociada
        Company company = companyService.findById(dto.getCompanyId());
        return Branch.builder()
                .name(dto.getName())
                .username(dto.getUsername())
                .password(dto.getPassword())
                .mercadoPagoAccessToken(dto.getMercadoPagoAccessToken())
                .enableIngredients(dto.isEnableIngredients())
                .enableKitchenCommands(dto.isEnableKitchenCommands())
                .location(dto.getLocation())
                .phone(dto.getPhone())
                .rut(dto.getRut())
                .enabled(dto.isEnabled())
                .build();
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<BranchDTO> toggleEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        Branch b = branchService.setEnabled(id, enabled, force);
        return ResponseEntity.ok(mapToDto(b));
    }

    /**
     * —————— ASIGNAR PLAN A LA SUCURSAL ——————
     */
    @PutMapping("/{branchId}/plan/{planId}")
    public ResponseEntity<BranchDTO> assignPlanToBranch(
            @PathVariable Long companyId,
            @PathVariable Long branchId,
            @PathVariable Long planId
    ) {
        Branch updated = branchService.assignPlan(branchId, planId);
        return ResponseEntity.ok(mapToDto(updated));
    }
    
    /**
     * —————— DESASIGNAR PLAN DE LA SUCURSAL ——————
     */
    @DeleteMapping("/{branchId}/plan")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignPlanFromBranch(
            @PathVariable Long companyId,
            @PathVariable Long branchId
    ) {
        branchService.unassignPlan(branchId);
    }
}
