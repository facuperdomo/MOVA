/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.movauy.mova.controller.branch;

import com.movauy.mova.dto.BranchDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/branch")
@RequiredArgsConstructor
public class CurrentBranchController {

    private final BranchService branchService;
    private final AuthService authService;

    @GetMapping("/me")
    public ResponseEntity<BranchDTO> getCurrentBranch(@RequestHeader("Authorization") String authHeader) {
        Long branchId = authService.getUserBasicFromToken(authHeader).getBranchId();
        Branch branch = branchService.findById(branchId);
        return ResponseEntity.ok(
                BranchDTO.builder()
                        .id(branch.getId())
                        .companyId(branch.getCompany().getId()) // si lo necesitas
                        .name(branch.getName())
                        .username(branch.getUsername()) // idem
                        .enableIngredients(branch.isEnableIngredients())
                        .enableKitchenCommands(branch.isEnableKitchenCommands())
                        .enablePrinting(branch.isEnablePrinting()) // ← aquí lo agregas
                        .location(branch.getLocation()) // y el resto de campos que uses
                        .phone(branch.getPhone())
                        .rut(branch.getRut())
                        .enabled(branch.isEnabled())
                        .planId(branch.getPlan() != null ? branch.getPlan().getId() : null)
                        .planName(branch.getPlan() != null ? branch.getPlan().getName() : null)
                        .maxCashBoxes(branch.getPlan() != null ? branch.getPlan().getMaxCashBoxes() : null)
                        .maxUsers(branch.getPlan() != null ? branch.getPlan().getMaxUsers() : null)
                        .build()
        );
    }

}
