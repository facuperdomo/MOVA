package com.movauy.mova.repository.sale;

import com.movauy.mova.model.sale.SaleItem;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Map;
import org.springframework.data.repository.query.Param;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("SELECT new map(p.name as name, SUM(si.quantity) as totalSold) "
            + "FROM SaleItem si "
            + "JOIN si.product p "
            + "WHERE si.sale.dateTime >= :startDate AND si.sale.user.companyId = :companyId "
            + "GROUP BY p.name "
            + "ORDER BY totalSold DESC")
    List<Map<String, Object>> findTopSellingProductsByCompany(@Param("startDate") LocalDateTime startDate,
            @Param("companyId") String companyId);

    @Query("SELECT COALESCE(SUM(si.quantity), 0) FROM SaleItem si WHERE si.sale.user.id = :companyId")
    int countTotalSaleItemsByCompany(@Param("companyId") Integer companyId);
}
