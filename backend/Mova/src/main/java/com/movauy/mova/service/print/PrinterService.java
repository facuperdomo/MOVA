// src/main/java/com/movauy/mova/service/PrinterService.java
package com.movauy.mova.service.print;

import com.movauy.mova.model.print.Printer;
import com.movauy.mova.repository.print.PrinterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

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

    public List<Printer> findByBranch(Long branchId) {
        return repo.findAllByBranchId(branchId);
    }
}
