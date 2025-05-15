package com.movauy.mova.repository.branch;

import com.movauy.mova.model.branch.Branch;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByUsername(String username);
    List<Branch> findByCompanyId(Long companyId);
    long countByCompanyId(Long companyId);
}
