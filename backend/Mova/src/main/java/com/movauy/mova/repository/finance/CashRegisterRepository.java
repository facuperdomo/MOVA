package com.movauy.mova.repository.finance;

import com.movauy.mova.model.finance.CashRegister;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 *
 * @author Facundo
 */
@Repository
public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    // Encuentra la caja abierta (la que no tiene fecha de cierre)
    Optional<CashRegister> findByCloseDateIsNull();
}
