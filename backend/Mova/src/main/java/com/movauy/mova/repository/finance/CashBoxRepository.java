package com.movauy.mova.repository.finance;

import com.movauy.mova.model.finance.CashBox;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CashBoxRepository extends JpaRepository<CashBox, Long> {

    /** Busca la caja aún abierta por sucursal y código */
    Optional<CashBox> findByBranchIdAndCodeAndIsOpenTrue(Long branchId, String code);

    /** Verifica si hay esa caja abierta */
    boolean existsByBranchIdAndCodeAndIsOpenTrue(Long branchId, String code);

    /** Lista todas las cajas existentes de la sucursal, ordenadas por código */
    List<CashBox> findByBranchIdOrderByCode(Long branchId);

    /** Sólo las cajas abiertas */
    List<CashBox> findByBranchIdAndIsOpenTrue(Long branchId);

    /** Sólo las cajas cerradas */
    List<CashBox> findByBranchIdAndIsOpenFalse(Long branchId);

    /** Busca cualquier caja (abierta o cerrada) por código */
    Optional<CashBox> findByBranchIdAndCode(Long branchId, String code);

    /** Verifica si ya existe esa caja en la sucursal (independientemente de su estado) */
    boolean existsByBranchIdAndCode(Long branchId, String code);

    /** Cajas abiertas donde está asignado un usuario */
    List<CashBox> findByBranchIdAndIsOpenTrueAndAssignedUsers_Id(Long branchId, Long userId);
    
    /**
     * Borra todas las cajas de una sucursal (histórico).
     */
    void deleteByBranch_Id(Long branchId);
    
    Optional<CashBox> findByIdAndBranchIdAndIsOpenTrue(Long id, Long branchId);
    
    // Cuenta todas las cajas de la sucursal
    long countByBranchId(Long branchId);
    
    List<CashBox> findByBranchIdAndEnabledTrue(Long branchId);
    
    long countByBranchIdAndEnabledTrue(Long branchId);
    
    boolean existsByBranchIdAndCodeAndEnabledTrue(Long branchId, String code);
}
