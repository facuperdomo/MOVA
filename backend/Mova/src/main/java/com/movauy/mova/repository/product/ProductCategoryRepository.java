package com.movauy.mova.repository.product;

import com.movauy.mova.model.product.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    List<ProductCategory> findByCompanyId(String companyId);
    Optional<ProductCategory> findById(Long id);
    Optional<ProductCategory> findByCompanyIdAndName(String companyId, String name);
}