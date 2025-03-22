package com.movauy.mova.controller.statistics;

import com.movauy.mova.service.statistics.StatisticsService;
import com.movauy.mova.service.user.AuthService;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class StatisticsController {

    private final StatisticsService statisticsService;
    private final AuthService authService;
    
    @GetMapping("/sales")
    public ResponseEntity<List<Map<String, Object>>> getSalesStatistics(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter) {
        Long companyId = authService.getCompanyIdFromToken(token);
        return ResponseEntity.ok(statisticsService.getSalesStatistics(filter, companyId.intValue()));
    }

    @GetMapping("/top-selling-drinks")
    public ResponseEntity<List<Map<String, Object>>> getTopSellingDrinks(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter) {
        Long companyId = authService.getCompanyIdFromToken(token);
        List<Map<String, Object>> drinks = statisticsService.getTopSellingDrinks(filter, companyId.intValue());
        return drinks.isEmpty() ? ResponseEntity.ok(Collections.emptyList()) : ResponseEntity.ok(drinks);
    }

    @GetMapping("/cash-register-history")
    public ResponseEntity<List<Map<String, Object>>> getCashRegisterHistory(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String filter) {
        Long companyId = authService.getCompanyIdFromToken(token);
        List<Map<String, Object>> history = statisticsService.getCashRegisterHistory(filter, companyId.intValue());
        return history.isEmpty() ? ResponseEntity.ok(Collections.emptyList()) : ResponseEntity.ok(history);
    }

    @GetMapping("/count-sale-items")
    public ResponseEntity<Integer> countSaleItems(@RequestHeader("Authorization") String token) {
        Long companyId = authService.getCompanyIdFromToken(token);
        int count = statisticsService.countSaleItems(companyId.intValue());
        return ResponseEntity.ok(count);
    }

    @PutMapping("/cancel-sale/{id}")
    public ResponseEntity<Void> cancelarVenta(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        Long companyId = authService.getCompanyIdFromToken(token);
        statisticsService.cancelarVenta(id, companyId.intValue());
        return ResponseEntity.ok().build();
    }
}
