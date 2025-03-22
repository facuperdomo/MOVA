package com.movauy.mova.service.statistics;

import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.sale.SaleItemRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;
    private final CashRegisterRepository cashRegisterRepository;

    public List<Map<String, Object>> getSalesStatistics(String filter, Integer companyId) {
        LocalDateTime startDate = getStartDate(filter);
        // Se filtran las ventas por empresa y fecha
        List<Sale> sales = saleRepository.findByCompanyIdAndDateTimeAfter(companyId, startDate);
        System.out.println("📊 Ventas obtenidas desde " + startDate + " para empresa " + companyId + ": " + sales);
        if (sales.isEmpty()) {
            return List.of();
        }
        return sales.stream()
                .map(sale -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", sale.getId());
                    result.put("date", sale.getDateTime().toLocalDate().toString().replace("-", "/"));
                    result.put("total", sale.getTotalAmount());
                    result.put("estado", sale.getEstado().name());
                    return result;
                })
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopSellingDrinks(String filter, Integer companyId) {
        LocalDateTime startDate = getStartDate(filter);
        // Se asume que el repositorio tiene un método para obtener tragos vendidos filtrado por empresa
        List<Map<String, Object>> drinks = saleItemRepository.findTopSellingDrinksByCompany(startDate, companyId);
        if (drinks == null) {
            return Collections.emptyList();
        }
        System.out.println("🍹 Datos de tragos vendidos (" + filter + ") para empresa " + companyId + ": " + drinks);
        return drinks;
    }

    public List<Map<String, Object>> getCashRegisterHistory(String filter, Integer companyId) {
        LocalDateTime startDate = getStartDate(filter);
        // Se asume que el repositorio filtra las cajas por empresa y fecha
        List<CashRegister> registers = cashRegisterRepository.findCashRegisterAfterByCompany(companyId, startDate);
        System.out.println("💰 Historial de cajas obtenidas para empresa " + companyId + ": " + registers);
        return registers.stream().map(register -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", register.getId());
            map.put("openDate", register.getOpenDate().toString().replace("T", " ").replace("-", "/"));
            map.put("closeDate", register.getCloseDate() != null ? register.getCloseDate().toString().replace("T", " ").replace("-", "/") : "No cerrada");
            map.put("initialAmount", register.getInitialAmount() != 0.0 ? String.valueOf(register.getInitialAmount()) : "Sin datos");
            map.put("totalSales", register.getTotalSales() != 0.0 ? String.valueOf(register.getTotalSales()) : "Sin datos");
            map.put("isOpen", register.isOpen());
            return map;
        }).collect(Collectors.toList());
    }

    public int countSaleItems(Integer companyId) {
        // Se asume que el repositorio cuenta los items de venta filtrando por empresa
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
    public void cancelarVenta(Long ventaId, Integer companyId) {
        if (ventaId == null) {
            throw new IllegalArgumentException("El ID de la venta no puede ser null.");
        }
        // Se verifica que la venta pertenezca a la empresa autenticada.
        Sale sale = saleRepository.findById(ventaId)
                .orElseThrow(() -> new IllegalArgumentException("Venta no encontrada"));
        // Suposición: la entidad Sale posee una propiedad 'company' (o similar) que identifica a la empresa.
        if (!sale.getUser().getId().equals(companyId)) {
            throw new SecurityException("No tienes permiso para cancelar esta venta.");
        }
        System.out.println("❌ Cancelando venta con ID: " + ventaId + " para empresa " + companyId);
        saleRepository.cancelarVenta(ventaId, companyId);
    }
}
