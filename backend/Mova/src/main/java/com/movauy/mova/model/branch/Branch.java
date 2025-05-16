// src/main/java/com/movauy/mova/model/branch/Branch.java
package com.movauy.mova.model.branch;

import com.fasterxml.jackson.annotation.JsonBackReference;  // <-- nueva import
import jakarta.persistence.*;
import lombok.*;
import com.movauy.mova.model.company.Company;
import com.movauy.mova.model.user.User;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonBackReference   // ignora la propiedad company al serializar, rompe el ciclo
    private Company company;

    @Column(name = "mercadopago_access_token")
    private String mercadoPagoAccessToken;

    @Builder.Default
    @Column(nullable = false)
    private boolean enableIngredients = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean enableKitchenCommands = false;

    private String location;
    private String phone;
    
    private String rut;

    /**
     * Todos los usuarios de esta sucursal. Al borrar la sucursal, JPA eliminará
     * en cascada sus users.
     */
    @OneToMany(
            mappedBy = "branch",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<User> users = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
