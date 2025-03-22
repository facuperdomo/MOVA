package com.movauy.mova.repository.sale;

import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.EstadoVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByCashRegisterId(Long cashRegisterId);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.estado = 'activa'")
    Double calculateTotalSales();

    List<Sale> findByDateTimeAfter(LocalDateTime dateTime);

    @Transactional
    @Modifying
    @Query("UPDATE Sale s SET s.estado = 'cancelada' WHERE s.id = :id AND s.user.id = :companyId")
    void cancelarVenta(@Param("id") Long id, @Param("companyId") Integer companyId);

    List<Sale> findByDateTimeAfterAndEstado(LocalDateTime dateTime, EstadoVenta estado);

    // Método para filtrar ventas por empresa y fecha
    @Query("SELECT s FROM Sale s WHERE s.user.id = :companyId AND s.dateTime >= :date")
    List<Sale> findByCompanyIdAndDateTimeAfter(@Param("companyId") Integer companyId, @Param("date") LocalDateTime date);
}
