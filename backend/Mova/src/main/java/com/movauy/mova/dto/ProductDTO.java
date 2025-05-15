package com.movauy.mova.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductDTO {

    private Long id;
    private String name;
    private double price;
    private String image;
    private Long categoryId;
    private String categoryName;

    private boolean enableIngredients;
    private List<IngredientDTO> ingredients;
    private Long ownerCompanyId;
}
