// src/main/java/com/movauy/mova/repository/finance/CashRegisterRepository.java
package com.movauy.mova.repository.finance;

import com.movauy.mova.model.finance.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    Optional<CashRegister> findByCloseDateIsNull();

    // Mejor: por branch directamente
    Optional<CashRegister> findByCloseDateIsNullAndBranch_Id(Long branchId);

    List<CashRegister> findAllByOrderByOpenDateDesc();

    List<CashRegister> findByCloseDateAfterOrderByOpenDateDesc(LocalDateTime date);

    List<CashRegister> findByBranch_IdAndOpenDateAfterOrderByOpenDateDesc(Long branchId, LocalDateTime date);
    
    /**
     * Borra todos los registros de caja asociados a un usuario dado.
     */
    void deleteByUser_Id(Long userId);

    /**
     * Borra todos los registros de caja asociados a los usuarios cuyos IDs
     * están en la lista proporcionada.
     */
    void deleteByUser_IdIn(List<Long> userIds);
    
    List<CashRegister> findByBranch_IdAndOpenDateBetweenOrderByOpenDateDesc(Long branchId, LocalDateTime start, LocalDateTime end);
}

