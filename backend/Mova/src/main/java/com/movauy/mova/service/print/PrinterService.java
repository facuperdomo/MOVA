// src/main/java/com/movauy/mova/service/print/PrinterService.java
package com.movauy.mova.service.print;

import com.movauy.mova.model.device.Device;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.repository.print.PrinterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PrinterService {

    private final PrinterRepository repo;

    public Printer create(Printer p) {
        return repo.save(p);
    }

    public Printer update(Printer p) {
        return repo.save(p);
    }

    public void delete(Long id) {
        repo.deleteById(id);
    }

    /** recupera todas las impresoras de una sucursal */
    public List<Printer> findByBranch(Long branchId) {
        return repo.findAllByBranchId(branchId);
    }

    /** recupera todas las impresoras asociadas a un dispositivo (tablet) */
    public List<Printer> findByDevice(Long deviceId) {
        return repo.findAllByDeviceId(deviceId);
    }
    
     /** Sincroniza la lista de impresoras asignadas a un dispositivo */
    @Transactional
    public void assignPrinters(Device device, List<Long> printerIds) {
        // 1) Desvincula TODO lo que ya estaba asignado a este device
        repo.findAllByDeviceId(device.getId())
            .forEach(p -> {
                p.setDevice(null);
                repo.save(p);
            });
        // 2) Vuelve a vincular sólo los que llegan en printerIds
        if (printerIds != null && !printerIds.isEmpty()) {
            repo.findAllById(printerIds)
                .forEach(p -> {
                    p.setDevice(device);
                    repo.save(p);
                });
        }
    }
}
