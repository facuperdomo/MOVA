package com.movauy.mova.repository.product;

import com.movauy.mova.model.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 *
 * @author Facundo
 */
public interface ProductRepository extends JpaRepository<Product, Long> {
}
