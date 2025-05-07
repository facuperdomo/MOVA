package com.movauy.mova.repository.sale;

import com.movauy.mova.model.sale.SaleItemIngredient;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author Facundo
 */
public interface SaleItemIngredientRepository extends JpaRepository<SaleItemIngredient, Long> {
    List<SaleItemIngredient> findBySaleItemId(Long saleItemId);
}
