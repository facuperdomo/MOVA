package com.movauy.mova.model.finance;

import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.user.User;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
}
