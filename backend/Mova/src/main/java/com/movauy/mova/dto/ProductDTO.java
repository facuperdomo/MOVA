package com.movauy.mova.dto;

import lombok.Data;

@Data
public class ProductDTO {

    private Long id;
    private String name;
    private double price;
    private String image;
    private Long categoryId;
    private String categoryName;

    public ProductDTO(Long id, String name, double price, String image, Long categoryId, String categoryName) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.image = image;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }

}
