package com.movauy.mova.controller.ingredient;

import com.movauy.mova.dto.IngredientDTO;
import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.service.ingredient.IngredientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@CrossOrigin(origins = {
    "http://localhost:3000",
    "https://movauy.top",
    "https://movauy.top:8443"
})
@RestController
@RequestMapping(path = "/api/ingredients", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Validated
public class IngredientController {

    private final IngredientService ingredientService;

    /**
     * List all ingredients for the current branch.
     */
    @GetMapping
    public ResponseEntity<List<IngredientDTO>> listIngredients(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization
    ) {
        List<Ingredient> entities = ingredientService.listForCurrentBranch(authorization);
        List<IngredientDTO> dtos = entities.stream()
                .map(IngredientDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Create a new ingredient under the current branch.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<IngredientDTO> createIngredient(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody IngredientDTO dto
    ) {
        Ingredient toCreate = dto.toEntity();
        Ingredient created = ingredientService.createForCurrentBranch(authorization, toCreate);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();

        IngredientDTO responseDto = IngredientDTO.fromEntity(created);
        return ResponseEntity.created(location).body(responseDto);
    }

    /**
     * Delete an ingredient by ID if it belongs to the current branch.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIngredient(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long id
    ) {
        ingredientService.deleteForCurrentBranch(authorization, id);
    }
}
