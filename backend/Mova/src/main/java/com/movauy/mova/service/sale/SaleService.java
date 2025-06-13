// src/main/java/com/movauy/mova/service/sale/SaleService.java
package com.movauy.mova.service.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.dto.SaleItemDTO;
import com.movauy.mova.dto.SaleItemResponseDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import com.movauy.mova.model.sale.SaleItem;
import com.movauy.mova.model.sale.SaleItemIngredient;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.repository.sale.SaleItemIngredientRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final SaleItemIngredientRepository saleItemIngredientRepository;
    private final AuthService authService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public Sale registerSale(SaleDTO saleDTO, String token) {
        UserBasicDTO me = authService.getUserBasicFromToken(token);
        Long userId = me.getId();
        Long companyId = me.getCompanyId();

        Long branchId = me.getBranchId();
        CashRegister cashReg = cashRegisterRepository
                .findByCloseDateIsNullAndBranch_Id(branchId)
                .orElseThrow(() -> new RuntimeException("La caja está cerrada."));

        User currentUser = authService.getUserById(userId);

        Sale sale = new Sale();
        sale.setTotalAmount(saleDTO.getTotalAmount().doubleValue());
        sale.setPaymentMethod(saleDTO.getPaymentMethod());
        sale.setDateTime(LocalDateTime.now());
        sale.setCashRegister(cashReg);
        sale.setUser(currentUser);
        sale.setKitchenStatus(OrderStatus.SENT_TO_KITCHEN);
        sale.setKitchenSentAt(LocalDateTime.now());
        sale.setBranch(currentUser.getBranch());

        List<SaleItem> items = saleDTO.getItems().stream()
                .map(dto -> {
                    Product product = productRepository.findById(dto.getProductId())
                            .orElseThrow(() -> new RuntimeException("Producto no encontrado ID=" + dto.getProductId()));

                    Long prodCid = product.getBranch().getCompany().getId();
                    if (!companyId.equals(prodCid)) {
                        throw new RuntimeException("Producto ID=" + dto.getProductId() + " no pertenece a esta empresa.");
                    }

                    SaleItem it = new SaleItem();
                    it.setSale(sale);
                    it.setProduct(product);
                    it.setQuantity(dto.getQuantity());
                    it.setUnitPrice(dto.getUnitPrice());
                    return it;
                })
                .collect(Collectors.toList());

        sale.setItems(items);

        System.out.println("DEBUG - Ingredientes recibidos por producto:");
        for (int i = 0; i < saleDTO.getItems().size(); i++) {
            SaleItemDTO item = saleDTO.getItems().get(i);
            System.out.println("Producto ID=" + item.getProductId() + " → Ingredientes=" + item.getIngredientIds());
        }
        Sale saved = saleRepository.save(sale);

        boolean toKitchen = saved.getItems().stream()
                .map(si -> si.getProduct().getCategory())
                .anyMatch(ProductCategory::isEnableKitchenCommands);

        if (toKitchen) {
            saved.setKitchenStatus(OrderStatus.SENT_TO_KITCHEN);
            saved.setKitchenSentAt(LocalDateTime.now());
            saved = saleRepository.save(saved);
        } else {
            saved.setKitchenStatus(OrderStatus.COMPLETED);
            saved = saleRepository.save(saved);
        }

        saveItemIngredients(saved, saleDTO.getItems());

        if (toKitchen) {
            messagingTemplate.convertAndSend("/topic/kitchen-orders",
                    toResponseDTO(saved));
        }

        return saved;
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
