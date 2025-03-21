package com.movauy.mova.model.product;

import jakarta.persistence.*;
import lombok.*;

/**
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

    private String name;
    private double price;
    
    @Lob  // Indica que la imagen se almacena como BLOB
    @Column(columnDefinition = "LONGBLOB")
    private byte[] image;
}
