// src/main/java/com/movauy/mova/service/sale/SaleService.java
package com.movauy.mova.service.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.dto.SaleItemDTO;
import com.movauy.mova.dto.SaleItemResponseDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.finance.CashBox;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import com.movauy.mova.model.sale.SaleItem;
import com.movauy.mova.model.sale.SaleItemIngredient;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.finance.CashBoxRepository;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.repository.sale.SaleItemIngredientRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.finance.CashBoxService;
import com.movauy.mova.service.finance.CashRegisterService;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final SaleItemIngredientRepository saleItemIngredientRepository;
    private final AuthService authService;
    private final SimpMessagingTemplate messagingTemplate;
    private final CashBoxService cashBoxService;
    private final CashRegisterService cashRegisterService;
    private final CashBoxRepository cashBoxRepository;
    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    @Transactional
    public SaleResponseDTO registerSale(SaleDTO saleDTO, String token) {
        log.debug("→ SaleService.registerSale: recibidos DTO={}, token=[REDACTED]", saleDTO);

        // 1) Contexto de usuario
        UserBasicDTO me = authService.getUserBasicFromToken(token);
        User currentUser = authService.getUserById(me.getId());
        Long branchId = me.getBranchId();
        log.debug("   Usuario {} en sucursal {}", currentUser.getUsername(), branchId);

        // 2) Validar/la caja (ya inyectada en el controller) y comprobar que sigue abierta
        CashBox box;
        try {
            log.debug("   Validando caja abierta por ID={} en sucursal {}", saleDTO.getCashBoxId(), branchId);
            box = cashBoxService.getOpenCashBoxById(token, saleDTO.getCashBoxId());
            log.debug("   Caja OK: id={} código={}", box.getId(), box.getCode());
        } catch (ResponseStatusException ex) {
            log.warn("   Caja inválida o cerrada: {}", ex.getReason());
            throw ex;
        }

        // 3) Construir la entidad Sale
        Sale sale = new Sale();
        sale.setTotalAmount(saleDTO.getTotalAmount().doubleValue());
        sale.setPaymentMethod(saleDTO.getPaymentMethod());
        sale.setDateTime(LocalDateTime.now());
        sale.setCashBox(box);
        sale.setUser(currentUser);
        sale.setBranch(currentUser.getBranch());

        // 4) Procesar items
        boolean toKitchen = false;
        List<SaleItem> items = new ArrayList<>();
        for (var dto : saleDTO.getItems()) {
            var p = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Producto no encontrado ID=" + dto.getProductId()
            ));
            if (p.getCategory().isEnableKitchenCommands()) {
                toKitchen = true;
            }
            SaleItem it = new SaleItem();
            it.setSale(sale);
            it.setProduct(p);
            it.setQuantity(dto.getQuantity());
            it.setUnitPrice(dto.getUnitPrice());
            items.add(it);
        }
        sale.setItems(items);
        log.debug("   {} ítems agregados. toKitchen={}", items.size(), toKitchen);

        // 5) Estado de cocina
        if (toKitchen) {
            sale.setKitchenStatus(Sale.OrderStatus.SENT_TO_KITCHEN);
            sale.setKitchenSentAt(LocalDateTime.now());
        } else {
            sale.setKitchenStatus(Sale.OrderStatus.COMPLETED);
        }

        // 6) Guardar la venta
        Sale saved = saleRepository.save(sale);
        log.debug("   Venta persistida con ID={}", saved.getId());

        // 7) Guardar ingredientes de items
        saveItemIngredients(saved, saleDTO.getItems());
        log.debug("   Ingredientes de items guardados");

        // 8) Notificar cocina si hace falta
        if (toKitchen) {
            messagingTemplate.convertAndSend("/topic/kitchen-orders", toResponseDTO(saved));
            log.debug("   Notificación enviada a /topic/kitchen-orders");
        }

        return toResponseDTO(saved);
    }

    public List<Sale> getOrdersByStatus(OrderStatus status) {
        return saleRepository.findByKitchenStatus(status);
    }

    public SaleResponseDTO toResponseDTO(Sale sale) {
        var items = sale.getItems().stream()
                .map(si -> {
                    var ingIds = saleItemIngredientRepository
                            .findBySaleItemId(si.getId())
                            .stream()
                            .map(link -> link.getIngredientId())
                            .collect(Collectors.toList());
                    return new SaleItemResponseDTO(
                            si.getProduct().getId(),
                            si.getProduct().getName(),
                            si.getQuantity(),
                            si.getUnitPrice(),
                            ingIds
                    );
                }).collect(Collectors.toList());

        return new SaleResponseDTO(
                sale.getId(),
                sale.getAccount() != null ? sale.getAccount().getId() : null,
                sale.getTotalAmount(),
                sale.getPaymentMethod(),
                sale.getDateTime(),
                sale.getEstado(),
                sale.getKitchenStatus(),
                sale.getKitchenSentAt(),
                items
        );
    }

    @Transactional
    public Sale updateOrderStatus(Long saleId, OrderStatus newStatus) {
        Sale s = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado ID=" + saleId));
        s.setKitchenStatus(newStatus);
        if (newStatus == OrderStatus.SENT_TO_KITCHEN) {
            s.setKitchenSentAt(LocalDateTime.now());
        }
        return saleRepository.save(s);
    }

    public List<Sale> getOrdersByKitchenStatus(OrderStatus ks) {
        return saleRepository.findByKitchenStatus(ks);
    }

    @Transactional
    public Sale updateKitchenStatus(Long saleId, OrderStatus newStatus) {
        return updateOrderStatus(saleId, newStatus);
    }

    public List<Sale> getOrdersByBranchAndKitchenStatus(String token, OrderStatus status) {
        Long branchId = authService.getUserBasicFromToken(token).getBranchId();
        return saleRepository.findByBranchIdAndKitchenStatus(branchId, status);
    }

    private void saveItemIngredients(Sale savedSale, List<SaleItemDTO> originalItems) {
        for (int i = 0; i < savedSale.getItems().size(); i++) {
            SaleItem si = savedSale.getItems().get(i);
            List<Long> ingredientIds = originalItems.get(i).getIngredientIds();

            // 🔧 Asegurarse de que no sea null
            if (ingredientIds == null) {
                ingredientIds = List.of();
            }

            for (Long ingId : ingredientIds) {
                SaleItemIngredient link = new SaleItemIngredient();
                link.setSaleItem(si);
                link.setIngredientId(ingId);
                saleItemIngredientRepository.save(link);
            }
        }
    }

    public SaleResponseDTO getById(Long saleId) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + saleId));
        return toResponseDTO(sale);
    }
}
