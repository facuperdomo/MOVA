package com.movauy.mova.service.branch;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.finance.CashRegisterRepository;
import com.movauy.mova.repository.ingredient.IngredientRepository;
import com.movauy.mova.repository.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final IngredientRepository ingredientRepository;
    private final CashRegisterRepository cashRegisterRepository;

    // Registro con hash de contraseña
    public Branch registerBranch(Branch branch) {
        branch.setPassword(passwordEncoder.encode(branch.getPassword()));
        return branchRepository.save(branch);
    }

    // Login (devuelve el branch si las credenciales son válidas)
    public Optional<Branch> authenticate(String username, String rawPassword) {
        return branchRepository.findByUsername(username)
                .filter(branch -> passwordEncoder.matches(rawPassword, branch.getPassword()));
    }

    public List<Branch> findByCompanyId(Long companyId) {
        return branchRepository.findByCompanyId(companyId);
    }

    /**
     * Obtiene una sucursal por su id o lanza IllegalArgumentException si no existe.
     */
    public Branch findById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada: " + id));
    }
    
    @Transactional
    public Branch updateBranch(
        Long id,
        String name,
        String username,
        String rawPassword,
        String mercadoPagoAccessToken,
        boolean enableIngredients,
        boolean enableKitchenCommands,
        String location,
        String phone,
        String rut
    ) {
        Branch existing = branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));

        existing.setName(name);
        existing.setUsername(username);

        if (rawPassword != null && !rawPassword.isBlank()) {
            existing.setPassword(passwordEncoder.encode(rawPassword));
        }

        existing.setMercadoPagoAccessToken(mercadoPagoAccessToken);
        existing.setEnableIngredients(enableIngredients);
        existing.setEnableKitchenCommands(enableKitchenCommands);
        existing.setLocation(location);
        existing.setPhone(phone);
        existing.setRut(rut);

        return branchRepository.save(existing);
    }

    @Transactional
    public void deleteBranch(Long id, boolean force) {
        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Sucursal no encontrada"));

        boolean tieneUsuarios = userRepository.existsByBranch_Id(id);
        boolean tieneIngredientes = !ingredientRepository.findByBranch_Id(id).isEmpty();

        if (!force) {
            if (tieneUsuarios) {
                throw new IllegalStateException("La sucursal tiene usuarios asociados.");
            }
            if (tieneIngredientes) {
                throw new IllegalStateException("La sucursal tiene ingredientes asociados.");
            }
        } else {
            // 1) recupero IDs de usuarios
            List<Long> userIds = userRepository.findByBranch_Id(id)
                    .stream()
                    .map(User::getId)
                    .toList();
            // 2) borro cash_registers de esos usuarios
            if (!userIds.isEmpty()) {
                cashRegisterRepository.deleteByUser_IdIn(userIds);
            }
            // 3) borro usuarios e ingredientes
            userRepository.deleteByBranch_Id(id);
            ingredientRepository.deleteByBranch_Id(id);
        }

        branchRepository.delete(branch);
    }

    public long countByCompanyId(Long companyId) {
        return branchRepository.countByCompanyId(companyId);
    }

    @Transactional
    public Branch setEnabled(Long branchId, boolean enabled, boolean force) {
        // 1) Cargo la sucursal o lanzo si no existe
        Branch b = branchRepository.findById(branchId)
                .orElseThrow(() -> new IllegalArgumentException("No existe sucursal " + branchId));

        // 2) Si vamos a DESHABILITAR y no es force, solo comprobamos usuarios
        if (!enabled && !force) {
            if (userRepository.existsByBranch_Id(branchId)) {
                throw new IllegalStateException("La sucursal tiene usuarios asociados.");
            }
        }
        b.setEnabled(enabled);
        return branchRepository.save(b);
    }
}
