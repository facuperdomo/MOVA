package com.movauy.mova.controller.print;

import com.movauy.mova.dto.CreatePrinterRequestDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.device.Device;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.repository.device.DeviceRepository;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.print.PrinterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/branches/{branchId}/printers")
@RequiredArgsConstructor
public class BranchPrinterController {

    private final PrinterService printerService;
    private final BranchService branchService;
    private final DeviceRepository deviceRepo;

    /**
     * Lista todas las impresoras de una sucursal
     */
    @GetMapping
    public List<Printer> listByBranch(@PathVariable Long branchId) {
        branchService.findById(branchId);
        return printerService.findByBranch(branchId);
    }

    /**
     * (Opcional) Actualiza una impresora existente
     */
    @PutMapping("/{printerId}")
    public Printer updatePrinter(
            @PathVariable Long branchId,
            @PathVariable Long printerId,
            @RequestBody Printer printer
    ) {
        branchService.findById(branchId);
        printer.setId(printerId);
        printer.setBranch(branchService.findById(branchId));
        return printerService.update(printer);
    }

    /**
     * (Opcional) Elimina una impresora
     */
    @DeleteMapping("/{printerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePrinter(
            @PathVariable Long branchId,
            @PathVariable Long printerId
    ) {
        branchService.findById(branchId);
        printerService.delete(printerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Printer createPrinter(
            @PathVariable Long branchId,
            @Valid @RequestBody CreatePrinterRequestDTO dto
    ) {
        // 1) valida existencia de la sucursal
        Branch branch = branchService.findById(branchId);

        // 2) valida existencia del dispositivo
        Device device = deviceRepo.findById(dto.getDeviceId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Device not found"));

        // 3) mapea DTO → entidad
        Printer p = new Printer();
        p.setBranch(branch);
        p.setDevice(device);
        p.setName(dto.getName());
        p.setMacAddress(dto.getMacAddress());
        p.setType(dto.getType());

        // 4) guarda
        return printerService.create(p);
    }
}
