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
        // 1. Obtener el companyId a partir del token
        Long companyId = authService.getCompanyIdFromToken(token);

        // 2. Verificar que exista una caja abierta para este usuario
        CashRegister currentCashRegister = cashRegisterRepository
                .findByCloseDateIsNullAndUser_Id(companyId)
                .orElseThrow(() -> new RuntimeException("No se puede realizar la venta porque la caja está cerrada."));

        // 3. Seleccionar el objeto User a utilizar según el método de pago:
        // Si el método de pago es "QR", se recupera el usuario completo,
        // de lo contrario se obtiene una versión básica sin el token.
        User currentUser;
        if ("QR".equalsIgnoreCase(saleDTO.getPaymentMethod())) {
            // Recupera el usuario completo (incluye el token)
            currentUser = authService.getUserById(companyId);
        } else {
            // Recupera los datos básicos del usuario (sin token)
            UserBasicDTO userBasicDTO = authService.getUserBasicById(companyId);
            // Conviértelo a entidad, utilizando el mapper. Aquí solo actualizamos los campos básicos.
            currentUser = UserMapper.toUser(userBasicDTO, new User());
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
            // Validar que el producto pertenezca a la empresa
            if (product.getUser() == null || !product.getUser().getId().equals(companyId)) {
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
