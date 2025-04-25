package com.movauy.mova.service.ingredient;

import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.ingredient.IngredientRepository;
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

    /**
     * Lista todos los ingredientes de la empresa extraída del token.
     */
    public List<Ingredient> listForCurrentCompany(String bearerToken) {
        Long companyId = authService.getCompanyIdFromToken(bearerToken);
        return repo.findAllByCompanyId(companyId);
    }

    /**
     * Crea un nuevo ingrediente asociado a la empresa del token, pero cargando
     * el User sin datos sensibles.
     */
    public Ingredient createForCurrentCompany(String bearerToken, Ingredient ingredient) {
        Long companyId = authService.getCompanyIdFromToken(bearerToken);
        User company = authService.getUserById(companyId);
        ingredient.setCompany(company);
        return repo.save(ingredient);
    }

    /**
     * Borra un ingrediente sólo si pertenece a la empresa del token.
     */
    public void deleteForCurrentCompany(String bearerToken, Long ingredientId) {
        Long companyId = authService.getCompanyIdFromToken(bearerToken);
        Ingredient ing = repo.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingrediente no encontrado"));
        if (!ing.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("No tienes permiso para eliminar este ingrediente");
        }
        repo.delete(ing);
    }
    
}
