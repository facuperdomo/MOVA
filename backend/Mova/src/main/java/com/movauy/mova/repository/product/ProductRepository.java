package com.movauy.mova.repository.product;

import com.movauy.mova.model.product.Product;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {
    
    @Query("SELECT p FROM Product p WHERE p.user.id = :companyId")
    List<Product> getProductsByCompany(@Param("companyId") Integer companyId);
}
