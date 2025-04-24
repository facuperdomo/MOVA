package com.movauy.mova.service.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.mapper.UserMapper;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.SaleItem;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.user.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SaleService {

    private final ProductRepository productRepository;
    private final SaleRepository saleRepository;
    private final CashRegisterRepository cashRegisterRepository;
    private final AuthService authService;

    @Autowired
    public SaleService(ProductRepository productRepository,
                       SaleRepository saleRepository,
                       CashRegisterRepository cashRegisterRepository,
                       AuthService authService) {
        this.productRepository = productRepository;
        this.saleRepository = saleRepository;
        this.cashRegisterRepository = cashRegisterRepository;
        this.authService = authService;
    }

    @Transactional
    public Sale registerSale(SaleDTO saleDTO, String token) {
        // 1. Obtener información del usuario autenticado
        UserBasicDTO userBasic = authService.getUserBasicFromToken(token);
        Long userId = userBasic.getId();               // ID del cajero
        String companyIdFromToken = userBasic.getCompanyId(); // ID de la empresa autenticada

        // 2. Verificar que exista una caja abierta para este usuario
        CashRegister currentCashRegister = cashRegisterRepository
                .findByCloseDateIsNullAndUser_Id(userId)
                .orElseThrow(() -> new RuntimeException("No se puede realizar la venta porque la caja está cerrada."));

        // 3. Seleccionar el objeto User a utilizar según el método de pago:
        User currentUser;
        if ("QR".equalsIgnoreCase(saleDTO.getPaymentMethod())) {
            currentUser = authService.getUserById(userId);
        } else {
            currentUser = UserMapper.toUser(userBasic, new User());
        }

        // 4. Crear la venta
        Sale sale = new Sale();
        sale.setTotalAmount(saleDTO.getTotalAmount());
        sale.setPaymentMethod(saleDTO.getPaymentMethod());
        sale.setDateTime(LocalDateTime.now());
        sale.setCashRegister(currentCashRegister);
        sale.setUser(currentUser);

        // 5. Procesar los ítems de la venta
        List<SaleItem> saleItems = saleDTO.getItems().stream().map(itemDTO -> {
            SaleItem item = new SaleItem();
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + itemDTO.getProductId()));

            System.out.println("🧾 Verificando producto...");
            System.out.println("🧾 Producto ID: " + product.getId());
            System.out.println("👤 Usuario dueño del producto: " + (product.getUser() != null ? product.getUser().getId() : "null"));
            System.out.println("🏢 companyId desde token: " + companyIdFromToken);
            System.out.println("🏢 companyId real del dueño del producto: " + (product.getUser() != null ? product.getUser().getCompanyId() : "null"));

            // Validar que el producto pertenezca a la empresa
            if (product.getUser() == null || !product.getUser().getCompanyId().equals(companyIdFromToken)) {
                throw new RuntimeException("El producto con ID " + itemDTO.getProductId() + " no pertenece a esta empresa.");
            }

            item.setProduct(product);
            item.setQuantity(itemDTO.getQuantity());
            item.setUnitPrice(itemDTO.getUnitPrice());
            item.setSale(sale);
            return item;
        }).collect(Collectors.toList());

        sale.setItems(saleItems);
        return saleRepository.save(sale);
    }
    
}
