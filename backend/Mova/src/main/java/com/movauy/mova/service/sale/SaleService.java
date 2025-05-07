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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.messaging.simp.SimpMessagingTemplate;

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
        // 1) Obtener user básico y verificar caja abierta
        UserBasicDTO userBasic = authService.getUserBasicFromToken(token);
        Long userId = userBasic.getId();
        String companyId = userBasic.getCompanyId();

        CashRegister currentCashRegister = cashRegisterRepository
                .findByCloseDateIsNullAndUser_Id(userId)
                .orElseThrow(() -> new RuntimeException("La caja está cerrada."));

        // 2) Recuperar la entidad User MANAGED desde BD
        User currentUser = authService.getUserById(userId);

        // 3) Crear la Sale
        Sale sale = new Sale();
        sale.setTotalAmount(saleDTO.getTotalAmount());
        sale.setPaymentMethod(saleDTO.getPaymentMethod());
        sale.setDateTime(LocalDateTime.now());
        sale.setCashRegister(currentCashRegister);
        sale.setUser(currentUser);
        sale.setKitchenStatus(OrderStatus.SENT_TO_KITCHEN);
        sale.setKitchenSentAt(LocalDateTime.now());

        // 4) Construir los SaleItem
        List<SaleItem> items = saleDTO.getItems().stream().map(dto -> {
            Product product = productRepository.findById(dto.getProductId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado ID=" + dto.getProductId()));

            // validar empresa
            if (product.getUser() == null || !product.getUser().getCompanyId().equals(companyId)) {
                throw new RuntimeException("Producto ID=" + dto.getProductId() + " no pertenece a esta empresa.");
            }

            SaleItem item = new SaleItem();
            item.setSale(sale);
            item.setProduct(product);
            item.setQuantity(dto.getQuantity());
            item.setUnitPrice(dto.getUnitPrice());
            return item;
        }).collect(Collectors.toList());

        // 5) Asociar y salvar
        sale.setItems(items);
        Sale savedSale = saleRepository.save(sale);

        SaleResponseDTO payload = toResponseDTO(savedSale);
        boolean hasKitchen = savedSale.getItems().stream()
                .map(si -> si.getProduct().getCategory())
                .anyMatch(ProductCategory::isEnableKitchenCommands);

        if (hasKitchen) {
            savedSale.setKitchenStatus(OrderStatus.SENT_TO_KITCHEN);
            savedSale.setKitchenSentAt(LocalDateTime.now());
            savedSale = saleRepository.save(savedSale);

            // Publica sólo si va a cocina
            messagingTemplate.convertAndSend("/topic/kitchen-orders", toResponseDTO(savedSale));
        } else {
            // No va a cocina
            savedSale.setKitchenStatus(OrderStatus.COMPLETED);
            savedSale = saleRepository.save(savedSale);
        }

        // 6) Persiste ingredientes seleccionados por ítem
        List<SaleItemDTO> dtos = saleDTO.getItems();
        for (int i = 0; i < savedSale.getItems().size(); i++) {
            SaleItem si = savedSale.getItems().get(i);
            List<Long> ingIds = dtos.get(i).getIngredientIds();
            if (ingIds != null) {
                ingIds.forEach(ingId -> {
                    SaleItemIngredient link = new SaleItemIngredient();
                    link.setSaleItem(si);
                    link.setIngredientId(ingId);
                    saleItemIngredientRepository.save(link);
                });
            }
        }

        return savedSale;
    }

    // Obtener ventas por estado
    public List<Sale> getOrdersByStatus(OrderStatus status) {
        return saleRepository.findByKitchenStatus(status);
    }

// Convertir entidad a DTO
    public SaleResponseDTO toResponseDTO(Sale sale) {
        // mapeo explícito de cada SaleItem a SaleItemResponseDTO
        List<SaleItemResponseDTO> items = sale.getItems().stream()
                .map(si -> {
                    // traemos de la tabla SaleItemIngredient los IDs de ingrediente
                    List<Long> ingredientIds = saleItemIngredientRepository
                            .findBySaleItemId(si.getId())
                            .stream()
                            .map(link -> link.getIngredientId())
                            .collect(Collectors.toList());

                    return new SaleItemResponseDTO(
                            si.getProduct().getId(),
                            si.getQuantity(),
                            si.getUnitPrice(),
                            ingredientIds
                    );
                })
                .collect(Collectors.toList());

        // finalmente construimos el DTO de la venta completa
        return new SaleResponseDTO(
                sale.getId(),
                sale.getTotalAmount(),
                sale.getPaymentMethod(),
                sale.getDateTime(),
                sale.getEstado(), // tu EstadoVenta
                sale.getKitchenStatus(), // nuevo kitchenStatus
                sale.getKitchenSentAt(), // timestamp de envío
                items
        );
    }

// Actualizar estado de un pedido
    @Transactional
    public Sale updateOrderStatus(Long saleId, OrderStatus newStatus) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado ID=" + saleId));
        sale.setKitchenStatus(newStatus);
        // si cambias a SENT_TO_KITCHEN, setea timestamp
        if (newStatus == OrderStatus.SENT_TO_KITCHEN) {
            sale.setKitchenSentAt(LocalDateTime.now());
        }
        return saleRepository.save(sale);
    }

    public List<Sale> getOrdersByKitchenStatus(OrderStatus ks) {
        return saleRepository.findByKitchenStatus(ks);
    }

    // actualizar sólo kitchenStatus
    @Transactional
    public Sale updateKitchenStatus(Long saleId, OrderStatus newStatus) {
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado ID=" + saleId));
        sale.setKitchenStatus(newStatus);
        if (newStatus == OrderStatus.SENT_TO_KITCHEN) {
            sale.setKitchenSentAt(LocalDateTime.now());
        }
        return saleRepository.save(sale);
    }

}
