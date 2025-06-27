// src/main/java/com/movauy/mova/controller/admin/CashRegisterController.java
package com.movauy.mova.controller.admin;

import com.movauy.mova.dto.CashRegisterDTO;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.service.finance.CashRegisterService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/cash-register")
@RequiredArgsConstructor
@CrossOrigin(origins = "https://movauy.top:8443")
public class CashRegisterController {

    private final CashRegisterService cashRegisterService;

    @Data
    static class MovementRequest {

        private Long boxId;
        private Double amount;
    }

    /**
     * Registra el movimiento de apertura de caja.
     */
    @PostMapping("/open")
    public ResponseEntity<String> open(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody MovementRequest req
    ) {
        String token = authHeader.replace("Bearer ", "");
        cashRegisterService.registerOpening(token, req.getBoxId(), req.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body("Apertura registrada");
    }

    /**
     * Registra el movimiento de cierre de caja.
     */
    @PostMapping("/close")
    public ResponseEntity<String> close(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody MovementRequest req
    ) {
        String token = authHeader.replace("Bearer ", "");
        cashRegisterService.registerClosing(token, req.getBoxId(), req.getAmount());
        return ResponseEntity.ok("Cierre registrado");
    }

    /**
     * Devuelve el historial de movimientos de la caja indicada.
     */
    @GetMapping("/history")
    public ResponseEntity<List<CashRegisterDTO>> history(
            @RequestParam("boxId") Long boxId
    ) {
        List<CashRegister> list = cashRegisterService.getHistory(boxId);
        List<CashRegisterDTO> dtos = list.stream()
                .map(CashRegisterDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * ¿Tiene el usuario alguna caja ABIERTA asignada?
     */
    @GetMapping("/status")
    public ResponseEntity<Boolean> isCashRegisterOpenForUser(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        // listCashRegisters(token, true) filtra solo las abiertas Y asignadas al usuario
        boolean anyOpenAssigned = cashRegisterService.listCashRegisters(token, true).size() > 0;
        return ResponseEntity.ok(anyOpenAssigned);
    }
}
