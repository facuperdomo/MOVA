package com.movauy.mova.model.product;

import com.movauy.mova.model.user.User;  // Importar la entidad Company
import jakarta.persistence.*;
import lombok.*;

/**
 * Representa un producto que se vende en el sistema
 * 
 * @author Facundo
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private double price;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] image;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)  // Relaciona el producto con un usuario
    private User user;  // Usuario que creó el producto
}
