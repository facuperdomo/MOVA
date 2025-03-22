package com.movauy.mova.service.product;

import com.movauy.mova.dto.ProductDTO;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.repository.product.ProductRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Obtiene todos los productos de una empresa y devuelve un DTO con la imagen en Base64 para el frontend.
     */
    public List<ProductDTO> getProductsByCompany(Integer companyId) {
        List<Product> products = productRepository.getProductsByCompany(companyId);
        return products.stream().map(product -> {
            String base64Image = (product.getImage() != null)
                    ? Base64.getEncoder().encodeToString(product.getImage())
                    : null;
            return new ProductDTO(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    base64Image
            );
        }).collect(Collectors.toList());
    }

    /**
     * Agrega un producto asegurando que se cumple la validación básica.
     */
    public Product addProduct(Product product) {
        if (product.getName() == null || product.getName().isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        if (product.getPrice() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a 0.");
        }
        return productRepository.save(product);
    }

    /**
     * Actualiza un producto verificando que pertenece a la empresa autenticada.
     */
    public Product updateProduct(Long id, String name, double price, byte[] image, Integer companyId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        // Se compara el id del usuario asociado al producto con el companyId autenticado.
        if (!product.getUser().getId().equals(companyId)) {
            throw new SecurityException("No tienes permiso para modificar este producto.");
        }

        product.setName(name);
        product.setPrice(price);
        if (image != null) {
            product.setImage(image);
        }
        return productRepository.save(product);
    }

    /**
     * Elimina un producto verificando que pertenece a la empresa autenticada.
     */
    @Transactional
    public void deleteProduct(Long id, Integer companyId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        if (!product.getUser().getId().equals(companyId)) {
            throw new SecurityException("No tienes permiso para eliminar este producto.");
        }
        productRepository.deleteById(id);
    }
}
