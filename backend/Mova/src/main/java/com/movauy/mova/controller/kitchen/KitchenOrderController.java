package com.movauy.mova.controller.kitchen;

import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.dto.UpdateKitchenStatusDTO;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import com.movauy.mova.service.sale.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/kitchen/orders")
@PreAuthorize("hasRole('KITCHEN')")
@RequiredArgsConstructor
public class KitchenOrderController {

    private final SaleService saleService;

    @GetMapping
    public ResponseEntity<List<SaleResponseDTO>> listPending(
            @RequestHeader("Authorization") String token
    ) {
        List<Sale> sales = saleService.getOrdersByBranchAndKitchenStatus(token, OrderStatus.SENT_TO_KITCHEN);
        List<SaleResponseDTO> dtos = sales.stream()
                .map(saleService::toResponseDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}/kitchen-status")
    public ResponseEntity<SaleResponseDTO> updateKitchenStatus(
            @PathVariable Long id,
            @RequestBody UpdateKitchenStatusDTO body
    ) {
        Sale updated = saleService.updateKitchenStatus(id, body.getKitchenStatus());
        return ResponseEntity.ok(saleService.toResponseDTO(updated));
    }
    
}

