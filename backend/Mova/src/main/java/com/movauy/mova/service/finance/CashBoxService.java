// src/main/java/com/movauy/mova/service/finance/CashBoxService.java
package com.movauy.mova.service.finance;

import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.finance.CashBox;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.plan.Plan;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.finance.CashBoxRepository;
import com.movauy.mova.repository.user.UserRepository;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.user.AuthService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class CashBoxService {

    private final CashBoxRepository cashBoxRepository;
    private final BranchRepository branchRepository;
    private final BranchService branchService;
    private final AuthService authService;
    private final UserRepository userRepo;
    private static final Logger log = LoggerFactory.getLogger(CashBoxService.class);

    /**
     * Obtiene la caja abierta para la sucursal autenticada y código dado.
     */
    public CashBox getOpenCashBox(String token, String code) {
        Long branchId = authService.getBranchIdFromToken(token);
        return cashBoxRepository
                .findByBranchIdAndCodeAndIsOpenTrue(branchId, code)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No hay caja abierta con código '" + code + "' en la sucursal " + branchId
        ));
    }

    /**
     * Lista todas las cajas (histórico) o sólo las abiertas (si openOnly=true).
     */
    public List<CashBox> listCashBoxes(String token, Boolean openOnly) {
        Long branchId = authService.getBranchIdFromToken(token);
        if (openOnly == null) {
            return cashBoxRepository.findByBranchIdOrderByCode(branchId);
        } else if (openOnly) {
            return cashBoxRepository.findByBranchIdAndIsOpenTrue(branchId);
        } else {
            return cashBoxRepository.findByBranchIdAndIsOpenFalse(branchId);
        }
    }

    /**
     * Busca una caja por código; lanza 404 si no existe.
     */
    public CashBox getByCode(String token, String code) {
        Long branchId = authService.getBranchIdFromToken(token);
        return cashBoxRepository
                .findByBranchIdAndCode(branchId, code)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Caja con código '" + code + "' no encontrada"
        ));
    }

    /**
     * Obtiene la caja abierta por su id, validando que pertenezca al usuario y
     * esté open.
     */
    public CashBox getOpenCashBoxById(String token, Long boxId) {
        Long branchId = authService.getBranchIdFromToken(token);
        CashBox box = cashBoxRepository.findById(boxId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Caja no encontrada: " + boxId
        ));
        if (!box.getBranch().getId().equals(branchId) || Boolean.FALSE.equals(box.getIsOpen())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Caja inválida o no está abierta: " + boxId
            );
        }
        return box;
    }

    /**
     * Crea una nueva caja (sin abrir), validando el límite
     */
    @Transactional
    public CashBox createCashBox(String token, String code, String name) {
        Long branchId = authService.getBranchIdFromToken(token);

        // 1) Límite de plan
        if (!canCreateNewCashBox(token)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    String.format("Has alcanzado el máximo de %d cajas para este plan",
                            getMaxCashBoxesForBranch(branchId))
            );
        }

        // 2) Conflicto de código existente
        if (cashBoxRepository.existsByBranchIdAndCodeAndEnabledTrue(branchId, code)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Ya existe una caja **habilitada** con código '" + code + "' en esta sucursal"
            );
        }

        // 3) Crear y guardar
        CashBox box = CashBox.builder()
                .branch(Branch.builder().id(branchId).build())
                .code(code)
                .name(name)
                .isOpen(false)
                .enabled(true)
                .build();
        return cashBoxRepository.save(box);
    }

    /**
     * Devuelve true si el usuario del token tiene al menos una caja abierta
     * asignada en su sucursal.
     */
    public boolean isCashBoxOpenForUser(String token) {
        Long branchId = authService.getBranchIdFromToken(token);
        Long userId = authService.getUserBasicFromToken(token).getId();
        List<CashBox> cajas = cashBoxRepository
                .findByBranchIdAndIsOpenTrueAndAssignedUsers_Id(branchId, userId);
        return !cajas.isEmpty();
    }

    @Transactional
    public void assignUserToBox(String jwt, Long boxId, Long userId) {
        CashBox box = cashBoxRepository.findById(boxId)
                .orElseThrow(() -> new EntityNotFoundException("Caja no encontrada"));
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        // Validación: si el usuario ya tiene una caja distinta, error
        if (user.getAssignedBox() != null && !user.getAssignedBox().getId().equals(boxId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "El usuario '" + user.getUsername() + "' ya está asignado a la caja '"
                    + user.getAssignedBox().getCode() + "'."
            );
        }

        user.setAssignedBox(box);
        userRepo.save(user);
    }

    @Transactional
    public void removeUserFromBox(String jwt, Long boxId, Long userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("Usuario no encontrado"));

        user.setAssignedBox(null);
        userRepo.save(user);
    }

    public List<User> getUsersForBox(String jwt, Long boxId) {
        // (Opcional) valida que la caja exista y pertenece a tu sucursal:
        cashBoxRepository.findById(boxId)
                .orElseThrow(() -> new EntityNotFoundException("Caja no encontrada: " + boxId));

        // Aquí usas directamente el repo para evitar cargar via la relación
        List<User> assigned = userRepo.findByAssignedBox_Id(boxId);
        log.debug(">>> Usuarios asignados a caja {}: {}", boxId,
                assigned.stream().map(User::getUsername).toList());
        return assigned;
    }

    /**
     * Busca la caja abierta asignada al usuario del token
     */
    public CashBox getOpenCashBoxForUser(String token) {
        // 1) sacamos el ID de usuario y sucursal
        UserBasicDTO me = authService.getUserBasicFromToken(token);
        Long userId = me.getId();
        Long branchId = me.getBranchId();
        log.debug("→ getOpenCashBoxForUser: userId={} branchId={}", userId, branchId);

        // 2) cargamos el usuario y su assignedBox
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Usuario no encontrado: " + userId
        ));

        CashBox box = user.getAssignedBox();
        log.debug("    Usuario {} tiene assignedBox={}", user.getUsername(), box != null ? box.getId() : null);

        // 3) validaciones: existe, está en la misma sucursal y sigue abierta
        if (box == null
                || !box.getBranch().getId().equals(branchId)
                || Boolean.FALSE.equals(box.getIsOpen())) {
            String msg = "No tienes ninguna caja abierta asignada";
            log.warn("✘ {}", msg);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
        }

        log.debug("✔ Caja válida para el usuario: id={} código={}", box.getId(), box.getCode());
        return box;
    }

    /**
     * Cuenta CUÁNTAS cajas (entidades) hay en esta sucursal
     */
    public long countBoxesForBranch(Long branchId) {
        return cashBoxRepository.countByBranchId(branchId);
    }

    /**
     * Obtiene el máximo de cajas permitido segun el plan de la sucursal
     */
    public int getMaxCashBoxesForBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Branch no encontrada: " + branchId));
        return branch.getPlan().getMaxCashBoxes();
    }

    /**
     * ¿Puede crear una nueva caja (sin tener en cuenta si está abierta o
     * cerrada)? Se basa en el máximo de cajas que permite el plan de la
     * sucursal.
     */
    // --------------------------------------------------------
    // ¿Puede crear una nueva caja? (usa authService, no jwtService)
    public boolean canCreateNewCashBox(String token) {
        Long branchId = authService.getBranchIdFromToken(token);
        int maxBoxes = getMaxCashBoxesForBranch(branchId);

        // >>> aquí contamos sólo las habilitadas
        long totalEnabled = cashBoxRepository.countByBranchIdAndEnabledTrue(branchId);

        return totalEnabled < maxBoxes;
    }

    /**
     * Marca una caja como enabled = false (o true)
     */
    @Transactional
    public CashBox setEnabled(String token, Long boxId, boolean enabled) {
        Long branchId = authService.getBranchIdFromToken(token);
        CashBox box = cashBoxRepository.findById(boxId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Caja no encontrada: " + boxId
        ));

        // valida que la caja pertenezca a tu sucursal
        if (!box.getBranch().getId().equals(branchId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "No puedes modificar esta caja"
            );
        }

        // si estamos deshabilitando, desasigno a todos los usuarios
        if (!enabled) {
            // busca todos los usuarios con assignedBox = esta caja
            List<User> assigned = userRepo.findByAssignedBox_Id(boxId);
            assigned.forEach(u -> u.setAssignedBox(null));
            userRepo.saveAll(assigned);
        }

        box.setEnabled(enabled);
        return cashBoxRepository.save(box);
    }

}
