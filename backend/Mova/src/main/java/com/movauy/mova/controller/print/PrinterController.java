package com.movauy.mova.controller.print;

import com.movauy.mova.dto.CreatePrinterRequestDTO;
import com.movauy.mova.model.device.Device;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.repository.device.DeviceRepository;
import com.movauy.mova.service.print.PrinterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/devices/{deviceId}/printers")
@RequiredArgsConstructor
public class PrinterController {

    private final PrinterService printerService;
    private final DeviceRepository deviceRepo;

    /**
     * Lista todas las impresoras de un dispositivo
     */
    @GetMapping
    public List<Printer> list(@PathVariable("deviceId") Long deviceId) {
        Device device = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Device not found"));
        return printerService.findByDevice(deviceId);
    }

    /**
     * Crea una nueva impresora bajo un dispositivo
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Printer create(
            @PathVariable Long deviceId,
            @Valid @RequestBody CreatePrinterRequestDTO dto
    ) {
        Device device = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Device not found"));

        // Mapea el DTO a entidad
        Printer p = new Printer();
        p.setDevice(device);
        p.setBranch(device.getBranch());
        p.setName(dto.getName());
        p.setMacAddress(dto.getMacAddress());
        p.setType(dto.getType());

        return printerService.create(p);
    }

    @PutMapping("/{printerId}")
    public Printer update(
            @PathVariable Long deviceId,
            @PathVariable Long printerId,
            @Valid @RequestBody CreatePrinterRequestDTO dto
    ) {
        Device device = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Device not found"));

        Printer p = new Printer();
        p.setId(printerId);
        p.setDevice(device);
        p.setBranch(device.getBranch());
        p.setName(dto.getName());
        p.setMacAddress(dto.getMacAddress());
        p.setType(dto.getType());

        return printerService.update(p);
    }

    /**
     * Elimina una impresora
     */
    @DeleteMapping("/{printerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("deviceId") Long deviceId,
            @PathVariable("printerId") Long printerId) {
        // opcional: validar que la impresora pertenezca a este dispositivo
        printerService.delete(printerId);
    }

    /**
     * Sincroniza la lista de impresoras asignadas al dispositivo
     */
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignPrinters(
            @PathVariable Long deviceId,
            @RequestBody List<Long> printerIds // lista de IDs a dejar asignados
    ) {
        Device device = deviceRepo.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Device not found"));

        printerService.assignPrinters(device, printerIds);
    }
}
