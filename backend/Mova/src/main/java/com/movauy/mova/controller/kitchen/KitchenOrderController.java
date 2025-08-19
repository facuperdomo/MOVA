// src/main/java/com/movauy/mova/controller/kitchen/KitchenOrderController.java
package com.movauy.mova.controller.kitchen;

import com.movauy.mova.dto.KitchenOrderDTO;
import com.movauy.mova.dto.SaleItemResponseDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.dto.UpdateKitchenStatusDTO;
import com.movauy.mova.model.kitchen.KitchenOrder;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import com.movauy.mova.service.kitchen.KitchenOrderService;
import com.movauy.mova.service.product.ProductCategoryService;
import com.movauy.mova.service.sale.SaleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/kitchen/orders")
@PreAuthorize("hasRole('KITCHEN')")
@RequiredArgsConstructor
public class KitchenOrderController {

    private final SaleService saleService;
    private final ProductCategoryService categoryService;
    private final KitchenOrderService kitchenOrderService;

    @GetMapping
    public ResponseEntity<List<Object>> listPending(@RequestHeader("Authorization") String token) {
        // 1) Ventas pendientes (como ya lo tienes)
        List<Sale> sales = saleService.getOrdersByBranchAndKitchenStatus(token, OrderStatus.SENT_TO_KITCHEN);
        List<SaleResponseDTO> saleDtos = sales.stream()
                .map(saleService::toResponseDTO)
                .map(dto -> {
                    List<SaleItemResponseDTO> kitchenItems = dto.getItems().stream()
                            .filter(item -> {
                                ProductCategory cat = categoryService.getById(item.getCategoryId());
                                return cat.isEnableKitchenCommands();
                            })
                            .collect(Collectors.toList());
                    dto.setItems(kitchenItems);
                    return dto;
                })
                .filter(dto -> !dto.getItems().isEmpty())
                .toList();

        // 2) ✅ snapshots agregados por cuenta (NO toDTO de cada KitchenOrder)
        List<KitchenOrderDTO> accountDtos = kitchenOrderService.findPendingByBranchAggregated(token);

        // 3) Unir resultados
        List<Object> result = new ArrayList<>();
        result.addAll(saleDtos);
        result.addAll(accountDtos);
        return ResponseEntity.ok(result);
    }

    @PutMapping("/{id}/kitchen-status")
    public ResponseEntity<SaleResponseDTO> updateKitchenStatus(
            @PathVariable Long id,
            @RequestBody UpdateKitchenStatusDTO body
    ) {
        Sale updated = saleService.updateKitchenStatus(id, body.getKitchenStatus());
        SaleResponseDTO dto = saleService.toResponseDTO(updated);
        List<SaleItemResponseDTO> kitchenItems = dto.getItems().stream()
                .filter(it -> {
                    ProductCategory cat = categoryService.getById(it.getCategoryId());
                    return cat.isEnableKitchenCommands();
                })
                .toList();
        dto.setItems(kitchenItems);
        return ResponseEntity.ok(dto);
    }
}
