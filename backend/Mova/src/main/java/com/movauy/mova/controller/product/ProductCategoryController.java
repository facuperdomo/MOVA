package com.movauy.mova.controller.product;

import com.movauy.mova.dto.CategoryDTO;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.repository.product.ProductCategoryRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.service.product.ProductCategoryService;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://movauy.top:8443"
})
public class ProductCategoryController {

    private final ProductCategoryService categoryService;
    private final AuthService authService;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(@RequestHeader("Authorization") String token) {
        Long branchId = authService.getBranchIdFromToken(token);
        List<ProductCategory> categories = categoryService.getCategoriesForBranch(branchId);

        List<CategoryDTO> dtos = categories.stream()
                .map(c -> new CategoryDTO(
                c.getId(),
                c.getName(),
                c.isHasIngredients(),
                c.isEnableKitchenCommands()
        ))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<ProductCategory> createCategory(@RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        boolean hasIngredients = Boolean.TRUE.equals(body.get("hasIngredients"));
        boolean enableKitchenCommands = Boolean.TRUE.equals(body.get("enableKitchenCommands"));
        Long branchId = authService.getBranchIdFromToken(token);

        ProductCategory category = categoryService.createCategory(
                branchId, name.trim(), hasIngredients, enableKitchenCommands
        );
        return ResponseEntity.ok(category);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductCategory> updateCategory(@RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        boolean hasIngredients = Boolean.TRUE.equals(body.get("hasIngredients"));
        boolean enableKitchenCommands = Boolean.TRUE.equals(body.get("enableKitchenCommands"));
        Long branchId = authService.getBranchIdFromToken(token);

        ProductCategory updated = categoryService.updateCategory(id, branchId, name.trim(), hasIngredients, enableKitchenCommands);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        Long branchId = authService.getBranchIdFromToken(token);
        categoryService.deleteCategory(id, branchId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/has-products")
    public ResponseEntity<Map<String, Boolean>> hasProducts(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        Long branchId = authService.getBranchIdFromToken(token);
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        if (!category.getBranch().getId().equals(branchId)) {
            throw new SecurityException("No tienes permiso para acceder a esta categoría");
        }

        boolean hasProducts = productRepository.existsByCategoryId(id);
        return ResponseEntity.ok(Map.of("hasProducts", hasProducts));
    }
}
