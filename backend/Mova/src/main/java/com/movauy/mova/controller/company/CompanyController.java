package com.movauy.mova.controller.company;

import com.movauy.mova.dto.CompanyDTO;
import com.movauy.mova.dto.CompanyResponseDTO;
import com.movauy.mova.model.company.Company;
import com.movauy.mova.service.company.CompanyService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService svc;

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<CompanyResponseDTO> create(@RequestBody CompanyDTO dto) {
        Company c = svc.createCompany(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toResponse(c));
    }

    @GetMapping
    public ResponseEntity<?> listAll() {
        var list = svc.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CompanyResponseDTO> getOne(@PathVariable Long id) {
        return ResponseEntity.ok(toResponse(svc.findById(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CompanyResponseDTO> update(@PathVariable Long id,
            @RequestBody CompanyDTO dto) {
        return ResponseEntity.ok(toResponse(svc.updateCompany(id, dto)));
    }

    /**
     * Borra una empresa. Si tiene sucursales asociadas y force=false arroja 409
     * con { error, message }. Con force=true, las elimina todas antes de borrar
     * la empresa.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(
            @PathVariable Long id,
            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force
    ) {
        try {
            svc.deleteCompany(id, force);
            return ResponseEntity.noContent().build();
        } catch (IllegalStateException ex) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "TieneSucursales",
                            "message", ex.getMessage()
                    ));
        }
    }

    private CompanyResponseDTO toResponse(Company c) {
        return CompanyResponseDTO.builder()
                .id(c.getId())
                .name(c.getName())
                .contactEmail(c.getContactEmail())
                .contactPhone(c.getContactPhone())
                .logoUrl(c.getLogoUrl())
                .enabled(c.isEnabled())
                .build();
    }

    @PutMapping("/{id}/enabled")
    public ResponseEntity<?> toggleEnabled(
            @PathVariable Long id,
            @RequestParam boolean enabled,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        try {
            Company c = svc.setEnabled(id, enabled, force);
            return ResponseEntity.ok(toResponse(c));
        } catch (IllegalStateException ex) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "TieneSucursales",
                            "message", ex.getMessage()
                    ));
        }
    }
}
