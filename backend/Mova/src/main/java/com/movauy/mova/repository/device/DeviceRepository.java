package com.movauy.mova.repository.device;

import com.movauy.mova.model.device.Device;
import com.movauy.mova.model.print.Printer;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {
    List<Device> findAllByBranchId(Long branchId);
}
