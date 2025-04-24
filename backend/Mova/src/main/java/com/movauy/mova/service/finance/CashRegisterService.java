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

    // Obtiene la caja abierta para el usuario autenticado
    public Optional<CashRegister> getOpenCashRegister(String token) {
        UserBasicDTO currentUser = authService.getUserBasicFromToken(token);
        return cashRegisterRepository.findByCloseDateIsNullAndUser_Id(currentUser.getId());
    }

    // Abre una caja para el usuario autenticado si no existe ya una abierta para ese usuario
    public boolean openCashRegister(String token, double initialAmount) {
        UserBasicDTO currentUser = authService.getUserBasicFromToken(token);

        if (cashRegisterRepository.findByCloseDateIsNullAndUser_Id(currentUser.getId()).isPresent()) {
            return false; // Ya existe una caja abierta para este usuario
        }

        // Si necesitás el usuario completo para persistirlo (por relaciones JPA), podés cargarlo desde el repo
        User userEntity = authService.getUserById(currentUser.getId());

        CashRegister newCashRegister = CashRegister.builder()
                .initialAmount(initialAmount)
                .openDate(LocalDateTime.now())
                .open(true)
                .totalSales(0.0)
                .user(userEntity)  // Relación completa para persistencia
                .build();

        cashRegisterRepository.save(newCashRegister);
        return true;
    }

    // Cierra la caja abierta para el usuario autenticado
    public Map<String, Object> closeCashRegister(String token) {
        UserBasicDTO currentUser = authService.getUserBasicFromToken(token);
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
                .filter(sale -> sale.getEstado() == EstadoVenta.ACTIVA)
                .mapToDouble(Sale::getTotalAmount)
                .sum();
    }
}
