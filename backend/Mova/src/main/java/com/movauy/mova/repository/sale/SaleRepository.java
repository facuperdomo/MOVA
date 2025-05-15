// src/main/java/com/movauy/mova/repository/sale/SaleRepository.java
package com.movauy.mova.repository.sale;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.EstadoVenta;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByCashRegisterId(Long cashRegisterId);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) "
            + "FROM Sale s "
            + "WHERE s.estado = 'ACTIVA'")
    Double calculateTotalSales();

    List<Sale> findByDateTimeAfter(LocalDateTime dateTime);

    List<Sale> findByDateTimeAfterAndEstado(LocalDateTime dateTime, EstadoVenta estado);

    @Modifying
    @Transactional
    @Query("UPDATE Sale s SET s.estado = 'CANCELADA' WHERE s.id = :id AND s.branch.id = :branchId")
    void cancelSaleByBranch(@Param("id") Long id, @Param("branchId") Long branchId);

    List<Sale> findByBranch_IdAndDateTimeAfter(Long branchId, LocalDateTime dateTime);

    List<Sale> findByKitchenStatus(OrderStatus status);
    
    List<Sale> findByBranchIdAndKitchenStatus(Long branchId, OrderStatus status);
    
    List<Sale> findByBranchIn(List<Branch> branches);
    
    List<Sale> findByBranchInAndDateTimeAfter(List<Branch> branches, LocalDateTime dateTime);
    
    List<Sale> findByBranchInAndDateTimeBetween(List<Branch> branches, LocalDateTime start, LocalDateTime end);
    
    List<Sale> findByBranch_IdAndDateTimeBetween(Long branchId, LocalDateTime start, LocalDateTime end);
}
