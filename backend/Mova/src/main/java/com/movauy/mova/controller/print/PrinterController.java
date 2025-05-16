package com.movauy.mova.controller.print;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.service.print.PrinterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/branches/{branchId}/printers")
@RequiredArgsConstructor
public class PrinterController {

    private final PrinterService printerService;
    private final BranchRepository branchRepo;

    /**
     * GET /api/branches/{branchId}/printers
     */
    @GetMapping
    public List<Printer> list(@PathVariable Long branchId) {
        return printerService.findByBranch(branchId);
    }

    /**
     * POST /api/branches/{branchId}/printers
     */
    @PostMapping
    public Printer create(@PathVariable Long branchId,
                          @RequestBody Printer p) {
        Branch branch = branchRepo.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Branch not found"));
        p.setBranch(branch);
        return printerService.create(p);
    }

    /**
     * PUT /api/branches/{branchId}/printers/{id}
     */
    @PutMapping("/{id}")
    public Printer update(@PathVariable Long branchId,
                          @PathVariable Long id,
                          @RequestBody Printer p) {
        Branch branch = branchRepo.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Branch not found"));
        p.setId(id);
        p.setBranch(branch);
        return printerService.update(p);
    }

    /**
     * DELETE /api/branches/{branchId}/printers/{id}
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long branchId,
                       @PathVariable Long id) {
        // opcional: validar branchId existe o pertenece
        printerService.delete(id);
    }
}
