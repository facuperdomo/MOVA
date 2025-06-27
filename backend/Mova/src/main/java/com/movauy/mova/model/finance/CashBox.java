package com.movauy.mova.model.finance;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "cash_box")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CashBox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Sucursal a la que pertenece */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    /** Código para distinguir cajas (DEFAULT, FRONT, BACK, etc.) */
    @Column(nullable = false, length = 50)
    private String code;

    /** Nombre legible de la caja */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Flag que indica si la caja está abierta (true) o cerrada (false).
     * Será el único campo de estado.
     */
    @Column(name = "is_open", nullable = false)
    private Boolean isOpen;
    
    /** si la caja está activa (no "borrada") */
    private Boolean enabled = true;
    
    /** Usuarios asignados a esta caja */
    @OneToMany(mappedBy = "assignedBox")
    @Builder.Default                    
    private Set<User> assignedUsers = new HashSet<>();
}