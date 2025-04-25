package com.movauy.mova.service.product;

import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
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
    private final AuthService authService;

    public List<ProductCategory> getCategoriesForCompany(String token) {
        String companyId = authService.getCompanyIdFromToken(token).toString();
        return categoryRepository.findByCompanyId(companyId);
    }

    public ProductCategory createCategory(String token, String name, Boolean hasIngredients) {
        String companyId = authService.getCompanyIdFromToken(token).toString();

        if ("Sin categoría".equalsIgnoreCase(name.trim())) {
            throw new IllegalArgumentException("No se puede crear una categoría llamada 'Sin categoría'.");
        }

        ProductCategory category = new ProductCategory();
        category.setName(name);
        category.setCompanyId(companyId);
        category.setHasIngredients(hasIngredients != null && hasIngredients);
        return categoryRepository.save(category);
    }

    /**
     * Actualiza el nombre y el flag hasIngredients de una categoría existente.
     *
     * @param id             el id de la categoría a actualizar
     * @param token          el token de autorización (se extrae la empresa)
     * @param newName        el nuevo nombre de la categoría
     * @param hasIngredients el nuevo valor de hasIngredients
     * @return la categoría actualizada
     */
    @Transactional
    public ProductCategory updateCategory(Long id,
                                          String token,
                                          String newName,
                                          Boolean hasIngredients) {
        String companyId = authService.getCompanyIdFromToken(token).toString();

        // 1) recuperar la categoría y validar empresa
        ProductCategory existing = categoryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        if (!existing.getCompanyId().equals(companyId)) {
            throw new SecurityException("No tienes permiso para modificar esta categoría");
        }

        // 2) prohibir renombrar a "Sin categoría" (si quieres mantener esa lógica)
        if ("Sin categoría".equalsIgnoreCase(newName.trim())) {
            throw new IllegalArgumentException("No se puede renombrar a 'Sin categoría'.");
        }

        // 3) actualizar campos
        existing.setName(newName.trim());
        existing.setHasIngredients(hasIngredients != null && hasIngredients);

        // 4) guardar y devolver
        return categoryRepository.save(existing);
    }
    
    @Transactional
    public void deleteCategory(Long id, String token) {
        String companyId = authService.getCompanyIdFromToken(token).toString();
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        if (!category.getCompanyId().equals(companyId)) {
            throw new SecurityException("No tienes permiso para eliminar esta categoría");
        }

        // Buscar o crear categoría por defecto
        ProductCategory defaultCategory = categoryRepository
                .findByCompanyIdAndName(companyId, "Sin categoría")
                .orElseGet(() -> {
                    ProductCategory nueva = new ProductCategory();
                    nueva.setName("Sin categoría");
                    nueva.setCompanyId(companyId);
                    return categoryRepository.save(nueva);
                });

        // Reasignar productos a la categoría por defecto
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
