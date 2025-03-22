package com.movauy.mova.controller.product;

import com.movauy.mova.dto.ProductDTO;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.user.User;
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
    private final AuthService authService;
    
    /**
     * Obtiene la lista de productos de la empresa del usuario autenticado.
     */
    @GetMapping
    public ResponseEntity<List<ProductDTO>> getProducts(@RequestHeader("Authorization") String token) {
        // Se asume que authService.getCompanyIdFromToken(token) extrae el id de la empresa.
        Long companyId = authService.getCompanyIdFromToken(token);
        // Se convierte a Integer para comparar con el id de User.
        return ResponseEntity.ok(productService.getProductsByCompany(companyId.intValue()));
    }

    /**
     * Agrega un nuevo producto para la empresa del usuario autenticado.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> addProduct(
            @RequestHeader("Authorization") String token,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam("image") MultipartFile imageFile) {

        try {
            Long companyId = authService.getCompanyIdFromToken(token);
            // Se obtiene el objeto User (compañía) a partir del companyId.
            User companyUser = authService.getUserById(companyId); // Asumimos que este método existe en authService.

            // Verificar que el usuario obtenido tenga rol COMPANY.
            if (!companyUser.getRole().name().equals("COMPANY")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            byte[] imageBytes = imageFile.getBytes();
            Product product = new Product();
            product.setName(name);
            product.setPrice(price);
            product.setImage(imageBytes);
            // Se asocia el producto a la compañía.
            product.setUser(companyUser);

            return ResponseEntity.ok(productService.addProduct(product));
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Actualiza un producto existente, asegurando que pertenece a la empresa del usuario autenticado.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> updateProduct(
            @RequestHeader("Authorization") String token,
            @PathVariable Long id,
            @RequestParam("name") String name,
            @RequestParam("price") double price,
            @RequestParam(value = "image", required = false) MultipartFile imageFile) {

        try {
            Long companyId = authService.getCompanyIdFromToken(token);
            byte[] imageBytes = (imageFile != null && !imageFile.isEmpty()) ? imageFile.getBytes() : null;
            Product updatedProduct = productService.updateProduct(id, name, price, imageBytes, companyId.intValue());
            return ResponseEntity.ok(updatedProduct);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    /**
     * Elimina un producto verificando que pertenece a la empresa del usuario autenticado.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteProduct(
            @RequestHeader("Authorization") String token, @PathVariable Long id) {
        Long companyId = authService.getCompanyIdFromToken(token);
        productService.deleteProduct(id, companyId.intValue());
        return ResponseEntity.noContent().build();
    }
}
