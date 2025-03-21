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
     * 🔹 Obtener todos los productos. ✅ Devuelve un DTO con la imagen en Base64
     * para el frontend.
     */
    public List<ProductDTO> getAllProducts() {
        List<Product> products = productRepository.findAll();
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
     * 🔹 Agregar un producto asegurando que la imagen se guarde como BLOB.
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

    public Product updateProduct(Long id, String name, double price, byte[] image) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        product.setName(name);
        product.setPrice(price);

        if (image != null) {
            product.setImage(image);
        }

        return productRepository.save(product);
    }

    /**
     * 🔹 Eliminar un producto verificando que exista antes de borrarlo.
     */
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new IllegalArgumentException("Producto con ID " + id + " no encontrado.");
        }
        productRepository.deleteById(id);
    }
}
