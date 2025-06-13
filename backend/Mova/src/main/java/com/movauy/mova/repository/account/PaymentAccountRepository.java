package com.movauy.mova.repository.account;

import com.movauy.mova.model.account.PaymentAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio para manejar los registros de PaymentAccount.
 */
@Repository
public interface PaymentAccountRepository extends JpaRepository<PaymentAccount, Long> {
    /**
     * Obtiene todos los pagos realizados para una cuenta específica.
     *
     * @param accountId el ID de la cuenta
     * @return lista de PaymentAccount
     */
    List<PaymentAccount> findByAccountId(Long accountId);
    
    int countByAccountId(Long accountId);
}
