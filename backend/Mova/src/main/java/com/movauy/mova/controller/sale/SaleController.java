package com.movauy.mova.controller.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.service.sale.SaleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.movauy.mova.service.finance.CashRegisterService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final CashRegisterService cashRegisterService;
    
    @PostMapping("/sales")
    public ResponseEntity<?> registerSale(@RequestBody SaleDTO saleDTO) {
        if (cashRegisterService.getOpenCashRegister().isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No se pueden realizar ventas con la caja cerrada.");
        }

        try {
            Sale savedSale = saleService.registerSale(saleDTO);
            return ResponseEntity.ok(savedSale);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al registrar la venta: " + e.getMessage());
        }
    }
}
