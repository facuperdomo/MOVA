package com.movauy.mova.controller.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.service.finance.CashRegisterService;
import com.movauy.mova.service.sale.SaleService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final CashRegisterService cashRegisterService;

    /**
     * Registra una nueva venta si la caja está abierta. - Verifica el estado de
     * la caja con el token. - Registra la venta y devuelve su DTO de respuesta.
     */
    @PostMapping("/sales")
    public ResponseEntity<?> registerSale(
            @RequestHeader("Authorization") String token,
            @RequestBody SaleDTO saleDTO) {
        if (cashRegisterService.getOpenCashRegister(token).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No se pueden realizar ventas con la caja cerrada.");
        }
        try {
            Sale savedSale = saleService.registerSale(saleDTO, token);
            SaleResponseDTO response = saleService.toResponseDTO(savedSale);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al registrar la venta: " + e.getMessage()));
        }
    }
}
