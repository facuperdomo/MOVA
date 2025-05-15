package com.movauy.mova.service.statistics;

import com.movauy.mova.dto.BranchStatisticsDTO;
import com.movauy.mova.dto.CompanyStatisticsDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleItemRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final AuthService authService;
    private final BranchRepository branchRepository;

    private static final String NO_DATA = "Sin datos";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private static final Map<DayOfWeek, String> DIAS_SEMANA_ES = Map.of(
            DayOfWeek.MONDAY, "Lunes",
            DayOfWeek.TUESDAY, "Martes",
            DayOfWeek.WEDNESDAY, "Miércoles",
            DayOfWeek.THURSDAY, "Jueves",
            DayOfWeek.FRIDAY, "Viernes",
            DayOfWeek.SATURDAY, "Sábado",
            DayOfWeek.SUNDAY, "Domingo"
    );

    public List<Map<String, Object>> getSalesStatistics(String filter, String startDateStr, String endDateStr, String token) {
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();
        LocalDateTime start;
        LocalDateTime end;

        if (startDateStr != null && !startDateStr.isEmpty() && endDateStr != null && !endDateStr.isEmpty()) {
            start = LocalDate.parse(startDateStr).atStartOfDay();
            end = LocalDate.parse(endDateStr).atTime(23, 59, 59);
        } else {
            start = getStartDate(filter);
            end = LocalDateTime.now();
        }

        List<Sale> sales = saleRepository.findByBranch_IdAndDateTimeBetween(branchId, start, end);

        return sales.stream().map(sale -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", sale.getId());
            m.put("date", sale.getDateTime().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")));
            m.put("total", sale.getTotalAmount());
            m.put("estado", sale.getEstado().name());
            return m;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopSellingProducts(String filter, String startDateStr, String endDateStr, String token) {
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();
        LocalDateTime start;
        LocalDateTime end;

        if (startDateStr != null && !startDateStr.isEmpty() && endDateStr != null && !endDateStr.isEmpty()) {
            start = LocalDate.parse(startDateStr).atStartOfDay();
            end = LocalDate.parse(endDateStr).atTime(23, 59, 59);
        } else {
            start = getStartDate(filter);
            end = LocalDateTime.now();
        }

        List<Object[]> rows = saleItemRepository.findTopSellingProductsByBranch(start, end, branchId);

        if (rows == null) {
            return Collections.emptyList();
        }

        return rows.stream()
                .map(row -> {
                    Object[] cols = (Object[]) row;
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", cols[0]);
                    map.put("quantity", ((Number) cols[1]).intValue());
                    return map;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getCashRegisterHistory(String filter, String startDateStr, String endDateStr, String token) {
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();
        LocalDateTime start;
        LocalDateTime end;

        if (startDateStr != null && !startDateStr.isEmpty() && endDateStr != null && !endDateStr.isEmpty()) {
            start = LocalDate.parse(startDateStr).atStartOfDay();
            end = LocalDate.parse(endDateStr).atTime(23, 59, 59);
        } else {
            start = getStartDate(filter);
            end = LocalDateTime.now();
        }

        List<CashRegister> regs = cashRegisterRepository
                .findByBranch_IdAndOpenDateBetweenOrderByOpenDateDesc(branchId, start, end);

        return regs.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("openDate", c.getOpenDate().format(DATE_FORMAT));
            m.put("closeDate", c.getCloseDate() != null
                    ? c.getCloseDate().format(DATE_FORMAT)
                    : NO_DATA);
            m.put("initialAmount", c.getInitialAmount() != 0.0
                    ? String.valueOf(c.getInitialAmount())
                    : NO_DATA);
            m.put("totalSales", c.getTotalSales() != 0.0
                    ? String.valueOf(c.getTotalSales())
                    : NO_DATA);
            m.put("isOpen", c.isOpen());
            return m;
        }).collect(Collectors.toList());
    }

    public int countSaleItems(String token) {
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();
        return saleItemRepository.countTotalSaleItemsByBranch(branchId);
    }

    @Transactional
    public void cancelSale(Long saleId, String token) {
        if (saleId == null) {
            throw new IllegalArgumentException("El ID de la venta no puede ser null.");
        }

        Long branchId = authService.getUserBasicFromToken(token).getBranchId();

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));

        if (!branchId.equals(sale.getBranch().getId())) {
            throw new SecurityException("No tienes permiso para cancelar esta venta.");
        }

        saleRepository.cancelSaleByBranch(saleId, branchId);
    }

    private LocalDateTime getStartDate(String filter) {
        LocalDateTime now = LocalDateTime.now();
        return switch (filter) {
            case "day" ->
                now.truncatedTo(ChronoUnit.DAYS);
            case "week" ->
                now.minusWeeks(1).truncatedTo(ChronoUnit.DAYS);
            case "month" ->
                now.minusMonths(1).truncatedTo(ChronoUnit.DAYS);
            case "year" ->
                now.minusYears(1).truncatedTo(ChronoUnit.DAYS);
            default ->
                now.minusMonths(1).truncatedTo(ChronoUnit.DAYS);
        };
    }

    public CompanyStatisticsDTO getStatisticsByCompany(Long companyId, String filter, String startDateStr, String endDateStr) {
        List<Branch> branches = branchRepository.findByCompanyId(companyId);

        if (branches.isEmpty()) {
            return new CompanyStatisticsDTO(
                    0, BigDecimal.ZERO, 0, List.of(),
                    BigDecimal.ZERO, 0, BigDecimal.ZERO,
                    0, "Sin datos", Map.of()
            );
        }

        LocalDateTime startDate;
        LocalDateTime endDate;

        // Si se proporcionan fechas personalizadas
        if (startDateStr != null && endDateStr != null) {
            try {
                startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
                endDate = LocalDateTime.parse(endDateStr + "T23:59:59");
            } catch (Exception e) {
                throw new IllegalArgumentException("Fechas inválidas");
            }
        } else {
            startDate = getStartDate(filter);
            endDate = LocalDateTime.now();
        }

        List<Sale> allSales = saleRepository.findByBranchInAndDateTimeBetween(branches, startDate, endDate);

        return buildCompanyStatistics(allSales, branches);
    }

    public CompanyStatisticsDTO buildCompanyStatistics(List<Sale> sales, List<Branch> branches) {
        int totalSalesCount = sales.size();

        // Agrupar ventas por sucursal
        Map<Long, List<Sale>> salesByBranch = sales.stream()
                .collect(Collectors.groupingBy(sale -> sale.getBranch().getId()));

        // Detalle por sucursal
        List<BranchStatisticsDTO> branchStats = branches.stream().map(branch -> {
            List<Sale> branchSales = salesByBranch.getOrDefault(branch.getId(), List.of());

            int branchSaleCount = branchSales.size();
            BigDecimal branchRevenue = branchSales.stream()
                    .map(s -> BigDecimal.valueOf(s.getTotalAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int branchProductsSold = branchSales.stream()
                    .flatMap(s -> s.getItems().stream())
                    .mapToInt(i -> i.getQuantity())
                    .sum();

            return new BranchStatisticsDTO(
                    branch.getId(),
                    branch.getName(),
                    branchSaleCount,
                    branchRevenue,
                    branchProductsSold
            );
        }).toList();

        // Total de ingresos y productos
        BigDecimal totalRevenue = branchStats.stream()
                .map(BranchStatisticsDTO::getTotalRevenue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalProductsSold = branchStats.stream()
                .mapToInt(BranchStatisticsDTO::getTotalProductsSold)
                .sum();

        // Nuevas métricas
        BigDecimal averageTicket = totalSalesCount > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalSalesCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int activeBranchesCount = (int) salesByBranch.values().stream()
                .filter(list -> !list.isEmpty())
                .count();

        BigDecimal averageSalesPerBranch = activeBranchesCount > 0
                ? BigDecimal.valueOf((double) totalSalesCount / activeBranchesCount).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        int cancelledSalesCount = (int) sales.stream()
                .filter(s -> s.getEstado().name().equals("CANCELADA"))
                .count();

        // Día con más ventas
        String topDayOfWeek = sales.stream()
                .map(s -> s.getDateTime().getDayOfWeek())
                .collect(Collectors.groupingBy(d -> d, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(e -> DIAS_SEMANA_ES.getOrDefault(e.getKey(), "Sin datos"))
                .orElse("Sin datos");

        // Ingresos por categoría
        Map<String, BigDecimal> revenueByCategory = new HashMap<>();
        for (Sale s : sales) {
            for (var item : s.getItems()) {
                String cat = item.getProduct() != null && item.getProduct().getCategory() != null
                        ? item.getProduct().getCategory().getName()
                        : "Sin categoría";
                BigDecimal revenue = BigDecimal.valueOf(item.getProduct().getPrice())
                        .multiply(BigDecimal.valueOf(item.getQuantity()));
                revenueByCategory.merge(cat, revenue, BigDecimal::add);
            }
        }

        return new CompanyStatisticsDTO(
                totalSalesCount,
                totalRevenue,
                totalProductsSold,
                branchStats,
                averageTicket,
                activeBranchesCount,
                averageSalesPerBranch,
                cancelledSalesCount,
                topDayOfWeek,
                revenueByCategory
        );
    }

    public CompanyStatisticsDTO getStatisticsByBranch(Long branchId, String filter, String startDateStr, String endDateStr) {
        Optional<Branch> branchOpt = branchRepository.findById(branchId);
        if (branchOpt.isEmpty()) {
            throw new IllegalArgumentException("Sucursal no encontrada");
        }

        Branch branch = branchOpt.get();
        LocalDateTime startDate, endDate;

        if (startDateStr != null && endDateStr != null) {
            startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
            endDate = LocalDateTime.parse(endDateStr + "T23:59:59");
        } else {
            startDate = getStartDate(filter);
            endDate = LocalDateTime.now();
        }

        List<Sale> sales = saleRepository.findByBranchInAndDateTimeBetween(List.of(branch), startDate, endDate);
        return buildCompanyStatistics(sales, List.of(branch));
    }

    public List<Map<String, Object>> getTopSellingProductsByBranch(Long branchId, String filter, String startDateStr, String endDateStr) {
        LocalDateTime startDate;
        LocalDateTime endDate;

        if (startDateStr != null && endDateStr != null) {
            try {
                startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
                endDate = LocalDateTime.parse(endDateStr + "T23:59:59");
            } catch (Exception e) {
                throw new IllegalArgumentException("Fechas inválidas");
            }
        } else {
            startDate = getStartDate(filter);
            endDate = LocalDateTime.now();
        }

        // Consultar productos vendidos en ese rango
        return saleItemRepository.findTopSellingProductsByBranch(startDate, endDate, branchId)
                .stream()
                .map(row -> {
                    Object[] cols = (Object[]) row;
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", cols[0]);
                    map.put("quantity", ((Number) cols[1]).intValue());
                    return map;
                }).toList();
    }

}
