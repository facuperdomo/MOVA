// src/main/java/com/movauy/mova/repository/account/AccountItemRepository.java
package com.movauy.mova.repository.account;

import com.movauy.mova.model.account.AccountItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountItemRepository extends JpaRepository<AccountItem, Long> {
    List<AccountItem> findByAccountId(Long accountId);
}
