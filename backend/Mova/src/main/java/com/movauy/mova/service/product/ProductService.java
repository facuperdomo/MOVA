package com.movauy.mova.service.product;

import com.movauy.mova.dto.IngredientDTO;
import com.movauy.mova.dto.ProductDTO;
import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.repository.ingredient.IngredientRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final IngredientRepository ingredientRepository;
    private final AuthService authService;
    private final ProductCategoryService categoryService;  // inyectamos tu servicio de categorías

    /**
     * Obtiene todos los productos de una empresa y devuelve DTOs con
     * la imagen en Base64 y la lista de ingredientes.
     */
    public List<ProductDTO> getProductsByCompany(Integer companyId) {
        List<Product> products = productRepository.getProductsByCompany(companyId);
        return products.stream().map(product -> {
            String base64Image = product.getImage() != null
                    ? Base64.getEncoder().encodeToString(product.getImage())
                    : null;
            Long categoryId = product.getCategory() != null
                    ? product.getCategory().getId()
                    : null;
            String categoryName = product.getCategory() != null
                    ? product.getCategory().getName()
                    : "Sin categoría";

            List<IngredientDTO> ingredientDTOs = product.getIngredients().stream()
                    .map(i -> new IngredientDTO(i.getId(), i.getName()))
                    .collect(Collectors.toList());

            return new ProductDTO(
                    product.getId(),
                    product.getName(),
                    product.getPrice(),
                    base64Image,
                    categoryId,
                    categoryName,
                    product.isEnableIngredients(),
                    product.isEnableKitchenCommands(),
                    ingredientDTOs
            );
        }).collect(Collectors.toList());
    }

    /**
     * Agrega un producto validando nombre, precio, categoría e ingredientes.
     */
    @Transactional
    public Product addProduct(
            Product product,
            Long categoryId,
            Long companyId,
            List<Long> ingredientIds
    ) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        if (product.getPrice() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a 0.");
        }

        // Asignar categoría si viene
        if (categoryId != null) {
            ProductCategory category = categoryService.getById(categoryId);
            product.setCategory(category);
        }

        // Asignar ingredientes si vienen
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            List<Ingredient> ingredientes = ingredientRepository
                    .findByIdsForCompany(companyId, ingredientIds);
            product.setIngredients(new HashSet<>(ingredientes));
            product.setEnableIngredients(true);
        }

        // El controller ya asignó product.setUser(...)
        return productRepository.save(product);
    }

    /**
     * Actualiza un producto verificando permisos y reemplazando ingredientes.
     */
    @Transactional
    public Product updateProduct(
            Long id,
            String name,
            double price,
            byte[] image,
            Integer companyId,
            Long categoryId,
            List<Long> ingredientIds
    ) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        // Calcula el companyId "dueño" del producto
        String ownerCid = existing.getUser().getCompanyId();
        Long ownerCompanyId = (ownerCid == null || ownerCid.isBlank())
                ? existing.getUser().getId().longValue()
                : Long.valueOf(ownerCid);

        if (!ownerCompanyId.equals(companyId.longValue())) {
            throw new SecurityException("No tienes permiso para modificar este producto.");
        }

        // Actualizar campos
        existing.setName(name);
        existing.setPrice(price);

        if (categoryId != null) {
            ProductCategory category = categoryService.getById(categoryId);
            existing.setCategory(category);
        }

        if (image != null && image.length > 0) {
            existing.setImage(image);
        }

        // Reemplazar ingredientes
        existing.getIngredients().clear();
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            List<Ingredient> ingredientes = ingredientRepository
                    .findByIdsForCompany(companyId.longValue(), ingredientIds);
            existing.getIngredients().addAll(ingredientes);
            existing.setEnableIngredients(true);
        } else {
            existing.setEnableIngredients(false);
        }

        return productRepository.save(existing);
    }

    /**
     * Elimina un producto verificando que pertenezca a la empresa autenticada.
     */
    @Transactional
    public void deleteProduct(Long id, Integer companyId) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        String ownerCid = existing.getUser().getCompanyId();
        Long ownerCompanyId = (ownerCid == null || ownerCid.isBlank())
                ? existing.getUser().getId().longValue()
                : Long.valueOf(ownerCid);

        if (!ownerCompanyId.equals(companyId.longValue())) {
            throw new SecurityException("No tienes permiso para eliminar este producto.");
        }

        productRepository.delete(existing);
    }
    
    @Transactional
  public void updateIngredients(Long id, Integer companyId, List<Long> ingredientIds) {
    Product product = productRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        // Validar que la empresa propietaria coincide
        String ownerCid = product.getUser().getCompanyId();
        Long ownerCompanyId = (ownerCid == null || ownerCid.isBlank())
            ? product.getUser().getId().longValue()
            : Long.valueOf(ownerCid);
        if (!ownerCompanyId.equals(companyId.longValue())) {
            throw new SecurityException("No tienes permiso para modificar este producto.");
        }

        // Limpiar y poblar la nueva lista
        product.getIngredients().clear();
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            List<Ingredient> ings = ingredientRepository
                .findByIdsForCompany(companyId.longValue(), ingredientIds);
            product.getIngredients().addAll(ings);
            product.setEnableIngredients(true);
        } else {
            product.setEnableIngredients(false);
        }

        productRepository.save(product);
    }
}
