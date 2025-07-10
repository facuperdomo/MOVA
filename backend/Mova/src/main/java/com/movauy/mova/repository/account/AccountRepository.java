package com.movauy.mova.repository.account;

import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.branch.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByBranchAndClosed(Branch branch, boolean closed);

    /**
     * Trae la cuenta, todas sus líneas y cada línea con sus ingredients.
     */
    @EntityGraph(attributePaths = {"items", "items.ingredients"})
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdWithItemsAndIngredients(@Param("id") Long id);
}
