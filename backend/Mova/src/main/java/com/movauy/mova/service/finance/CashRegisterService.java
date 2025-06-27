// src/main/java/com/movauy/mova/service/finance/CashRegisterService.java
package com.movauy.mova.service.finance;

import com.movauy.mova.model.finance.CashBox;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.finance.CashBoxRepository;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterRepository repo;
    private final CashBoxRepository boxRepo;
    private final AuthService authService;
    private final CashBoxService cashBoxService;
    private final SaleRepository saleRepository;

    /**
     * Histórico de movimientos de una caja.
     */
    public List<CashRegister> getHistory(Long boxId) {
        return repo.findByCashBoxIdOrderByOpenDateAsc(boxId);
    }

    /**
     * Lista todas las cajas abiertas en la sucursal que además estén asignadas
     * al usuario del token.
     */
    public List<CashBox> listCashRegisters(String token, Boolean openOnly) {
        Long userId = authService.getUserBasicFromToken(token).getId();
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();

        // Ahora usamos el flag isOpen en lugar de closedAt IS NULL
        List<CashBox> cajas = boxRepo.findByBranchIdAndIsOpenTrue(branchId);

        // Filtramos sólo las cajas en las que el usuario esté asignado
        return cajas.stream()
                .filter(c -> c.getAssignedUsers()
                .stream()
                .anyMatch(u -> u.getId().equals(userId)))
                .collect(Collectors.toList());
    }

    /**
     * Registra la apertura de caja: - crea un CashRegister con openDate y monto
     * inicial - marca la caja como abierta
     */
    @Transactional
    public CashRegister registerOpening(String token, Long boxId, double initialAmount) {
        // 1) Validar y cargar caja
        CashBox box = boxRepo.findById(boxId)
                .orElseThrow(() -> new IllegalArgumentException("CashBox no encontrada: " + boxId));

        // 2) Obtener usuario que abre
        User user = authService.getUserEntityFromToken(token);

        // 3) Crear registro de apertura
        CashRegister cr = CashRegister.builder()
                .cashBox(box)
                .branch(box.getBranch())
                .code(box.getCode())
                .initialAmount(initialAmount)
                .openDate(LocalDateTime.now())
                .totalSales(0.0)
                .closingAmount(0.0)
                .user(user)
                .build();

        // 4) Guardar
        CashRegister saved = repo.save(cr);

        // 5) Marcar la caja como abierta
        box.setIsOpen(true);
        boxRepo.save(box);

        return saved;
    }

    /**
     * Registra el cierre de caja: - recupera el registro de apertura (sin
     * closeDate) - calcula totalSales a partir de la suma de todos los amounts
     * - rellena closeDate, totalSales y closingAmount - marca la caja como
     * cerrada
     */
    @Transactional
    public CashRegister registerClosing(String token, Long boxId, double closingAmount) {
        // 1) Traer la caja abierta
        CashBox box = cashBoxService.getOpenCashBoxById(token, boxId);

        // 2) Traer el registro de apertura pendiente
        CashRegister opening = repo
                .findTopByCashBoxIdAndCloseDateIsNullOrderByOpenDateDesc(boxId)
                .orElseThrow(() -> new IllegalStateException(
                "No existe registro de apertura para caja " + boxId));

        // 3) Rango exacto entre apertura y cierre
        LocalDateTime open = opening.getOpenDate();
        LocalDateTime close = LocalDateTime.now();

        // 4) Sumar todas las ventas hechas en ese intervalo
        Double sum = saleRepository.sumSalesByBoxBetween(boxId, open, close);
        double totalSales = (sum != null) ? sum : 0.0;
        System.out.println(">>> totalSales para caja "+boxId+" entre "+open+" y "+close+" = "+totalSales);
        
        // 5) Rellenar campos de cierre
        opening.setCloseDate(close);
        opening.setTotalSales(totalSales);
        opening.setClosingAmount(closingAmount);

        // 6) Guardar y marcar caja como cerrada
        CashRegister saved = repo.save(opening);
        box.setIsOpen(false);
        boxRepo.save(box);

        return saved;
    }
    
}
