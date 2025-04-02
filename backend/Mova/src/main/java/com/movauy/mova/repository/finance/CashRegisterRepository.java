package com.movauy.mova.repository.finance;

import com.movauy.mova.dto.CashRegisterDTO;
import com.movauy.mova.model.finance.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    // Método global (si se necesitara)
    Optional<CashRegister> findByCloseDateIsNull();

    // NUEVO: Buscar caja abierta para un usuario específico
    Optional<CashRegister> findByCloseDateIsNullAndUser_Id(Long userId);

    @Query("SELECT c FROM CashRegister c ORDER BY c.openDate DESC")
    List<CashRegister> findAllCashRegisters();

    @Query("SELECT c FROM CashRegister c WHERE c.closeDate >= :date ORDER BY c.openDate DESC")
    List<CashRegister> findCashRegisterAfter(@Param("date") LocalDateTime date);
    
    // Filtrado por empresa usando c.user.id
    @Query("SELECT c FROM CashRegister c WHERE c.user.id = :companyId AND c.closeDate >= :date ORDER BY c.openDate DESC")
    List<CashRegister> findCashRegisterAfterByCompany(@Param("companyId") Integer companyId, @Param("date") LocalDateTime date);
}
