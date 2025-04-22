package com.movauy.mova.controller.product;

import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.repository.product.ProductCategoryRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.service.product.ProductCategoryService;
import com.movauy.mova.service.user.AuthService;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<ProductCategory>> getCategories(@RequestHeader("Authorization") String token) {
        List<ProductCategory> categories = categoryService.getCategoriesForCompany(token);
        return ResponseEntity.ok(categories);
    }

    @PostMapping
    public ResponseEntity<ProductCategory> createCategory(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        ProductCategory category = categoryService.createCategory(token, name.trim());
        return ResponseEntity.ok(category);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id) {
        categoryService.deleteCategory(id, token);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/has-products")
    public ResponseEntity<Map<String, Boolean>> hasProducts(@PathVariable Long id, @RequestHeader("Authorization") String token) {
        String companyId = authService.getCompanyIdFromToken(token).toString();
        ProductCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Categoría no encontrada"));

        if (!category.getCompanyId().equals(companyId)) {
            throw new SecurityException("No tienes permiso para acceder a esta categoría");
        }

        boolean hasProducts = productRepository.existsByCategoryId(id);
        Map<String, Boolean> response = new HashMap<>();
        response.put("hasProducts", hasProducts);
        return ResponseEntity.ok(response);
    }

}
