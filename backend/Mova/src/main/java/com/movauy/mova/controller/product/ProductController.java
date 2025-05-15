package com.movauy.mova.controller.product;

import com.movauy.mova.dto.IngredientDTO;
import com.movauy.mova.dto.ProductDTO;
import com.movauy.mova.dto.ProductResponseDTO;
import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.product.ProductCategory;
import com.movauy.mova.model.user.User;
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
import java.util.stream.Collectors;

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
    public ResponseEntity<List<ProductResponseDTO>> getProducts(@RequestHeader("Authorization") String token) {
        Long branchId = authService.getBranchIdFromToken(token);
        List<ProductDTO> lista = productService.getProductsByBranch(branchId);

        List<ProductResponseDTO> dtos = lista.stream().map(p -> {
            List<IngredientDTO> ings = p.getIngredients().stream()
                    .map(i -> new IngredientDTO(i.getId(), i.getName()))
                    .collect(Collectors.toList());

            return new ProductResponseDTO(
                    p.getId(), p.getName(), p.getPrice(), p.getImage(),
                    p.getCategoryId(), p.getCategoryName(),
                    p.isEnableIngredients(), ings
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
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
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (price <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Long branchId = authService.getBranchIdFromToken(token);
        categoryService.getById(categoryId); // Validar existencia

        byte[] imageBytes = imageFile.getBytes();

        Product created = productService.addProduct(
                Product.builder()
                        .name(name.trim())
                        .price(price)
                        .image(imageBytes)
                        .branch(productService.getBranch(branchId))
                        .build(),
                categoryId,
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
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        if (price <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Long branchId = authService.getBranchIdFromToken(token);
        categoryService.getById(categoryId); // Validar existencia

        byte[] imageBytes = (imageFile != null && !imageFile.isEmpty()) ? imageFile.getBytes() : null;

        Product updated = productService.updateProduct(id, name.trim(), price, imageBytes, branchId, categoryId, ingredientIds);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(@RequestHeader("Authorization") String token, @PathVariable Long id) {
        Long branchId = authService.getBranchIdFromToken(token);
        productService.deleteProduct(id, branchId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/ingredients")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> updateIngredients(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestBody List<Long> ingredientIds
    ) {
        Long branchId = authService.getBranchIdFromToken(token);
        productService.updateIngredients(id, branchId, ingredientIds);
        return ResponseEntity.ok().build();
    }
}
