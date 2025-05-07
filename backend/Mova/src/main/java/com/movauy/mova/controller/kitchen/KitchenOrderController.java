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

    // 1) Listar pedidos pendientes de cocina
    @GetMapping
    public List<SaleResponseDTO> listPending() {
        return saleService.getOrdersByKitchenStatus(OrderStatus.SENT_TO_KITCHEN)
                .stream().map(saleService::toResponseDTO).toList();
    }

    @PutMapping("/{id}/kitchen-status")
    public SaleResponseDTO updateKitchenStatus(
            @PathVariable Long id,
            @RequestBody UpdateKitchenStatusDTO body
    ) {
        Sale updated = saleService.updateKitchenStatus(id, body.getKitchenStatus());
        return saleService.toResponseDTO(updated);
    }
}
