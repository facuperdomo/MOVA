package com.movauy.mova.repository.finance;

import com.movauy.mova.model.finance.CashRegister;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CashRegisterRepository extends JpaRepository<CashRegister, Long> {

    /**
     * Histórico de movimientos (aperturas y cierres) de una caja, ordenado por
     * fecha de apertura.
     */
    List<CashRegister> findByCashBoxIdOrderByOpenDateAsc(Long boxId);

    /**
     * Suma de todos los initialAmount (por ejemplo, para totalizar aperturas).
     */
    @Query("SELECT COALESCE(SUM(cr.initialAmount),0) FROM CashRegister cr WHERE cr.cashBox.id = :boxId")
    Double sumInitialAmounts(@Param("boxId") Long boxId);

    /**
     * Histórico de movimientos de TODAS las cajas de una sucursal entre dos
     * fechas de apertura.
     */
    List<CashRegister> findByCashBoxBranchIdAndOpenDateBetween(
            Long branchId,
            LocalDateTime start,
            LocalDateTime end
    );

    /**
     * Borra todos los movimientos de caja de todas las cashBox que pertenezcan
     * a una sucursal.
     */
    void deleteByCashBoxBranchId(Long branchId);

    /**
     * Borra todos los movimientos hechos por una lista de usuarios.
     */
    void deleteByUserIdIn(List<Long> userIds);

    /**
     * Suma de todos los closingAmount (cierres) ya registrados.
     */
    @Query("""
      SELECT COALESCE(SUM(cr.closingAmount),0)
        FROM CashRegister cr
       WHERE cr.cashBox.id = :boxId
         AND cr.closeDate IS NOT NULL
    """)
    Double sumClosingAmounts(@Param("boxId") Long boxId);

    /**
     * Busca el registro abierto (sin fecha de cierre aún).
     */
    Optional<CashRegister> findByCashBoxIdAndCloseDateIsNull(Long boxId);

    /**
     * Último registro de apertura pendiente (sin cerrar), por si guardas varias
     * aperturas consecutivas sin cierre.
     */
    Optional<CashRegister> findTopByCashBoxIdAndCloseDateIsNullOrderByOpenDateDesc(Long boxId);

    Optional<CashRegister> findByIdAndBranch_Id(Long id, Long branchId);

    @Query("""
  SELECT cr
    FROM CashRegister cr
   WHERE cr.cashBox.branch.id = :branchId
     AND (
         (cr.openDate BETWEEN :start AND :end)
      OR (cr.closeDate BETWEEN :start AND :end)
      OR (cr.openDate <= :start AND (cr.closeDate IS NULL OR cr.closeDate >= :start))
     )
""")
    List<CashRegister> findOverlappingByBranch(
            @Param("branchId") Long branchId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );

}
