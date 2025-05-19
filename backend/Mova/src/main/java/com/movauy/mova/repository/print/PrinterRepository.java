// src/main/java/com/movauy/mova/repository/PrinterRepository.java
package com.movauy.mova.repository.print;

import com.movauy.mova.model.print.Printer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PrinterRepository extends JpaRepository<Printer, Long> {
    List<Printer> findAllByBranchId(Long branchId);
    List<Printer> findByBranchId(Long branchId);
    List<Printer> findByDevice_Id(Long deviceId);
    List<Printer> findAllByDeviceId(Long deviceId);
}
