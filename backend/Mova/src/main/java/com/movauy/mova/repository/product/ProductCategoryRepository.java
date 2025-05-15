package com.movauy.mova.repository.product;

import com.movauy.mova.model.product.ProductCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductCategoryRepository extends JpaRepository<ProductCategory, Long> {
    
    List<ProductCategory> findByBranchId(Long branchId);

    Optional<ProductCategory> findById(Long id);

    Optional<ProductCategory> findByBranchIdAndName(Long branchId, String name);
}
