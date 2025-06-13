// src/main/java/com/movauy/mova/service/finance/CashRegisterService.java
package com.movauy.mova.service.finance;

import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.EstadoVenta;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterRepository cashRegisterRepository;
    private final SaleRepository saleRepository;
    private final AuthService authService;

    // Obtiene la caja abierta para la sucursal autenticada
    public Optional<CashRegister> getOpenCashRegister(String token) {
        Long branchId = authService.getBranchIdFromToken(token);
        return cashRegisterRepository.findByCloseDateIsNullAndBranch_Id(branchId);
    }

    // Abre una caja para el usuario autenticado si no existe ya una abierta para esa sucursal
    public boolean openCashRegister(String token, double initialAmount) {
        Long branchId = authService.getBranchIdFromToken(token);
        Long userId = authService.getUserBasicFromToken(token).getId();

        if (cashRegisterRepository.findByCloseDateIsNullAndBranch_Id(branchId).isPresent()) {
            return false;
        }

        User user = authService.getUserById(userId);

        CashRegister newCashRegister = CashRegister.builder()
                .initialAmount(initialAmount)
                .openDate(LocalDateTime.now())
                .open(true)
                .totalSales(0.0)
                .branch(authService.getBranchById(branchId))
                .user(user) // ✅ Ahora sí, seteamos el usuario
                .build();

        cashRegisterRepository.save(newCashRegister);
        return true;
    }

    // Cierra la caja abierta para la sucursal autenticada
    public Map<String, Object> closeCashRegister(String token) {
        Long branchId = authService.getBranchIdFromToken(token);
        Optional<CashRegister> openCash = cashRegisterRepository.findByCloseDateIsNullAndBranch_Id(branchId);

        if (openCash.isPresent()) {
            CashRegister cashRegister = openCash.get();
            double totalSold = calculateTotalSoldByCashRegister(cashRegister.getId());
            double expectedAmount = cashRegister.getInitialAmount() + totalSold;

            cashRegister.setTotalSales(totalSold);
            cashRegister.setCloseDate(LocalDateTime.now());
            cashRegister.setOpen(false);

            cashRegisterRepository.save(cashRegister);

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
                .filter(sale -> sale.getEstado() == EstadoVenta.ACTIVA)
                .mapToDouble(Sale::getTotalAmount)
                .sum();
    }

    /**
     * Obtiene la caja abierta para la sucursal indicada. Lanza excepción si no
     * existe ninguna caja abierta.
     */
    public CashRegister getOpenCashRegisterForBranch(Long branchId) {
        return cashRegisterRepository
                .findByCloseDateIsNullAndBranch_Id(branchId)
                .orElseThrow(()
                        -> new IllegalStateException("No hay ninguna caja abierta en la sucursal " + branchId)
                );
    }
}
