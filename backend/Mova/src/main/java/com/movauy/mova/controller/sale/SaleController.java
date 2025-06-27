// src/main/java/com/movauy/mova/controller/sale/SaleController.java
package com.movauy.mova.controller.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.model.finance.CashBox;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.service.finance.CashBoxService;
import com.movauy.mova.service.finance.CashRegisterService;
import com.movauy.mova.service.sale.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SaleController {

    private final SaleService saleService;
    private final CashBoxService cashBoxService;
    private final CashRegisterService cashRegisterService;
    private static final Logger log = LoggerFactory.getLogger(SaleController.class);

    /**
     * Registra una nueva venta si la caja está abierta. Asigna cashBoxId al DTO
     * y luego registra la venta + el movimiento en la caja.
     */
    @PostMapping("/sales")
    public ResponseEntity<?> registerSale(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody SaleDTO saleDTO
    ) {
        log.debug("→ registerSale recibida, DTO: {}", saleDTO);
        String token = authHeader.replace("Bearer ", "");

        // 1) Detectar la caja abierta y asignada al usuario
        CashBox box;
        try {
            log.debug("→ registerSale: detectando caja abierta asignada al usuario…");
            box = cashBoxService.getOpenCashBoxForUser(token);
            log.debug("✔ Caja asignada encontrada: id={} código={}", box.getId(), box.getCode());
        } catch (ResponseStatusException ex) {
            log.warn("✘ No hay caja abierta asignada: {}", ex.getReason());
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason()));
        }

        // 2) Inyectar el cashBoxId en el DTO
        saleDTO.setCashBoxId(box.getId());
        log.debug("→ cashBoxId inyectado en DTO: {}", saleDTO.getCashBoxId());

        // 3) Delegar en el servicio de ventas
        try {
            var responseDto = saleService.registerSale(saleDTO, token);
            log.info("✔ Venta registrada: id={} total={}", responseDto.getId(), responseDto.getTotalAmount());
            return ResponseEntity.ok(responseDto);
        } catch (ResponseStatusException ex) {
            // Pasar adelante errores de validación
            log.warn("✘ Error en registro de venta: {}", ex.getReason());
            return ResponseEntity
                    .status(ex.getStatusCode())
                    .body(Map.of("error", ex.getReason()));
        } catch (Exception e) {
            log.error("❌ Excepción inesperada al registrar la venta", e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error al registrar la venta: " + e.getMessage()));
        }
    }
}
