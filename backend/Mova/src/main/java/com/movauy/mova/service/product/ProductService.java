package com.movauy.mova.service.product;

import com.movauy.mova.dto.IngredientDTO;
import com.movauy.mova.dto.ProductDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.ingredient.IngredientRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.service.product.ProductCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductCategoryService categoryService;
    private final BranchRepository branchRepository;

    public List<ProductDTO> getProductsByBranch(Long branchId) {
        List<Product> products = productRepository.getProductsByBranch(branchId);
        return products.stream().map(product -> {
            String base64Image = product.getImage() != null
                    ? Base64.getEncoder().encodeToString(product.getImage())
                    : null;
            Long categoryId = product.getCategory() != null ? product.getCategory().getId() : null;
            String categoryName = product.getCategory() != null ? product.getCategory().getName() : "Sin categoría";
            List<IngredientDTO> ingredients = product.getIngredients().stream()
                    .map(i -> new IngredientDTO(i.getId(), i.getName()))
                    .collect(Collectors.toList());

            return new ProductDTO(
                    product.getId(), product.getName(), product.getPrice(), base64Image,
                    categoryId, categoryName, product.isEnableIngredients(), ingredients,
                    product.getBranch().getCompany().getId()
            );
        }).collect(Collectors.toList());
    }

    @Transactional
    public Product addProduct(Product product, Long categoryId, List<Long> ingredientIds) {
        if (product.getName() == null || product.getName().isBlank()) {
            throw new IllegalArgumentException("El nombre del producto no puede estar vacío.");
        }
        if (product.getPrice() <= 0) {
            throw new IllegalArgumentException("El precio debe ser mayor a 0.");
        }

        String trimmedName = product.getName().trim();
        Long branchId = product.getBranch().getId();

        // Validar unicidad por sucursal
        productRepository.findByBranchIdAndName(branchId, trimmedName).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe un producto con ese nombre en esta sucursal.");
        });

        // Asignar categoría si corresponde
        if (categoryId != null) {
            product.setCategory(categoryService.getById(categoryId));
        }

        // Asignar ingredientes si se indicaron
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            List<Ingredient> ingredientes = ingredientRepository.findByIdsForBranch(branchId, ingredientIds);
            product.setIngredients(new HashSet<>(ingredientes));
            product.setEnableIngredients(true);
        }

        return productRepository.save(product);
    }

    @Transactional
    public Product updateProduct(Long id, String name, double price, byte[] image, Long branchId, Long categoryId, List<Long> ingredientIds) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        if (!existing.getBranch().getId().equals(branchId)) {
            throw new SecurityException("No tienes permiso para modificar este producto.");
        }

        String trimmedName = name.trim();

        // Validar si se está cambiando el nombre, y si ya existe otro con ese nombre
        if (!existing.getName().equalsIgnoreCase(trimmedName)) {
            productRepository.findByBranchIdAndName(branchId, trimmedName).ifPresent(other -> {
                throw new IllegalArgumentException("Ya existe otro producto con ese nombre en esta sucursal.");
            });
        }

        existing.setName(trimmedName);
        existing.setPrice(price);

        if (categoryId != null) {
            existing.setCategory(categoryService.getById(categoryId));
        }

        if (image != null && image.length > 0) {
            existing.setImage(image);
        }

        existing.getIngredients().clear();
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            List<Ingredient> ingredientes = ingredientRepository.findByIdsForBranch(branchId, ingredientIds);
            existing.getIngredients().addAll(ingredientes);
            existing.setEnableIngredients(true);
        } else {
            existing.setEnableIngredients(false);
        }

        return productRepository.save(existing);
    }

    @Transactional
    public void deleteProduct(Long id, Long branchId) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        if (!existing.getBranch().getId().equals(branchId)) {
            throw new SecurityException("No tienes permiso para eliminar este producto.");
        }

        productRepository.delete(existing);
    }

    @Transactional
    public void updateIngredients(Long id, Long branchId, List<Long> ingredientIds) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Producto no encontrado"));

        if (!product.getBranch().getId().equals(branchId)) {
            throw new SecurityException("No tienes permiso para modificar ingredientes.");
        }

        product.getIngredients().clear();
        if (ingredientIds != null && !ingredientIds.isEmpty()) {
            List<Ingredient> ings = ingredientRepository.findByIdsForBranch(branchId, ingredientIds);
            product.getIngredients().addAll(ings);
            product.setEnableIngredients(true);
        } else {
            product.setEnableIngredients(false);
        }

        productRepository.save(product);
    }

    public Branch getBranch(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));
    }
}
