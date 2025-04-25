package com.movauy.mova.repository.ingredient;

import com.movauy.mova.model.ingredient.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    /**
     * Devuelve todos los ingredientes de una empresa.
     * @param companyId
     * @return 
     */
    List<Ingredient> findAllByCompanyId(Long companyId);

    /**
     * Devuelve sólo los ingredientes cuya id está en la lista y
     * que además pertenecen a la empresa indicada.
     * @param companyId
     * @param ids
     * @return 
     */
    @Query("SELECT i FROM Ingredient i WHERE i.company.id = :companyId AND i.id IN :ids")
    List<Ingredient> findByIdsForCompany(
        @Param("companyId") Long companyId,
        @Param("ids") List<Long> ids
    );
}
