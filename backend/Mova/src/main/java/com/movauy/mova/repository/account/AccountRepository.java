package com.movauy.mova.repository.account;

import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.branch.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByBranchAndClosed(Branch branch, boolean closed);
}

