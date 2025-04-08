package com.movauy.mova.service.sale;

import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.model.finance.CashRegister;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.model.sale.SaleItem;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.repository.sale.SaleRepository;
import com.movauy.mova.service.user.AuthService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        // Primero: obtener el companyId a partir del token
        Long companyId = authService.getCompanyIdFromToken(token);

        // Obtiene la caja abierta filtrando por usuario (companyId)
        // Asegúrate que en CashRegisterRepository exista el método:
        // Optional<CashRegister> findByCloseDateIsNullAndUser_Id(Long userId);
        CashRegister currentCashRegister = cashRegisterRepository
                .findByCloseDateIsNullAndUser_Id(companyId)
                .orElseThrow(() -> new RuntimeException("No se puede realizar la venta porque la caja está cerrada."));

        // Para evitar que se active el AttributeConverter en la carga completa del usuario,
        // se instancia un objeto User con solo el ID.
        User currentUser = new User();
        currentUser.setId(companyId);

        // Crea la venta con la información
        Sale sale = new Sale();
        sale.setTotalAmount(saleDTO.getTotalAmount());
        sale.setPaymentMethod(saleDTO.getPaymentMethod());
        sale.setDateTime(LocalDateTime.now());
        sale.setCashRegister(currentCashRegister);
        sale.setUser(currentUser);

        // Procesa cada ítem de la venta
        List<SaleItem> saleItems = saleDTO.getItems().stream().map(itemDTO -> {
            SaleItem item = new SaleItem();

            // Busca el producto según su ID
            Product product = productRepository.findById(itemDTO.getProductId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado con ID: " + itemDTO.getProductId()));

            // Valida que el producto pertenezca a la empresa del usuario (currentUser)
            if (product.getUser() == null || !product.getUser().getId().equals(currentUser.getId())) {
                throw new RuntimeException("El producto con ID " + itemDTO.getProductId() + " no pertenece a esta empresa.");
            }

            item.setProduct(product);
            item.setQuantity(itemDTO.getQuantity());
            item.setUnitPrice(itemDTO.getUnitPrice());
            item.setSale(sale);
            return item;
        }).collect(Collectors.toList());

        sale.setItems(saleItems);

        // Guarda y retorna la venta
        return saleRepository.save(sale);
    }
}
