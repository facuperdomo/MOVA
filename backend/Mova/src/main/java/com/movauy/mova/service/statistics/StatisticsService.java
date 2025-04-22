package com.movauy.mova.service.statistics;

import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleItemRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CashRegisterRepository cashRegisterRepository;

    public List<Map<String, Object>> getSalesStatistics(String filter, Integer companyId) {
        LocalDateTime startDate = getStartDate(filter);

        // Usamos el nuevo método que compara con user.companyId (como string)
        List<Sale> sales = saleRepository.findByUserCompanyIdAndDateTimeAfter(companyId.toString(), startDate);

        System.out.println("📊 Ventas desde " + startDate + " para empresa (companyId) " + companyId + ": " + sales.size());

        return sales.stream().map(sale -> {
            Map<String, Object> result = new HashMap<>();
            result.put("id", sale.getId());
            result.put("date", sale.getDateTime().toLocalDate().toString().replace("-", "/"));
            result.put("total", sale.getTotalAmount());
            result.put("estado", sale.getEstado().name());
            return result;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopSellingProducts(String filter, Integer companyId) {
        LocalDateTime startDate = getStartDate(filter);
        List<Map<String, Object>> products = saleItemRepository.findTopSellingProductsByCompany(startDate, companyId.toString());
        System.out.println("📦 Productos más vendidos (" + filter + ") para empresa " + companyId + ": " + products.size());
        return products != null ? products : Collections.emptyList();
    }

    public List<Map<String, Object>> getCashRegisterHistory(String filter, Integer companyId) {
        LocalDateTime startDate = getStartDate(filter);
        List<CashRegister> registers = cashRegisterRepository.findCashRegisterAfterByCompany(companyId.toString(), startDate);
        System.out.println("💰 Cajas desde " + startDate + " para empresa " + companyId + ": " + registers.size());

        return registers.stream().map(register -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", register.getId());
            map.put("openDate", register.getOpenDate().toString().replace("T", " ").replace("-", "/"));
            map.put("closeDate", register.getCloseDate() != null
                    ? register.getCloseDate().toString().replace("T", " ").replace("-", "/")
                    : "No cerrada");
            map.put("initialAmount", register.getInitialAmount() != 0.0 ? String.valueOf(register.getInitialAmount()) : "Sin datos");
            map.put("totalSales", register.getTotalSales() != 0.0 ? String.valueOf(register.getTotalSales()) : "Sin datos");
            map.put("isOpen", register.isOpen());
            return map;
        }).collect(Collectors.toList());
    }

    public int countSaleItems(Integer companyId) {
        return saleItemRepository.countTotalSaleItemsByCompany(companyId);
    }

    private LocalDateTime getStartDate(String filter) {
        LocalDateTime now = LocalDateTime.now();
        switch (filter) {
            case "day":
                return now.truncatedTo(ChronoUnit.DAYS);
            case "week":
                return now.minusWeeks(1).truncatedTo(ChronoUnit.DAYS);
            case "month":
                return now.minusMonths(1).truncatedTo(ChronoUnit.DAYS);
            case "year":
                return now.minusYears(1).truncatedTo(ChronoUnit.DAYS);
            default:
                return now.minusMonths(1).truncatedTo(ChronoUnit.DAYS);
        }
    }

    @Transactional
    public void cancelSale(Long saleId, String companyId) {
        if (saleId == null) {
            throw new IllegalArgumentException("El ID de la venta no puede ser null.");
        }

        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));

        if (!companyId.equals(sale.getUser().getCompanyId())) {
            throw new SecurityException("No tienes permiso para cancelar esta venta.");
        }

        System.out.println("❌ Cancelando venta con ID: " + saleId + " para empresa con companyId: " + companyId);
        saleRepository.cancelSaleByCompany(saleId, companyId);
    }

}
