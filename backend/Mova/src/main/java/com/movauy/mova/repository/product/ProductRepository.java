package com.movauy.mova.repository.product;

import com.movauy.mova.model.product.Product;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT p FROM Product p WHERE p.branch.id = :branchId")
    List<Product> getProductsByBranch(@Param("branchId") Long branchId);

    List<Product> findByCategoryId(Long categoryId);

    boolean existsByCategoryId(Long categoryId);
    
    Optional<Product> findByBranchIdAndName(Long branchId, String name);
    
    List<Product> findAllByBranchIdAndActiveTrue(Long branchId);
    
    Optional<Product> findByBranchIdAndNameAndActiveTrue(Long branchId, String name);
}
