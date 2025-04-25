package com.movauy.mova.service.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.dto.SaleItemDTO;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.sale.Sale;
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

@Service
@RequiredArgsConstructor
public class SaleService {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final SaleItemIngredientRepository saleItemIngredientRepository;
    private final AuthService authService;

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
}
