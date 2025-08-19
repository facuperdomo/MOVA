// src/main/java/com/movauy/mova/service/kitchen/KitchenOrderService.java
package com.movauy.mova.service.kitchen;

import com.movauy.mova.dto.KitchenItemDTO;
import com.movauy.mova.dto.KitchenOrderDTO;
import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.account.AccountItem;
import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.model.kitchen.KitchenOrder;
import com.movauy.mova.model.kitchen.KitchenOrderItem;
import com.movauy.mova.model.product.Product;
import com.movauy.mova.model.sale.Sale.OrderStatus;
import com.movauy.mova.repository.account.AccountItemRepository;
import com.movauy.mova.repository.account.AccountRepository;
import com.movauy.mova.repository.kitchen.KitchenOrderRepository;
import com.movauy.mova.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KitchenOrderService {

    private final KitchenOrderRepository repo;
    private final AuthService authService;
    private final SimpMessagingTemplate ws;
    private final AccountRepository accountRepository;
    private final AccountItemRepository accountItemRepository;

    // ---------------- util de ranking de estados ----------------
    private int rank(OrderStatus s) {
        if (s == null) {
            return 0;
        }
        return switch (s) {
            case SENT_TO_KITCHEN ->
                1;
            case PREPARING ->
                2;
            case READY ->
                3;
            case COMPLETED ->
                4;
            // Si en el futuro agregas estados, caerán acá:
            default ->
                0;
        };
    }

    // ---------------- PUBLIC API ----------------
    /**
     * Crea una KitchenOrder con las líneas nuevas NO enviadas, marca
     * kitchenSent=true y emite por WS un SNAPSHOT agregado de TODO lo pendiente
     * para esa cuenta.
     */
    @Transactional
    public KitchenOrder createFromAccount(Long accountId, String token) {
        Account acc = accountRepository.findByIdWithItemsAndIngredients(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        // 1) Ítems de cocina NO enviados (desduplicados por itemId por el fetch-join)
        List<AccountItem> raw = acc.getItems().stream()
                .filter(ai -> ai.getProduct().getCategory().isEnableKitchenCommands())
                .filter(ai -> !Boolean.TRUE.equals(ai.isKitchenSent()))
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(AccountItem::getId, Function.identity(), (a, b) -> a, LinkedHashMap::new),
                        m -> new ArrayList<>(m.values())
                ));

        if (raw.isEmpty()) {
            return null;
        }

        // 2) Agrupar por (productId + ingredientIds ordenados) y crear UNA KitchenOrder con esas líneas
        record Key(Long productId, List<Long> ingIds) {

        }
        Map<Key, Integer> grouped = new LinkedHashMap<>();
        for (AccountItem ai : raw) {
            List<Long> ingIds = ai.getIngredients().stream().map(Ingredient::getId).sorted().toList();
            grouped.merge(new Key(ai.getProduct().getId(), ingIds), ai.getQuantity(), Integer::sum);
        }

        KitchenOrder ko = new KitchenOrder();
        ko.setAccount(acc);
        ko.setBranch(acc.getBranch());
        ko.setDateTime(LocalDateTime.now());
        ko.setKitchenStatus(OrderStatus.SENT_TO_KITCHEN);

        grouped.forEach((key, qty) -> {
            KitchenOrderItem koi = new KitchenOrderItem();
            koi.setOrder(ko);
            Product p = raw.stream()
                    .filter(ai -> ai.getProduct().getId().equals(key.productId()))
                    .findFirst().orElseThrow().getProduct();
            koi.setProduct(p);
            koi.setQuantity(qty);
            koi.setIngredientIds(key.ingIds());
            ko.getItems().add(koi);
        });

        // 3) Marcar como enviados en la cuenta
        raw.forEach(ai -> ai.setKitchenSent(true));
        accountItemRepository.saveAll(raw);

        KitchenOrder saved = repo.save(ko);

        // 4) WS: enviar SNAPSHOT agregado tras el commit
        KitchenOrderDTO snapshot = buildAggregatedDtoForAccount(acc.getId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ws.convertAndSend("/topic/kitchen-orders", snapshot);
            }
        });

        return saved;
    }

    /**
     * Devuelve todas las KitchenOrder pendientes (sin COMPLETED) de la
     * sucursal.
     */
    @Transactional(readOnly = true)
    public List<KitchenOrder> findPendingByBranch(String token) {
        Long branchId = authService.getBranchIdFromToken(token);
        return repo.findByBranchIdAndKitchenStatusIn(
                branchId,
                List.of(OrderStatus.SENT_TO_KITCHEN, OrderStatus.PREPARING, OrderStatus.READY)
        );
    }

    /**
     * Igual que findPendingByBranch pero ya agregadas por cuenta (con
     * aggregated=true).
     */
    @Transactional(readOnly = true)
    public List<KitchenOrderDTO> findPendingByBranchAggregated(String token) {
        List<KitchenOrder> list = findPendingByBranch(token);

        // agrupar por cuenta
        Map<Long, List<KitchenOrder>> byAccount = list.stream()
                .collect(Collectors.groupingBy(ko -> ko.getAccount().getId(), LinkedHashMap::new, Collectors.toList()));

        List<KitchenOrderDTO> result = new ArrayList<>();
        for (Map.Entry<Long, List<KitchenOrder>> e : byAccount.entrySet()) {
            result.add(toAggregatedDto(e.getValue()));
        }
        return result;
    }

    /**
     * Actualiza estado (sin retroceder) y publica SNAPSHOT agregado de la
     * cuenta.
     */
    @Transactional
    public KitchenOrder updateStatusForAccount(Long accountId, OrderStatus newStatus) {
        KitchenOrder ko = repo.findTopByAccountIdAndKitchenStatusNot(accountId, OrderStatus.COMPLETED)
                .orElseThrow(() -> new IllegalArgumentException("No hay orden de cocina pendiente para la cuenta " + accountId));

        if (rank(newStatus) >= rank(ko.getKitchenStatus())) {
            ko.setKitchenStatus(newStatus);
        }
        KitchenOrder saved = repo.save(ko);

        KitchenOrderDTO snapshot = buildAggregatedDtoForAccount(accountId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ws.convertAndSend("/topic/kitchen-orders", snapshot);
            }
        });

        return saved;
    }

    // ---------------- DTO helpers ----------------
    /**
     * Snapshot agregado de TODO lo pendiente para una cuenta.
     */
    @Transactional(readOnly = true)
    public KitchenOrderDTO buildAggregatedDtoForAccount(Long accountId) {
        Account acc = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        List<KitchenOrder> list = repo.findByAccountIdAndKitchenStatusIn(
                accountId,
                List.of(OrderStatus.SENT_TO_KITCHEN, OrderStatus.PREPARING, OrderStatus.READY)
        );

        if (list.isEmpty()) {
            // snapshot vacío: útil si quieres borrar de la UI al completar
            return new KitchenOrderDTO(
                    acc.getId(),
                    acc.getName(),
                    LocalDateTime.now(),
                    OrderStatus.COMPLETED,
                    List.of(),
                    true
            );
        }

        return toAggregatedDto(list);
    }

    /**
     * Convierte un conjunto de KitchenOrder de la MISMA cuenta en un DTO
     * agregado.
     */
    private KitchenOrderDTO toAggregatedDto(List<KitchenOrder> ordersOfSameAccount) {
        Account acc = ordersOfSameAccount.get(0).getAccount();

        // estado más avanzado + fecha más reciente
        OrderStatus maxStatus = ordersOfSameAccount.stream()
                .map(KitchenOrder::getKitchenStatus)
                .max(Comparator.comparingInt(this::rank)).orElse(OrderStatus.SENT_TO_KITCHEN);

        LocalDateTime lastDate = ordersOfSameAccount.stream()
                .map(KitchenOrder::getDateTime)
                .max(LocalDateTime::compareTo).orElse(LocalDateTime.now());

        // agrupar items por (productId + ingredientIds)
        record Key(Long productId, List<Long> ingIds) {

        }
        Map<Key, Integer> grouped = new LinkedHashMap<>();

        // cache para obtener el nombre del producto sin ir al repo
        Map<Long, Product> anyProductById = new HashMap<>();

        for (KitchenOrder ko : ordersOfSameAccount) {
            for (KitchenOrderItem it : ko.getItems()) {
                List<Long> ids = Optional.ofNullable(it.getIngredientIds()).orElse(List.of());
                List<Long> sorted = new ArrayList<>(ids);
                Collections.sort(sorted);
                grouped.merge(new Key(it.getProduct().getId(), sorted), it.getQuantity(), Integer::sum);
                anyProductById.putIfAbsent(it.getProduct().getId(), it.getProduct());
            }
        }

        List<KitchenItemDTO> items = grouped.entrySet().stream()
                .map(e -> {
                    Long productId = e.getKey().productId();
                    Product p = anyProductById.get(productId);
                    return new KitchenItemDTO(
                            productId,
                            p != null ? p.getName() : "#" + productId,
                            e.getValue(),
                            e.getKey().ingIds()
                    );
                }).toList();

        return new KitchenOrderDTO(
                acc.getId(),
                acc.getName(),
                lastDate,
                maxStatus,
                items,
                true // <— snapshot agregado
        );
    }

    /**
     * DTO unitario (no agregado). Aún útil si en algún lado necesitas la orden
     * “cruda”.
     */
    public KitchenOrderDTO toDTO(KitchenOrder k) {
        return new KitchenOrderDTO(
                k.getAccount().getId(),
                k.getAccount().getName(),
                k.getDateTime(),
                k.getKitchenStatus(),
                k.getItems().stream().map(this::toItemDTO).toList(),
                false
        );
    }

    private KitchenItemDTO toItemDTO(KitchenOrderItem it) {
        Product p = it.getProduct();
        List<Long> ingrIds = Optional.ofNullable(it.getIngredientIds()).map(ArrayList::new).orElseGet(ArrayList::new);
        return new KitchenItemDTO(p.getId(), p.getName(), it.getQuantity(), ingrIds);
    }
}
