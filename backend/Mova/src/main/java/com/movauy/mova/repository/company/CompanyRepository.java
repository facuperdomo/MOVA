package com.movauy.mova.repository.company;

import com.movauy.mova.model.company.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CompanyRepository extends JpaRepository<Company, Long> {
    /**
     * Busca una empresa por su nombre (único).
     */
    Optional<Company> findByName(String name);
}
