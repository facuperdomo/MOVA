package com.movauy.mova.model.account;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.ingredient.Ingredient;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // Ej: "Mesa 4", "Juan", "Barra"

    private boolean closed = false;

    @ManyToOne
    private Branch branch;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<AccountItem> items = new ArrayList<>();

    /**
     * Total de porciones en las que se va a dividir la cuenta, si se elige
     * “split”. Si es null, no se utiliza reparto parcial.
     */
    private Integer splitTotal;

    /**
     * Cuántas porciones quedan todavía pendientes por pagar. Inicialmente se
     * iguala a splitTotal cuando se crea/actualiza el split. Cada pago parcial
     * le resta 1.
     */
    private Integer splitRemaining;

    /**
     * Suma precio × cantidad de cada línea de cuenta.
     */
    public BigDecimal calculateTotal() {
        return items.stream()
                .map(item
                        -> // convertimos unitPrice (double) a BigDecimal
                        BigDecimal.valueOf(item.getUnitPrice())
                        // multiplicamos por la cantidad
                        .multiply(BigDecimal.valueOf(item.getQuantity()))
                )
                // reducimos con BigDecimal::add sobre BigDecimal
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
