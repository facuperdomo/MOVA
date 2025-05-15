package com.movauy.mova.service.ingredient;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.repository.ingredient.IngredientRepository;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository repo;
    private final AuthService authService;
    private final BranchRepository branchRepository;

    /**
     * Lista todos los ingredientes de la sucursal del token.
     */
    public List<Ingredient> listForCurrentBranch(String bearerToken) {
        Long branchId = authService.getUserBasicFromToken(bearerToken).getBranchId();
        return repo.findAllByBranchId(branchId);
    }

    /**
     * Crea un nuevo ingrediente para la sucursal actual,
     * validando que no haya uno con el mismo nombre.
     */
    public Ingredient createForCurrentBranch(String bearerToken, Ingredient ingredient) {
        Long branchId = authService.getUserBasicFromToken(bearerToken).getBranchId();
        String trimmedName = ingredient.getName().trim();

        // Verificar duplicado
        repo.findByBranchIdAndName(branchId, trimmedName)
            .ifPresent(existing -> {
                throw new IllegalArgumentException("Ya existe un ingrediente con ese nombre en esta sucursal.");
            });

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));

        ingredient.setName(trimmedName);
        ingredient.setBranch(branch);
        return repo.save(ingredient);
    }

    /**
     * Elimina un ingrediente solo si pertenece a la sucursal actual.
     */
    public void deleteForCurrentBranch(String bearerToken, Long ingredientId) {
        Long branchId = authService.getUserBasicFromToken(bearerToken).getBranchId();
        Ingredient ing = repo.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));

        if (!ing.getBranch().getId().equals(branchId)) {
            throw new AccessDeniedException("No tienes permiso para eliminar este ingrediente.");
        }

        repo.delete(ing);
    }
}
