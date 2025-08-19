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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CashRegisterService {

    private final CashRegisterRepository repo;
    private final CashBoxRepository boxRepo;
    private final AuthService authService;
    private final CashBoxService cashBoxService;
    private final SaleRepository saleRepository;

    // === Constantes y utilitario usados en esta clase ===
    private static final String NO_DATA = "Sin datos";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private LocalDateTime getStartDate(String filter) {
        LocalDateTime now = LocalDateTime.now();
        return switch (filter) {
            case "day"   -> now.truncatedTo(ChronoUnit.DAYS);
            case "week"  -> now.minusWeeks(1).truncatedTo(ChronoUnit.DAYS);
            case "month" -> now.minusMonths(1).truncatedTo(ChronoUnit.DAYS);
            case "year"  -> now.minusYears(1).truncatedTo(ChronoUnit.DAYS);
            default      -> now.minusMonths(1).truncatedTo(ChronoUnit.DAYS);
        };
    }
    // ====================================================

    public List<CashRegister> getHistory(Long boxId) {
        return repo.findByCashBoxIdOrderByOpenDateAsc(boxId);
    }

    public List<CashBox> listCashRegisters(String token, Boolean openOnly) {
        Long userId = authService.getUserBasicFromToken(token).getId();
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();

        List<CashBox> cajas = boxRepo.findByBranchIdAndIsOpenTrue(branchId);

        return cajas.stream()
                .filter(c -> c.getAssignedUsers().stream().anyMatch(u -> u.getId().equals(userId)))
                .collect(Collectors.toList());
    }

    @Transactional
    public CashRegister registerOpening(String token, Long boxId, double initialAmount) {
        CashBox box = boxRepo.findById(boxId)
                .orElseThrow(() -> new IllegalArgumentException("CashBox no encontrada: " + boxId));

        User user = authService.getUserEntityFromToken(token);

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

        CashRegister saved = repo.save(cr);

        box.setIsOpen(true);
        boxRepo.save(box);

        return saved;
    }

    @Transactional
    public CashRegister registerClosing(String token, Long boxId, double closingAmount) {
        CashBox box = cashBoxService.getOpenCashBoxById(token, boxId);

        CashRegister opening = repo
                .findTopByCashBoxIdAndCloseDateIsNullOrderByOpenDateDesc(boxId)
                .orElseThrow(() -> new IllegalStateException("No existe registro de apertura para caja " + boxId));

        LocalDateTime open = opening.getOpenDate();
        LocalDateTime close = LocalDateTime.now();

        Double sum = saleRepository.sumSalesByBoxBetween(boxId, open, close);
        double totalSales = (sum != null) ? sum : 0.0;

        opening.setCloseDate(close);
        opening.setTotalSales(totalSales);
        opening.setClosingAmount(closingAmount);

        CashRegister saved = repo.save(opening);
        box.setIsOpen(false);
        boxRepo.save(box);

        return saved;
    }

    public List<Map<String, Object>> getCashBoxHistory(
            String filter,
            String startDateStr,
            String endDateStr,
            String token,
            List<Long> boxIds
    ) {
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();

        LocalDateTime start, end;
        if (startDateStr != null && !startDateStr.isEmpty()
                && endDateStr != null && !endDateStr.isEmpty()) {
            start = LocalDate.parse(startDateStr).atStartOfDay();
            end   = LocalDate.parse(endDateStr).atTime(23, 59, 59);
        } else {
            start = getStartDate(filter);
            end   = LocalDateTime.now();
        }

        List<CashRegister> movs = repo.findOverlappingByBranch(branchId, start, end);

        if (boxIds != null && !boxIds.isEmpty()) {
            movs = movs.stream()
                    .filter(cr -> boxIds.contains(cr.getCashBox().getId()))
                    .toList();
        }

        return movs.stream()
                .sorted(Comparator.comparing(CashRegister::getOpenDate).reversed())
                .map(cr -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", cr.getId());
                    m.put("boxId", cr.getCashBox().getId());
                    m.put("code", cr.getCode());
                    // ⬇️ campos alineados con el front (openDate / closeDate)
                    m.put("openDate",  cr.getOpenDate().format(DATE_FORMAT));
                    m.put("closeDate", cr.getCloseDate() != null ? cr.getCloseDate().format(DATE_FORMAT) : NO_DATA);
                    m.put("initialAmount", cr.getInitialAmount());
                    m.put("closingAmount", cr.getClosingAmount());
                    m.put("totalSales", cr.getTotalSales());
                    m.put("isOpen", cr.getCloseDate() == null);
                    return m;
                })
                .toList();
    }
}
