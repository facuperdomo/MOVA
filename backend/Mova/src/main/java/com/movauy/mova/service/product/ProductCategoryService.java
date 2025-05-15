package com.movauy.mova.service.product;

import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.product.ProductCategoryRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final BranchRepository branchRepository;

    public List<ProductCategory> getCategoriesForBranch(Long branchId) {
        return categoryRepository.findByBranchId(branchId);
    }

    public ProductCategory createCategory(Long branchId, String name, Boolean hasIngredients, Boolean enableKitchenCommands) {
        if ("Sin categoría".equalsIgnoreCase(name.trim())) {
            throw new IllegalArgumentException("No se puede crear una categoría llamada 'Sin categoría'.");
        }

        categoryRepository.findByBranchIdAndName(branchId, name.trim()).ifPresent(existing -> {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre en esta sucursal.");
        });

        ProductCategory category = new ProductCategory();
        category.setName(name.trim());
        category.setBranch(branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada")));
        category.setHasIngredients(hasIngredients != null && hasIngredients);
        category.setEnableKitchenCommands(enableKitchenCommands != null && enableKitchenCommands);
        return categoryRepository.save(category);
    }

    @Transactional
    public ProductCategory updateCategory(Long id, Long branchId, String newName, Boolean hasIngredients, Boolean enableKitchenCommands) {
        ProductCategory existing = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        if (!existing.getBranch().getId().equals(branchId)) {
            throw new SecurityException("No tienes permiso para modificar esta categoría");
        }

        if ("Sin categoría".equalsIgnoreCase(newName.trim())) {
            throw new IllegalArgumentException("No se puede renombrar a 'Sin categoría'.");
        }

        if (!existing.getName().equalsIgnoreCase(newName.trim())) {
            categoryRepository.findByBranchIdAndName(branchId, newName.trim()).ifPresent(other -> {
                throw new IllegalArgumentException("Ya existe otra categoría con ese nombre en esta sucursal.");
            });
        }

        existing.setName(newName.trim());
        existing.setHasIngredients(hasIngredients != null && hasIngredients);
        existing.setEnableKitchenCommands(enableKitchenCommands != null && enableKitchenCommands);
        return categoryRepository.save(existing);
    }

    @Transactional
    public void deleteCategory(Long id, Long branchId) {
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        if (!category.getBranch().getId().equals(branchId)) {
            throw new SecurityException("No tienes permiso para eliminar esta categoría");
        }

        // Obtener o crear "Sin categoría"
        ProductCategory defaultCategory = categoryRepository
                .findByBranchIdAndName(branchId, "Sin categoría")
                .orElseGet(() -> {
                    ProductCategory nueva = new ProductCategory();
                    nueva.setName("Sin categoría");
                    nueva.setBranch(category.getBranch());
                    return categoryRepository.save(nueva);
                });

        // Reasignar productos
        List<Product> productos = productRepository.findByCategoryId(id);
        for (Product p : productos) {
            p.setCategory(defaultCategory);
        }
        productRepository.saveAll(productos);

        categoryRepository.deleteById(id);
    }

    public ProductCategory getById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Categoría no encontrada"));
    }
}
