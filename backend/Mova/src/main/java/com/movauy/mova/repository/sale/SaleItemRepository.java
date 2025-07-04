package com.movauy.mova.repository.sale;

import com.movauy.mova.model.sale.SaleItem;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    /**
     * Devuelve los productos más vendidos para una sucursal (branch), a partir
     * de una fecha.
     */
    @Query("""
        SELECT new map(
            p.name AS name,
            SUM(si.quantity) AS totalSold
        )
        FROM SaleItem si
        JOIN si.product p
        WHERE si.sale.dateTime >= :startDate
          AND si.sale.branch.id = :branchId
        GROUP BY p.name
        ORDER BY totalSold DESC
    """)
    List<Map<String, Object>> findTopSellingProductsByBranch(
            @Param("startDate") LocalDateTime startDate,
            @Param("branchId") Long branchId
    );

    /**
     * Cuenta el total de unidades vendidas por una sucursal (branch).
     */
    @Query("""
        SELECT COALESCE(SUM(si.quantity), 0)
        FROM SaleItem si
        WHERE si.sale.branch.id = :branchId
    """)
    int countTotalSaleItemsByBranch(@Param("branchId") Long branchId);

    @Query("SELECT si.product.name, SUM(si.quantity) "
            + "FROM SaleItem si "
            + "WHERE si.sale.branch.id = :branchId AND si.sale.dateTime BETWEEN :start AND :end "
            + "GROUP BY si.product.name "
            + "ORDER BY SUM(si.quantity) DESC")
    List<Object[]> findTopSellingProductsByBranch(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("branchId") Long branchId);

    boolean existsByProductId(Long productId);

    @Query("""
  SELECT p.name, SUM(i.quantity)
    FROM SaleItem i
    JOIN i.sale s
    JOIN s.cashBox cb
    JOIN i.product p
   WHERE s.branch.id = :branchId
     AND cb.id IN :boxIds
     AND s.dateTime BETWEEN :start AND :end
   GROUP BY p.name
   ORDER BY SUM(i.quantity) DESC
""")
    List<Object[]> findTopSellingProductsByBranchAndBoxIds(
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end,
            @Param("branchId") Long branchId,
            @Param("boxIds") List<Long> boxIds
    );
}
