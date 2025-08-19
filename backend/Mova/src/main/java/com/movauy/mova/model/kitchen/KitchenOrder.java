// src/main/java/com/movauy/mova/model/kitchen/KitchenOrder.java
package com.movauy.mova.model.kitchen;

import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity @Getter @Setter
@Table(name = "kitchen_order")
public class KitchenOrder {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY) private Branch branch;
    @ManyToOne(fetch = FetchType.LAZY) private Account account;

    private LocalDateTime dateTime;

    @Enumerated(EnumType.STRING)
    private OrderStatus kitchenStatus = OrderStatus.SENT_TO_KITCHEN;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KitchenOrderItem> items = new ArrayList<>();
}
