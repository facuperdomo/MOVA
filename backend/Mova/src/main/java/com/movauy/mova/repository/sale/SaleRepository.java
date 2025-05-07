package com.movauy.mova.repository.sale;

import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.EstadoVenta;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByCashRegisterId(Long cashRegisterId);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.estado = 'activa'")
    Double calculateTotalSales();

    List<Sale> findByDateTimeAfter(LocalDateTime dateTime);

    List<Sale> findByDateTimeAfterAndEstado(LocalDateTime dateTime, EstadoVenta estado);

    // ✅ Actualiza estado de una venta a CANCELADA si pertenece a la empresa
    @Transactional
    @Modifying
    @Query("UPDATE Sale s SET s.estado = 'CANCELADA' WHERE s.id = :id AND s.user.companyId = :companyId")
    void cancelSaleByCompany(@Param("id") Long id, @Param("companyId") String companyId);

    // ✅ Filtra ventas por empresa (companyId como String) y fecha
    @Query("SELECT s FROM Sale s WHERE s.user.companyId = :companyId AND s.dateTime >= :date")
    List<Sale> findByUserCompanyIdAndDateTimeAfter(@Param("companyId") String companyId, @Param("date") LocalDateTime date);

    List<Sale> findByKitchenStatus(OrderStatus status);
}
