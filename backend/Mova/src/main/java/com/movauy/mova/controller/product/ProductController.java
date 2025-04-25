package com.movauy.mova.controller.product;

import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.service.product.ProductCategoryService;
import com.movauy.mova.service.product.ProductService;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://movauy.top:8443"
})
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<List<?>> getProducts(
            @RequestHeader("Authorization") String token
    ) {
        Long companyId = authService.getCompanyIdFromToken(token);
        return ResponseEntity.ok(
                productService.getProductsByCompany(companyId.intValue())
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> addProduct(
            @RequestHeader("Authorization") String token,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("image") MultipartFile imageFile,
            @RequestParam(value = "ingredientIds", required = false) List<Long> ingredientIds
    ) throws IOException {
        Long companyId = authService.getCompanyIdFromToken(token);
        // obtenemos la categoría por si queremos validar algo, pero NO la pasamos al servicio
        ProductCategory category = categoryService.getById(categoryId);

        byte[] imageBytes = imageFile.getBytes();

        Product created = productService.addProduct(
                Product.builder()
                        .name(name)
                        .price(price)
                        .image(imageBytes)
                        .user(authService.getUserById(companyId))
                        .build(),
                categoryId,
                companyId,
                ingredientIds
        );
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> updateProduct(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile imageFile,
            @RequestParam(value = "ingredientIds", required = false) List<Long> ingredientIds
    ) throws IOException {
        Long companyId = authService.getCompanyIdFromToken(token);
        byte[] imageBytes = (imageFile != null && !imageFile.isEmpty())
                ? imageFile.getBytes()
                : null;

        // validamos que la categoría exista (opcional)
        categoryService.getById(categoryId);

        // Pasamos **solo** el categoryId, no el objeto ProductCategory
        Product updated = productService.updateProduct(
                id,
                name,
                price,
                imageBytes,
                companyId.intValue(),
                categoryId,
                ingredientIds
        );
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id
    ) {
        Long companyId = authService.getCompanyIdFromToken(token);
        productService.deleteProduct(id, companyId.intValue());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ingredients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateIngredients(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody List<Long> ingredientIds
    ) {
        Long companyId = authService.getCompanyIdFromToken(token);
        productService.updateIngredients(id, companyId.intValue(), ingredientIds);
        return ResponseEntity.ok().build();
    }
}
