package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductResponseDTO {
    private Long id;
    private String name;
    private double price;
    private String image;               // Base64
    private Long categoryId;
    private String categoryName;
    private boolean enableIngredients;
    private List<IngredientDTO> ingredients;
}
