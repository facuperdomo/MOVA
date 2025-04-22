package com.movauy.mova.controller.product;

import com.movauy.mova.dto.ProductDTO;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.model.user.User;
import com.movauy.mova.service.product.ProductCategoryService;
import com.movauy.mova.service.product.ProductService;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final AuthService authService;

    @GetMapping
    public ResponseEntity<List<ProductDTO>> getProducts(@RequestHeader("Authorization") String token) {
        Long companyId = authService.getCompanyIdFromToken(token);
        return ResponseEntity.ok(productService.getProductsByCompany(companyId.intValue()));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> addProduct(
            @RequestHeader("Authorization") String token,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam("image") MultipartFile imageFile) {

        try {
            Long companyId = authService.getCompanyIdFromToken(token);
            User companyUser = authService.getUserById(companyId);

            if (!companyUser.getRole().name().equals("COMPANY")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            byte[] imageBytes = imageFile.getBytes();
            ProductCategory category = categoryService.getById(categoryId); // Asegurate de tener este método

            Product product = new Product();
            product.setName(name);
            product.setPrice(price);
            product.setImage(imageBytes);
            product.setUser(companyUser);
            product.setCategory(category); // Asociar categoría

            return ResponseEntity.ok(productService.addProduct(product, categoryId));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> updateProduct(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("categoryId") Long categoryId,
            @RequestParam(value = "image", required = false) MultipartFile imageFile) {

        try {
            Long companyId = authService.getCompanyIdFromToken(token);
            byte[] imageBytes = (imageFile != null && !imageFile.isEmpty()) ? imageFile.getBytes() : null;
            ProductCategory category = categoryService.getById(categoryId);

            Product updatedProduct = productService.updateProduct(id, name, price, imageBytes, companyId.intValue(), category);
            return ResponseEntity.ok(updatedProduct);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader("Authorization") String token, @PathVariable Long id) {
        Long companyId = authService.getCompanyIdFromToken(token);
        productService.deleteProduct(id, companyId.intValue());
        return ResponseEntity.noContent().build();
    }
}
