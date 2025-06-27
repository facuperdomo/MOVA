package com.movauy.mova.controller.statistics;

import com.movauy.mova.dto.CompanyStatisticsDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.service.sale.SaleService;
import com.movauy.mova.service.statistics.StatisticsService;
import com.movauy.mova.service.user.AuthService;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@CrossOrigin(origins = {
    "https://movauy.top:8443",
    "http://localhost:3000"
})
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final AuthService authService;
    private final SaleService saleService;

    /**
     * Devuelve estadísticas de ventas filtradas por día, semana, mes o año. Se
     * basa en la sucursal obtenida desde el token.
     */
    @GetMapping("/sales")
    public ResponseEntity<List<Map<String, Object>>> getSalesStatistics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<Map<String, Object>> sales
                = statisticsService.getSalesStatistics(filter, startDate, endDate, token);
        return ResponseEntity.ok(sales);
    }

    /**
     * Devuelve los productos más vendidos en el período seleccionado
     * (día/semana/etc). Útil para generar rankings de ventas.
     */
    @GetMapping("/top-selling-products")
    public ResponseEntity<List<Map<String, Object>>> getTopSellingProducts(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<Map<String, Object>> products
                = statisticsService.getTopSellingProducts(filter, startDate, endDate, token);
        return ResponseEntity.ok(products);
    }

    /**
     * Lista la historia de apertura y cierre de cajas, con montos y fechas.
     * Muestra también el estado actual (abierta o cerrada).
     */
    @GetMapping("/cash-box-history")
    public ResponseEntity<List<Map<String, Object>>> getCashBoxHistory(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        List<Map<String, Object>> history
                = statisticsService.getCashBoxHistory(filter, startDate, endDate, token);
        return ResponseEntity.ok(history);
    }

    /**
     * Devuelve la cantidad total de ítems vendidos. Útil para mostrar en
     * dashboards o KPIs rápidos.
     */
    @GetMapping("/count-sale-items")
    public ResponseEntity<Integer> countSaleItems(
            @RequestHeader("Authorization") String token) {
        int count = statisticsService.countSaleItems(token);
        return ResponseEntity.ok(count);
    }

    /**
     * Marca una venta específica como cancelada. Verifica que la venta
     * pertenezca a la misma sucursal del usuario.
     */
    @PutMapping("/cancel-sale/{id}")
    public ResponseEntity<Void> cancelarVenta(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        statisticsService.cancelSale(id, token);
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/by-company/{companyId}")
    public CompanyStatisticsDTO getStatisticsByCompany(
            @PathVariable Long companyId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return statisticsService.getStatisticsByCompany(companyId, filter, startDate, endDate);
    }

    @PreAuthorize("hasRole('SUPERADMIN')")
    @GetMapping("/by-branch/{branchId}")
    public CompanyStatisticsDTO getStatisticsByBranch(
            @PathVariable Long branchId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return statisticsService.getStatisticsByBranch(branchId, filter, startDate, endDate);
    }

    @GetMapping("/by-branch/{branchId}/top-products")
    public List<Map<String, Object>> getTopProductsByBranch(
            @PathVariable Long branchId,
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        return statisticsService.getTopSellingProductsByBranch(branchId, filter, startDate, endDate);
    }

    @GetMapping("/sales/{id}")
    public ResponseEntity<?> getSaleById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(saleService.getById(id));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
