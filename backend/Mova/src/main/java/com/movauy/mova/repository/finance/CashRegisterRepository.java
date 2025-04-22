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

    // Método global: obtiene la caja que aún no se cerró
    Optional<CashRegister> findByCloseDateIsNull();

    // Buscar la caja abierta para un usuario específico (filtrado por user.id)
    Optional<CashRegister> findByCloseDateIsNullAndUser_Id(Long userId);

    // Obtiene todas las cajas ordenadas por fecha de apertura descendente
    @Query("SELECT c FROM CashRegister c ORDER BY c.openDate DESC")
    List<CashRegister> findAllCashRegisters();

    // Obtiene las cajas cuyo cierre es posterior a una fecha dada
    @Query("SELECT c FROM CashRegister c WHERE c.closeDate >= :date ORDER BY c.openDate DESC")
    List<CashRegister> findCashRegisterAfter(@Param("date") LocalDateTime date);

    // Filtrado por empresa: obtiene las cajas con cierre posterior a una fecha dada para un usuario específico
    @Query("SELECT c FROM CashRegister c WHERE c.user.companyId = :companyId AND c.openDate >= :date ORDER BY c.openDate DESC")
    List<CashRegister> findCashRegisterAfterByCompany(@Param("companyId") String companyId, @Param("date") LocalDateTime date);
}
