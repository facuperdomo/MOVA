// src/main/java/com/movauy/mova/repository/account/AccountItemRepository.java
package com.movauy.mova.repository.account;

import com.movauy.mova.model.account.AccountItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountItemRepository extends JpaRepository<AccountItem, Long> {

    List<AccountItem> findByAccountId(Long accountId);

    @Query("""
       select distinct ai
       from AccountItem ai
       join fetch ai.ingredients ing
       where ai.account.id = :accountId
         and ai.kitchenSent = false
    """)
    List<AccountItem> findPendingKitchenItems(@Param("accountId") Long accountId);
}
