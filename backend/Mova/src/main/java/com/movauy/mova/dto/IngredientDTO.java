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

    @NotBlank(message = "El nombre del ingrediente no puede estar vacío")
    private String name;

    /**
     * Crea un DTO a partir de la entidad.
     */
    public static IngredientDTO fromEntity(Ingredient ingredient) {
        if (ingredient == null) {
            return null;
        }
        return IngredientDTO.builder()
                .id(ingredient.getId())
                .name(ingredient.getName())
                .build();
    }

    /**
     * Genera una entidad a partir de este DTO. NOTA: el campo branch se asigna
     * dentro del servicio.
     */
    public Ingredient toEntity() {
        Ingredient ing = new Ingredient();
        ing.setName(this.name);
        return ing;
    }
}
