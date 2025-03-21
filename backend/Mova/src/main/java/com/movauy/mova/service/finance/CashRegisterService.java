package com.movauy.mova.service.finance;

import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 *
 * @author Facundo
 */
@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterRepository cashRegisterRepository;
    private final SaleRepository saleRepository; // Repositorio para obtener ventas

    public Optional<CashRegister> getOpenCashRegister() {
        return cashRegisterRepository.findByCloseDateIsNull();
    }

    public boolean openCashRegister(double initialAmount) {
        if (getOpenCashRegister().isPresent()) {
            return false;
        }

        CashRegister newCashRegister = CashRegister.builder()
                .initialAmount(initialAmount)
                .openDate(LocalDateTime.now())
                .open(true)
                .build();

        cashRegisterRepository.save(newCashRegister);
        return true;
    }

    public Map<String, Object> closeCashRegister() {
        Optional<CashRegister> openCash = getOpenCashRegister();
        if (openCash.isPresent()) {
            CashRegister cashRegister = openCash.get();
            cashRegister.setCloseDate(LocalDateTime.now());
            cashRegister.setOpen(false);
            cashRegisterRepository.save(cashRegister);

            // ✅ Calcular solo ventas asociadas a esta caja
            double totalSold = calculateTotalSoldByCashRegister(cashRegister.getId());
            double expectedAmount = cashRegister.getInitialAmount() + totalSold;

            Map<String, Object> result = new HashMap<>();
            result.put("totalSold", totalSold);
            result.put("expectedAmount", expectedAmount);

            return result;
        }
        return null;
    }

    private double calculateTotalSoldByCashRegister(Long cashRegisterId) {
        List<Sale> sales = saleRepository.findByCashRegisterId(cashRegisterId);
        return sales.stream()
                .filter(sale -> "CASH".equalsIgnoreCase(sale.getPaymentMethod()))
                .mapToDouble(Sale::getTotalAmount)
                .sum();
    }
}
