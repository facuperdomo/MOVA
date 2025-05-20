// src/main/java/com/movauy/mova/controller/device/DeviceController.java
package com.movauy.mova.controller.device;

import com.movauy.mova.dto.DeviceDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.device.Device;
import com.movauy.mova.repository.device.DeviceRepository;
import com.movauy.mova.service.branch.BranchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/branches/{branchId}/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceRepository deviceRepo;
    private final BranchService branchService;

    @GetMapping
    public List<DeviceDTO> listDevices(@PathVariable Long branchId) {
        branchService.findById(branchId);
        return deviceRepo.findAllByBranchId(branchId).stream()
                         .map(this::toDto)
                         .collect(Collectors.toList());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeviceDTO createDevice(@PathVariable Long branchId,
                                  @RequestBody DeviceDTO dto) {
        Branch branch = branchService.findById(branchId);

        // Mapeo manual del DTO a la entidad
        Device device = new Device();
        device.setName(dto.getName());
        device.setBridgeUrl(dto.getBridgeUrl());
        device.setUuid(dto.getUuid());
        device.setBranch(branch);

        Device saved = deviceRepo.save(device);
        return toDto(saved);
    }

    @PutMapping("/{deviceId}")
    public DeviceDTO updateDevice(@PathVariable Long branchId,
                                  @PathVariable Long deviceId,
                                  @RequestBody DeviceDTO dto) {
        branchService.findById(branchId);
        Device existing = deviceRepo.findById(deviceId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Device not found"));

        existing.setName(dto.getName());
        existing.setBridgeUrl(dto.getBridgeUrl());
        Device updated = deviceRepo.save(existing);
        return toDto(updated);
    }

    @DeleteMapping("/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDevice(@PathVariable Long branchId,
                             @PathVariable Long deviceId) {
        branchService.findById(branchId);
        deviceRepo.deleteById(deviceId);
    }

    private DeviceDTO toDto(Device d) {
        return DeviceDTO.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .bridgeUrl(d.getBridgeUrl())
                        .branchId(d.getBranch().getId())
                        .build();
    }
}
