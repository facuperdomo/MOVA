package com.movauy.mova.service.statistics;

import com.movauy.mova.dto.BranchStatisticsDTO;
import com.movauy.mova.dto.CompanyStatisticsDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.finance.CashBox;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleItemRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.finance.CashBoxService;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CashBoxService cashBoxService;
    private final AuthService authService;
    private final BranchRepository branchRepository;
    private final CashRegisterRepository cashRegisterRepository;

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

    // StatisticsService.java
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
            end = LocalDate.parse(endDateStr).atTime(23, 59, 59);
        } else {
            start = getStartDate(filter);
            end = LocalDateTime.now();
        }

        // Trae TODOS los movimientos que tocan el rango
        List<CashRegister> movs = cashRegisterRepository.findOverlappingByBranch(branchId, start, end);

        // Filtro por cajas seleccionadas (si aplica)
        if (boxIds != null && !boxIds.isEmpty()) {
            movs = movs.stream()
                    .filter(cr -> boxIds.contains(cr.getCashBox().getId()))
                    .toList();
        }

        // Un item por movimiento (NADA de agrupar por caja)
        return movs.stream()
                .sorted(Comparator.comparing(CashRegister::getOpenDate).reversed())
                .map(cr -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", cr.getId());                        // ← importante para el key del front
                    m.put("boxId", cr.getCashBox().getId());
                    m.put("code", cr.getCode());
                    m.put("openedAt", cr.getOpenDate().format(DATE_FORMAT)); // el front usa openedAt
                    m.put("closedAt", cr.getCloseDate() != null
                            ? cr.getCloseDate().format(DATE_FORMAT) : NO_DATA);
                    m.put("initialAmount", cr.getInitialAmount());
                    m.put("closingAmount", cr.getClosingAmount());
                    m.put("totalSales", cr.getTotalSales());
                    m.put("isOpen", cr.getCloseDate() == null);
                    return m;
                })
                .toList();
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

    public CompanyStatisticsDTO getStatisticsByBranch(
            Long branchId,
            String filter,
            String startDateStr,
            String endDateStr,
            List<Long> boxIds
    ) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));

        LocalDateTime startDate, endDate;
        if (startDateStr != null && endDateStr != null) {
            startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
            endDate = LocalDateTime.parse(endDateStr + "T23:59:59");
        } else {
            startDate = getStartDate(filter);
            endDate = LocalDateTime.now();
        }

        List<Sale> sales;
        if (boxIds != null && !boxIds.isEmpty()) {
            sales = saleRepository
                    .findByBranch_IdAndCashBox_IdInAndDateTimeBetween(
                            branchId, boxIds, startDate, endDate);
        } else {
            sales = saleRepository
                    .findByBranch_IdAndDateTimeBetween(branchId, startDate, endDate);
        }

        return buildCompanyStatistics(sales, List.of(branch));
    }

    public List<Map<String, Object>> getTopSellingProductsByBranch(
            Long branchId,
            String filter,
            String startDateStr,
            String endDateStr,
            List<Long> boxIds,
            Long cashRegisterId
    ) {
        // Si viene una apertura de caja, usar su período y su caja
        if (cashRegisterId != null) {
            var cr = cashRegisterRepository
                    .findByIdAndBranch_Id(cashRegisterId, branchId) // o findByIdAndCashBox_Branch_Id(...)
                    .orElseThrow(() -> new NoSuchElementException(
                    "Período de caja no encontrado o no pertenece a la sucursal"));

            var from = cr.getOpenDate();                                     // 👈 nombres reales de tu entidad
            var to = (cr.getCloseDate() != null) ? cr.getCloseDate() : LocalDateTime.now();
            Long onlyBoxId = cr.getCashBox().getId();

            List<Object[]> rows = saleItemRepository.findTopSellingProductsByBranchAndBoxIds(
                    from, to, branchId, List.of(onlyBoxId));

            return rows.stream()
                    .map(r -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("name", (String) r[0]);
                        m.put("quantity", ((Number) r[1]).intValue());
                        return m;
                    })
                    .collect(Collectors.toList());
        }

        // === Lógica existente cuando NO hay cashRegisterId ===
        LocalDateTime startDate, endDate;
        if (startDateStr != null && endDateStr != null) {
            startDate = LocalDateTime.parse(startDateStr + "T00:00:00");
            endDate = LocalDateTime.parse(endDateStr + "T23:59:59");
        } else {
            startDate = getStartDate(filter);
            endDate = LocalDateTime.now();
        }

        List<Object[]> rows;
        if (boxIds != null && !boxIds.isEmpty()) {
            rows = saleItemRepository.findTopSellingProductsByBranchAndBoxIds(
                    startDate, endDate, branchId, boxIds);
        } else {
            rows = saleItemRepository.findTopSellingProductsByBranch(
                    startDate, endDate, branchId);
        }

        return rows.stream()
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", (String) r[0]);
                    m.put("quantity", ((Number) r[1]).intValue());
                    return m;
                })
                .collect(Collectors.toList());
    }

    /**
     * Devuelve listado de ventas (id, date, total, estado) para una sucursal y,
     * opcionalmente, filtradas por boxIds.
     */
    public List<Map<String, Object>> getSalesByBranch(
            Long branchId,
            String filter,
            String startDateStr,
            String endDateStr,
            List<Long> boxIds,
            Long cashRegisterId
    ) {
        if (cashRegisterId != null) {
            var cr = cashRegisterRepository
                    .findByIdAndBranch_Id(cashRegisterId, branchId)
                    .orElseThrow(() -> new NoSuchElementException(
                    "Período de caja no encontrado o no pertenece a la sucursal"));

            var from = cr.getOpenDate();
            var to = (cr.getCloseDate() != null) ? cr.getCloseDate() : LocalDateTime.now(); // abierta => hasta ahora

            // Forzamos la caja del período para no mezclar ventas de otras cajas
            Long onlyBoxId = cr.getCashBox().getId();

            return saleRepository.aggregateSales(branchId, List.of(onlyBoxId), from, to);
        }

        // 1) parsear rango
        LocalDateTime start, end;
        if (startDateStr != null && endDateStr != null) {
            start = LocalDateTime.parse(startDateStr + "T00:00:00");
            end = LocalDateTime.parse(endDateStr + "T23:59:59");
        } else {
            start = getStartDate(filter);
            end = LocalDateTime.now();
        }

        // 2) obtener ventas con o sin filtro de cajas
        List<Sale> sales;
        if (boxIds != null && !boxIds.isEmpty()) {
            sales = saleRepository
                    .findByBranch_IdAndCashBox_IdInAndDateTimeBetween(branchId, boxIds, start, end);
        } else {
            sales = saleRepository
                    .findByBranch_IdAndDateTimeBetween(branchId, start, end);
        }

        // 3) mapear a List<Map<String,Object>>
        return sales.stream().map(sale -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", sale.getId());
            m.put("date", sale.getDateTime().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")));
            m.put("total", sale.getTotalAmount());
            m.put("estado", sale.getEstado().name());
            return m;
        }).collect(Collectors.toList());
    }
}
