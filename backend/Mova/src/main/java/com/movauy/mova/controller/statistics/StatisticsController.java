package com.movauy.mova.controller.statistics;

import com.movauy.mova.service.statistics.StatisticsService;
import com.movauy.mova.service.user.AuthService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

    /**
     * Obtiene estadísticas de ventas para la empresa actual, filtradas por
     * día/semana/mes/año.
     */
    @GetMapping("/sales")
    public ResponseEntity<List<Map<String, Object>>> getSalesStatistics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter) {
        Long companyId = authService.getCompanyIdFromToken(token);
        System.out.println("📊 GET /sales called - companyId: " + companyId + ", filter: " + filter);
        List<Map<String, Object>> sales = statisticsService.getSalesStatistics(filter, companyId.intValue());
        return ResponseEntity.ok(sales);
    }

    /**
     * Devuelve el listado de productos (tragos) más vendidos.
     */
    @GetMapping("/top-selling-products")
    public ResponseEntity<List<Map<String, Object>>> getTopSellingProducts(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter) {
        Long companyId = authService.getCompanyIdFromToken(token);
        List<Map<String, Object>> products = statisticsService.getTopSellingProducts(filter, companyId.intValue());
        return products.isEmpty() ? ResponseEntity.ok(Collections.emptyList()) : ResponseEntity.ok(products);
    }

    /**
     * Historial de cajas abiertas/cerradas con sus ventas.
     */
    @GetMapping("/cash-register-history")
    public ResponseEntity<List<Map<String, Object>>> getCashRegisterHistory(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter) {
        Long companyId = authService.getCompanyIdFromToken(token);
        List<Map<String, Object>> history = statisticsService.getCashRegisterHistory(filter, companyId.intValue());
        return history.isEmpty() ? ResponseEntity.ok(Collections.emptyList()) : ResponseEntity.ok(history);
    }

    /**
     * Cantidad total de ítems vendidos (para mostrar conteos rápidos).
     */
    @GetMapping("/count-sale-items")
    public ResponseEntity<Integer> countSaleItems(@RequestHeader("Authorization") String token) {
        Long companyId = authService.getCompanyIdFromToken(token);
        int count = statisticsService.countSaleItems(companyId.intValue());
        return ResponseEntity.ok(count);
    }

    /**
     * Marca una venta como cancelada.
     */
    @PutMapping("/cancel-sale/{id}")
    public ResponseEntity<Void> cancelarVenta(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long companyId = authService.getCompanyIdFromToken(token);
        statisticsService.cancelSale(id, companyId.toString());
        return ResponseEntity.ok().build();
    }
}
