package com.movauy.mova.controller.admin;

import com.movauy.mova.dto.CashBoxDTO;
import com.movauy.mova.dto.CashBoxStatusDTO;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.finance.CashBox;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.user.User;
import com.movauy.mova.service.finance.CashBoxService;
import com.movauy.mova.service.finance.CashRegisterService;
import java.util.HashSet;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/cash-box")
@RequiredArgsConstructor
@CrossOrigin(origins = "https://movauy.top:8443")
public class CashBoxController {

    private final CashBoxService cashBoxService;
    private final CashRegisterService cashRegisterService;
    private static final Logger log = LoggerFactory.getLogger(CashBoxController.class);

    @Data
    static class OpenCashBoxRequest {

        String code;
        double initialAmount;
    }

    @Data
    static class CloseCashBoxRequest {

        String code;
        double closingAmount;
    }

    @Data
    static class CreateCashBoxRequest {

        String code;
        String name;
    }

    /**
     * —————— ESTADO: ¿Hay alguna caja abierta? ——————
     */
    @GetMapping("/status")
    public ResponseEntity<Boolean> isAnyOpen(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        boolean anyOpen = cashBoxService.listCashBoxes(token, true).size() > 0;
        return ResponseEntity.ok(anyOpen);
    }

    /**
     * —————— CREAR CAJA (sin abrir) ——————
     */
    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CreateCashBoxRequest req
    ) {
        String token = authHeader.replace("Bearer ", "");

        // 1) Validación del tope de cajas creadas
        if (!cashBoxService.canCreateNewCashBox(token)) {
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "error", "MaxBoxesReached",
                            "message", "Has alcanzado el máximo de cajas que tu plan permite crear."
                    ));
        }

        // 2) Si quedó espacio, creamos la caja
        CashBox box = cashBoxService.createCashBox(token, req.getCode(), req.getName());
        CashBoxDTO dto = new CashBoxDTO(box.getId(), box.getCode(), box.getName(), box.getIsOpen(), box.getEnabled());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    /**
     * —————— ABRIR CAJA ——————
     */
    @PostMapping("/open")
    public ResponseEntity<?> open(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody OpenCashBoxRequest req
    ) {
        String token = authHeader.replace("Bearer ", "");

        CashBox box = cashBoxService.getByCode(token, req.getCode());
        cashRegisterService.registerOpening(token, box.getId(), req.getInitialAmount());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Caja '" + box.getCode() + "' abierta.");
    }

    /**
     * —————— CERRAR CAJA ——————
     */
    @PostMapping("/close")
    public ResponseEntity<Map<String, Double>> close(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody CloseCashBoxRequest req
    ) {
        String token = authHeader.replace("Bearer ", "");
        CashBox box = cashBoxService.getByCode(token, req.getCode());
        // <-- aquí recibes el CashRegister con totalSales e initialAmount
        CashRegister closing = cashRegisterService.registerClosing(token, box.getId(), req.getClosingAmount());

        double totalSold = closing.getTotalSales();
        double expectedAmount = closing.getInitialAmount() + closing.getTotalSales();

        // devuelves un objeto con exactamente esos dos campos
        return ResponseEntity.ok(Map.of(
                "totalSold", totalSold,
                "expectedAmount", expectedAmount
        ));
    }

    /**
     * —————— LISTAR CAJAS ——————
     */
    @GetMapping
    public ResponseEntity<List<CashBoxDTO>> list(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(value = "open", required = false) Boolean openOnly
    ) {
        String token = authHeader.replace("Bearer ", "");
        List<CashBoxDTO> dtos = cashBoxService.listCashBoxes(token, openOnly)
                .stream()
                .map(b -> new CashBoxDTO(b.getId(), b.getCode(), b.getName(), b.getIsOpen(), b.getEnabled()))
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * ¿Tiene el usuario alguna caja abierta asignada?
     */
    @GetMapping("/status-for-user")
    public ResponseEntity<CashBoxStatusDTO> statusForUser(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        boolean open = cashBoxService.isCashBoxOpenForUser(token);

        // si está abierta, buscamos la caja y su código
        String code = null;
        if (open) {
            CashBox box = cashBoxService.getOpenCashBoxForUser(token);
            code = box.getCode();
        }

        CashBoxStatusDTO dto = new CashBoxStatusDTO(open, code);
        return ResponseEntity.ok(dto);
    }

    // —————— ASIGNAR USUARIO ——————
    @PostMapping("/{boxId}/users/{userId}")
    public ResponseEntity<Void> assignUser(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long boxId,
            @PathVariable Long userId
    ) {
        String token = authHeader.replace("Bearer ", "");
        cashBoxService.assignUserToBox(token, boxId, userId);
        return ResponseEntity.ok().build();
    }

    // —————— DESASIGNAR USUARIO ——————
    @DeleteMapping("/{boxId}/users/{userId}")
    public ResponseEntity<Void> unassignUser(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long boxId,
            @PathVariable Long userId
    ) {
        String token = authHeader.replace("Bearer ", "");
        cashBoxService.removeUserFromBox(token, boxId, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{boxId}/users")
    public ResponseEntity<List<UserBasicDTO>> listAssignedUsers(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long boxId
    ) {
        String token = authHeader.replace("Bearer ", "");
        List<User> users = cashBoxService.getUsersForBox(token, boxId);
        List<UserBasicDTO> dtos = users.stream()
                .map(u -> new UserBasicDTO(u.getId(), u.getUsername(), u.getRole().name()))
                .toList();
        log.info("Controller listAssignedUsers boxId={} → DTOs count={} → {}", boxId, dtos.size(),
                dtos.stream().map(UserBasicDTO::getUsername).toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * —————— PUEDE CREAR MÁS CAJAS? ——————
     */
    @GetMapping("/can-create")
    public ResponseEntity<Boolean> canCreate(
            @RequestHeader("Authorization") String authHeader
    ) {
        String token = authHeader.replace("Bearer ", "");
        boolean canCreate = cashBoxService.canCreateNewCashBox(token);
        return ResponseEntity.ok(canCreate);
    }

    /**
     * Habilitar / deshabilitar lógicamente una caja
     */
    @PutMapping("/{boxId}/enabled")
    public ResponseEntity<?> toggleEnabled(
            @RequestHeader("Authorization") String authHeader,
            @PathVariable Long boxId,
            @RequestParam boolean enabled
    ) {
        String token = authHeader.replace("Bearer ", "");
        CashBox updated = cashBoxService.setEnabled(token, boxId, enabled);
        // si quieres, mapéalo a DTO
        return ResponseEntity.ok(Map.of(
                "id", updated.getId(),
                "code", updated.getCode(),
                "enabled", updated.getEnabled()
        ));
    }
}
