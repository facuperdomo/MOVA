package com.movauy.mova.model.ingredient;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.movauy.mova.model.branch.Branch;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
    name = "ingredients",
    uniqueConstraints = @UniqueConstraint(columnNames = {"branch_id", "name"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ingredient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Cada ingrediente pertenece a una sucursal
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    @JsonIgnore
    private Branch branch;
}
