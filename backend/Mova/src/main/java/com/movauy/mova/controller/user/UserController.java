package com.movauy.mova.controller.user;

import com.movauy.mova.dto.UserBasicDTO;
import com.movauy.mova.dto.UserCreateDTO;
import com.movauy.mova.dto.UserUpdateDTO;
import com.movauy.mova.mapper.UserMapper;
import com.movauy.mova.model.user.User;
import com.movauy.mova.repository.branch.BranchRepository;
import com.movauy.mova.repository.user.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepo;
    private final BranchRepository branchRepo;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<UserBasicDTO>> listByBranch(@RequestParam Long branchId) {
        List<UserBasicDTO> dtos = userRepo.findByBranch_Id(branchId).stream()
                .map(UserMapper::toUserBasicDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody UserCreateDTO dto) {
        try {
            User user = new User();
            UserMapper.toUser(dto, user, branchRepo, passwordEncoder);
            User saved = userRepo.save(user);
            return ResponseEntity
                    .status(HttpStatus.CREATED)
                    .body(UserMapper.toUserBasicDTO(saved));
        } catch (DataIntegrityViolationException ex) {
            Map<String, String> body = Map.of(
                    "error", "UsuarioDuplicado",
                    "message", "Ya existe un usuario con ese nombre en esta sucursal"
            );
            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(body);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserBasicDTO> update(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateDTO dto
    ) {
        User existing = userRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        UserMapper.toUser(dto, existing, branchRepo, passwordEncoder);
        User updated = userRepo.save(existing);
        return ResponseEntity.ok(UserMapper.toUserBasicDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
