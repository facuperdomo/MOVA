// src/main/java/com/movauy/mova/model/company/Company.java
package com.movauy.mova.model.company;

import com.fasterxml.jackson.annotation.JsonManagedReference;  // <-- nueva import
import jakarta.persistence.*;
import lombok.*;
import com.movauy.mova.model.branch.Branch;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String contactEmail;

    private String contactPhone;

    private String logoUrl;

    /**
     * Todas las sucursales de esta empresa. Cuando borremos la empresa, JPA
     * eliminará en cascada sus branches (y a su vez los users).
     */
    @OneToMany(
            mappedBy = "company",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @JsonManagedReference    // evita volver a serializar, rompe el ciclo
    @Builder.Default
    private List<Branch> branches = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;
}
