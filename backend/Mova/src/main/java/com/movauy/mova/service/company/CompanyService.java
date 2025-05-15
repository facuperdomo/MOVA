package com.movauy.mova.service.company;

import com.movauy.mova.dto.CompanyDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.company.Company;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.company.CompanyRepository;
import com.movauy.mova.service.branch.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepo;
    private final BranchRepository branchRepository;
    private final BranchService branchService;

    public Company createCompany(CompanyDTO dto) {
        // 1) Guardar la compañía
        Company c = Company.builder()
                .name(dto.getName())
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .logoUrl(dto.getLogoUrl())
                .enabled(dto.isEnabled())
                .build();
        return companyRepo.save(c);
    }

    public List<Company> findAll() {
        return companyRepo.findAll();
    }

    public Company findById(Long id) {
        return companyRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Empresa no encontrada"));
    }

    public Company updateCompany(Long id, CompanyDTO dto) {
        Company c = findById(id);
        c.setName(dto.getName());
        c.setContactEmail(dto.getContactEmail());
        c.setContactPhone(dto.getContactPhone());
        c.setLogoUrl(dto.getLogoUrl());
        c.setEnabled(dto.isEnabled());
        return companyRepo.save(c);
    }

    /**
     * Borra una empresa.
     *
     * @param id ID de la empresa
     * @param force si es true, primero borra todas las sucursales (y sus
     * usuarios/ingredientes) antes de eliminar la empresa. Si es false y hay
     * sucursales, lanza IllegalStateException.
     */
    @Transactional
    public void deleteCompany(Long companyId, boolean force) {
        // 1) obtengo todas las sucursales
        List<Branch> branches = branchService.findByCompanyId(companyId);

        // 2) elimino cada sucursal forzando si hace falta
        for (Branch b : branches) {
            branchService.deleteBranch(b.getId(), force);
        }

        // 3) ya puedo borrar la compañía
        companyRepo.deleteById(companyId);
    }

    @Transactional
    public Company setEnabled(Long companyId, boolean enabled, boolean force) {
        // 1) Si estamos deshabilitando CON force, antes deshabilitamos todas las sucursales
        if (!enabled && force) {
            List<Branch> branches = branchService.findByCompanyId(companyId);
            for (Branch b : branches) {
                // deshabilita cada sucursal sin volver a lanzar excepción
                branchService.setEnabled(b.getId(), false, true);
            }
        } // 2) Si deshabilitamos SIN force, validamos que no haya sucursales
        else if (!enabled && !force) {
            long count = branchService.countByCompanyId(companyId);
            if (count > 0) {
                throw new IllegalStateException(
                        "La empresa tiene " + count + " sucursales asociadas."
                );
            }
        }
        // 3) Actualiza el flag enabled de la empresa
        Company c = findById(companyId);
        c.setEnabled(enabled);
        return companyRepo.save(c);
    }
}
