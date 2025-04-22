package com.movauy.mova.service.product;

import com.movauy.mova.dto.ProductDTO;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
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
    private final ProductCategoryService categoryService;

    /**
     * Obtiene todos los productos de una empresa y devuelve un DTO con la
     * imagen en Base64 para el frontend.
     */
    public List<ProductDTO> getProductsByCompany(Integer companyId) {
        List<Product> products = productRepository.getProductsByCompany(companyId);
        return products.stream().map(product -> {
            String base64Image = (product.getImage() != null)
                    ? Base64.getEncoder().encodeToString(product.getImage())
                    : null;

            Long categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
            String categoryName = product.getCategory() != null ? product.getCategory().getName() : "Sin categoría";

            return new ProductDTO(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    base64Image,
                    categoryId,
                    categoryName
            );
        }).collect(Collectors.toList());
    }

    /**
     * Agrega un producto asegurando que se cumple la validación básica.
     */
    public Product addProduct(Product product, Long categoryId) {
        if (product.getName() == null || product.getName().isEmpty()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        if (product.getPrice() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a 0.");
        }

        if (categoryId != null) {
            ProductCategory category = categoryService.getById(categoryId);
            product.setCategory(category);
        }
        System.out.println("➡️ Guardando producto con categoría ID: " + categoryId);
        return productRepository.save(product);
    }

    /**
     * Actualiza un producto verificando que pertenece a la empresa autenticada.
     */
    public Product updateProduct(Long id, String name, double price, byte[] image, Integer companyId, ProductCategory category) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        if (!product.getUser().getId().equals(companyId)) {
            throw new SecurityException("No tienes permiso para modificar este producto.");
        }

        product.setName(name);
        product.setPrice(price);
        if (category != null) {
            product.setCategory(category);
        }
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
