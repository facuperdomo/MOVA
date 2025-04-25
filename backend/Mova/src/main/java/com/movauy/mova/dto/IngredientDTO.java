// src/main/java/com/movauy/mova/dto/IngredientDTO.java
package com.movauy.mova.dto;

import com.movauy.mova.model.ingredient.Ingredient;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IngredientDTO {
    private Long id;

    @NotBlank(message = "Name must not be blank")
    private String name;

    /**
     * Crea un DTO a partir de la entidad.
     */
    public static IngredientDTO fromEntity(Ingredient ingredient) {
        return IngredientDTO.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .build();
    }

    /**
     * Genera una entidad a partir de este DTO.
     * NOTA: el campo company se asigna en el servicio.
     */
    public Ingredient toEntity() {
        Ingredient ing = new Ingredient();
        ing.setName(this.name);
        return ing;
    }
}
