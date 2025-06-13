package com.movauy.mova.service.account;

import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.account.PaymentAccount;
import com.movauy.mova.model.account.PaymentAccount.Status;
import com.movauy.mova.repository.account.AccountRepository;
import com.movauy.mova.repository.account.PaymentAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentAccountService {

    private final PaymentAccountRepository paymentRepo;
    private final AccountRepository accountRepo;

    /**
     * Registra un pago parcial o total sobre una cuenta.
     *
     * @param accountId   id de la cuenta a pagar
     * @param amount      monto pagado
     * @param payerName   nombre de quien paga
     * @param closeAfter  si viene true, y con este pago la cuenta queda totalmente saldada,
     *                    se marcará la cuenta como cerrada
     * @return el objeto PaymentAccount guardado
     */
    @Transactional
    public PaymentAccount addPayment(Long accountId,
                                     BigDecimal amount,
                                     String payerName,
                                     Boolean closeAfter) {

        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada: " + accountId));

        // 1) Total ya pagado hasta antes de este nuevo pago
        BigDecimal paidSoFar = paymentRepo
                .findByAccountId(accountId)
                .stream()
                .map(PaymentAccount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2) Total con este nuevo pago
        BigDecimal newTotalPaid = paidSoFar.add(amount);

        // 3) Total de la cuenta (precio * cantidad de cada item)
        BigDecimal accountTotal = account.calculateTotal();

        // 4) Determinamos el estado del pago (pagada completamente o parcialmente)
        Status status = newTotalPaid.compareTo(accountTotal) >= 0
                ? Status.PAID_IN_FULL
                : Status.PARTIALLY_PAID;

        // 5) Creamos el registro de PaymentAccount
        PaymentAccount payment = new PaymentAccount(account, amount, status);
        payment.setPayerName(payerName);

        // 6) Si el pago cubre todo y closeAfter == true, cerramos la cuenta
        if (status == Status.PAID_IN_FULL && Boolean.TRUE.equals(closeAfter)) {
            account.setClosed(true);
            accountRepo.save(account);
        }

        // 7) Actualizamos splitRemaining si estaba usando split
        if (account.getSplitRemaining() != null && account.getSplitRemaining() > 0) {
            account.setSplitRemaining(account.getSplitRemaining() - 1);
            accountRepo.save(account);
        }

        // 8) Guardamos y devolvemos el pago
        return paymentRepo.save(payment);
    }

    /**
     * Devuelve todos los pagos asociados a una cuenta.
     */
    @Transactional(readOnly = true)
    public List<PaymentAccount> getPaymentsForAccount(Long accountId) {
        if (!accountRepo.existsById(accountId)) {
            throw new IllegalArgumentException("Cuenta no encontrada: " + accountId);
        }
        return paymentRepo.findByAccountId(accountId);
    }

    /**
     * Divide lo que resta pagar en X partes iguales.
     *
     * @param accountId Id de la cuenta
     * @param numPeople Número de personas (nuevas) para repartir
     * @return Monto (en pesos) que debe pagar cada persona de lo que queda
     */
    @Transactional(readOnly = true)
    public BigDecimal getSharePerPerson(Long accountId, int numPeople) {
        Account account = accountRepo.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("Cuenta no encontrada: " + accountId));

        // 1) Total actual de la cuenta en pesos
        BigDecimal currentTotal = account.calculateTotal();

        // 2) Monto ya pagado en pesos
        BigDecimal paidSoFar = paymentRepo
                .findByAccountId(accountId)
                .stream()
                .map(PaymentAccount::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3) Dinero restante en pesos
        BigDecimal remainingMoney = currentTotal.subtract(paidSoFar);

        // 4) Cuántas porciones iniciales había y cuántas quedaron
        int totalSlices = account.getSplitTotal() == null ? 0 : account.getSplitTotal();
        int remainingSlices = account.getSplitRemaining() == null ? totalSlices : account.getSplitRemaining();
        int paidCount = totalSlices - remainingSlices;

        // 5) Cuántas personas faltan por pagar entre estas numPeople
        int remainingPeople = Math.max(1, numPeople - paidCount);

        // 6) División final: lo que queda ÷ cuántos faltan
        return remainingMoney.divide(
                BigDecimal.valueOf(remainingPeople),
                2,
                RoundingMode.HALF_UP
        );
    }
}
