package com.movauy.mova.service.finance;

import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.user.AuthService;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterRepository cashRegisterRepository;
    private final SaleRepository saleRepository;
    private final AuthService authService; // Inyectamos AuthService

    // Obtiene la caja abierta para el usuario autenticado
    public Optional<CashRegister> getOpenCashRegister(String token) {
        Long companyId = authService.getCompanyIdFromToken(token);
        User currentUser = authService.getUserById(companyId);
        return cashRegisterRepository.findByCloseDateIsNullAndUser_Id(currentUser.getId());
    }

    // Abre una caja para el usuario autenticado si no existe ya una abierta para ese usuario
    public boolean openCashRegister(String token, double initialAmount) {
        Long companyId = authService.getCompanyIdFromToken(token);
        User currentUser = authService.getUserById(companyId);

        if (cashRegisterRepository.findByCloseDateIsNullAndUser_Id(currentUser.getId()).isPresent()) {
            return false; // Ya existe una caja abierta para esta empresa
        }
        
        CashRegister newCashRegister = CashRegister.builder()
                .initialAmount(initialAmount)
                .openDate(LocalDateTime.now())
                .open(true)
                .totalSales(0.0)
                .user(currentUser)  // Asignamos el usuario a la caja
                .build();

        cashRegisterRepository.save(newCashRegister);
        return true;
    }

    // Cierra la caja abierta para el usuario autenticado
    public Map<String, Object> closeCashRegister(String token) {
        Long companyId = authService.getCompanyIdFromToken(token);
        User currentUser = authService.getUserById(companyId);
        Optional<CashRegister> openCash = cashRegisterRepository.findByCloseDateIsNullAndUser_Id(currentUser.getId());
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
        System.out.println("Ventas encontradas para la caja " + cashRegisterId + ": " + sales.size());

        sales.forEach(sale -> System.out.println("Venta: " + sale.getTotalAmount() + " - Método: " + sale.getPaymentMethod()));

        return sales.stream()
                .filter(sale -> "CASH".equalsIgnoreCase(sale.getPaymentMethod()))
                .mapToDouble(Sale::getTotalAmount)
                .sum();
    }
}
