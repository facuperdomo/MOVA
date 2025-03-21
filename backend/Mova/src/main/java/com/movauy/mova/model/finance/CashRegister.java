package com.movauy.mova.model.finance;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 *
 * @author Facundo
 */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CashRegister {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private boolean open;
    private double initialAmount;
    private double totalSales;
    private LocalDateTime openDate;
    private LocalDateTime closeDate;
}
