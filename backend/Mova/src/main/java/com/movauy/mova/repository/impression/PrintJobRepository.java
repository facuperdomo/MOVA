// src/main/java/com/movauy/mova/repository/impression/PrintJobRepository.java
package com.movauy.mova.repository.impression;

import com.movauy.mova.model.impression.PrintJob;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJob,Long> {
  Optional<PrintJob> findFirstByStatusAndCompanyIdOrderByCreatedAtAsc(
    PrintJob.Status status, String companyId);
}
