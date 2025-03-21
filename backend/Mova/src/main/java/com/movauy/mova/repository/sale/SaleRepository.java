package com.movauy.mova.repository.sale;

import com.movauy.mova.model.sale.Sale;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author Facundo
 */
public interface SaleRepository extends JpaRepository<Sale, Long> {
    List<Sale> findByCashRegisterId(Long cashRegisterId);
}
