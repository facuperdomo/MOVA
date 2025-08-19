// src/main/java/com/movauy/mova/repository/kitchen/KitchenOrderRepository.java
package com.movauy.mova.repository.kitchen;

import com.movauy.mova.model.kitchen.KitchenOrder;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KitchenOrderRepository extends JpaRepository<KitchenOrder, Long> {
    List<KitchenOrder> findByBranchIdAndKitchenStatusIn(Long branchId, List<OrderStatus> statuses);
    Optional<KitchenOrder> findTopByAccountIdAndKitchenStatusNot(Long accountId, OrderStatus status);
    List<KitchenOrder> findByAccountIdAndKitchenStatusIn(Long accountId, List<OrderStatus> statuses);
}
