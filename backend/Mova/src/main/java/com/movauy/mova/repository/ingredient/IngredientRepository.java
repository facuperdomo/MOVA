// src/main/java/com/movauy/mova/repository/ingredient/IngredientRepository.java
package com.movauy.mova.repository.ingredient;

import com.movauy.mova.model.ingredient.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /**
     * Devuelve todos los ingredientes de una sucursal.
     */
    List<Ingredient> findAllByBranchId(Long branchId);

    /**
     * Devuelve todos los ingredientes asociados a una sucursal.
     */
    List<Ingredient> findByBranch_Id(Long branchId);

    /**
     * Devuelve los ingredientes por ID, filtrados por sucursal.
     */
    @Query("""
        SELECT i FROM Ingredient i
        WHERE i.branch.id = :branchId
          AND i.id IN :ids
    """)
    List<Ingredient> findByIdsForBranch(
        @Param("branchId") Long branchId,
        @Param("ids") List<Long> ids
    );

    /**
     * Busca un ingrediente por nombre dentro de una sucursal.
     */
    Optional<Ingredient> findByBranchIdAndName(Long branchId, String name);

    /**
     * Borra todos los ingredientes de una sucursal (para delete-force).
     */
    void deleteByBranch_Id(Long branchId);
}
