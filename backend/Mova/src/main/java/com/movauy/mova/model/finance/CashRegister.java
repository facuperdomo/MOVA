package com.movauy.mova.model.finance;

import com.movauy.mova.model.user.User;
import com.movauy.mova.model.branch.Branch;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "cash_register")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class CashRegister {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cash_box_id", nullable = false)
    private CashBox cashBox;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;

    @Column(nullable = false, length = 50)
    private String code;

    /** monto inicial depositado al abrir */
    @Column(name = "initial_amount", nullable = false)
    private Double initialAmount;

    /** total vendido (se calcula al cerrar) */
    @Column(name = "total_sales", nullable = true)
    private Double totalSales;

    /** monto que efectivamente cierras */
    @Column(name = "closing_amount", nullable = true)
    private Double closingAmount;

    /** fecha/hora de apertura */
    @Column(name = "open_date", nullable = false)
    private LocalDateTime openDate;

    /** fecha/hora de cierre */
    @Column(name = "close_date", nullable = true)
    private LocalDateTime closeDate;

    /** usuario que abrió/la cerró */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}

