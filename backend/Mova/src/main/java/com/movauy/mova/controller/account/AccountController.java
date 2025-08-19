// src/main/java/com/movauy/mova/controller/account/AccountController.java
package com.movauy.mova.controller.account;

import com.movauy.mova.dto.AccountCreateDTO;
import com.movauy.mova.dto.AccountItemDTO;
import com.movauy.mova.dto.AccountItemPaymentDTO;
import com.movauy.mova.dto.AccountResponseDTO;
import com.movauy.mova.dto.KitchenOrderDTO;
import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.PayItemsRequestDTO;
import com.movauy.mova.dto.PaymentRequestDTO;
import com.movauy.mova.dto.SplitStatusDTO;
import com.movauy.mova.dto.UnitItemDTO;
import com.movauy.mova.dto.UpdateKitchenStatusDTO;
import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.account.AccountItem;
import com.movauy.mova.model.ingredient.Ingredient;
import com.movauy.mova.model.kitchen.KitchenOrder;
import com.movauy.mova.repository.account.AccountRepository;
import com.movauy.mova.service.account.AccountService;
import com.movauy.mova.service.kitchen.KitchenOrderService;
import com.movauy.mova.service.sale.SaleService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final SaleService saleService;
    private final KitchenOrderService kitchenOrderService;

    @PostMapping
    public ResponseEntity<AccountResponseDTO> createAccount(@RequestBody AccountCreateDTO dto) {
        Account account = accountService.createAccount(dto);
        return ResponseEntity.ok(AccountResponseDTO.from(account));
    }

    @GetMapping
    public ResponseEntity<List<AccountResponseDTO>> getAccounts(
            @RequestParam Long branchId,
            @RequestParam boolean closed
    ) {
        List<Account> accounts = accountService.getAccountsByBranch(branchId, closed);
        List<AccountResponseDTO> response = accounts.stream()
                .map(AccountResponseDTO::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/items")
    public ResponseEntity<AccountResponseDTO> addItem(
            @PathVariable Long id,
            @RequestBody AccountItemDTO dto
    ) {
        Account updated = accountService.addItemToAccount(id, dto);
        return ResponseEntity.ok(AccountResponseDTO.from(updated));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(AccountResponseDTO.from(accountService.getById(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        accountService.deleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Borra un item de cuenta por su ID
     */
    @DeleteMapping("/{accountId}/items/{itemId}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Long accountId,
            @PathVariable Long itemId
    ) {
        accountService.removeItemFromAccount(accountId, itemId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Actualiza la cantidad de un item en la cuenta.
     */
    @PutMapping("/{accountId}/items/{itemId}")
    public ResponseEntity<AccountResponseDTO> updateItemQuantity(
            @PathVariable Long accountId,
            @PathVariable Long itemId,
            @RequestBody AccountItemDTO dto
    ) {
        Account updated = accountService.updateItemQuantity(
                accountId,
                itemId,
                dto.getQuantity()
        );
        return ResponseEntity.ok(AccountResponseDTO.from(updated));
    }

    @PutMapping("/{id}/split")
    public ResponseEntity<Void> updateSplit(
            @PathVariable Long id,
            @RequestParam int people
    ) {
        accountService.initOrUpdateSplit(id, people);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/split/status")
    public ResponseEntity<SplitStatusDTO> splitStatus(@PathVariable Long id) {
        SplitStatusDTO status = accountService.getSplitStatus(id);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/{accountId}/items-with-payment")
    public List<AccountItemPaymentDTO> getItemsWithPayment(
            @PathVariable Long accountId
    ) {
        return accountService.getAccountItemsWithPaymentInfo(accountId);
    }

    /**
     * Este endpoint recibe un JSON con { itemIds: [...], payerName: "..." }
     * Marca cada AccountItem con paid=true y registra un PaymentAccount con la
     * suma total de (unitPrice * quantity) de esos ítems.
     */
    @PostMapping("/{accountId}/payments/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void payAccountItems(
            @PathVariable Long accountId,
            @RequestBody PayItemsRequestDTO request
    ) {
        accountService.payItemsAndRecordPayment(
                accountId,
                request.getItemIds(),
                request.getPayerName()
        );
    }

    // ——— Pago de productos sueltos + recibo ———
    @PostMapping("/{id}/payments/items/receipt")
    public ResponseEntity<OrderDTO> payItems(
            @PathVariable Long id,
            @RequestBody PayItemsRequestDTO body
    ) {
        OrderDTO receipt = accountService.payItemsAndBuildReceipt(id, body);
        return ResponseEntity.ok(receipt);
    }

    // ——— Pago parcial (split) + recibo ———
    @PostMapping("/{id}/payments/split")
    public ResponseEntity<OrderDTO> payPartial(
            @PathVariable Long id,
            @RequestBody PaymentRequestDTO body
    ) {
        OrderDTO receipt = accountService.registerPaymentAndBuildReceipt(id, body);
        return ResponseEntity.ok(receipt);
    }

    // ——— Cierre de cuenta (total) + recibo ———
    @PutMapping("/{id}/close")
    public ResponseEntity<OrderDTO> closeAccountAndPrint(
            @PathVariable Long id,
            @RequestHeader("Authorization") String token,
            @RequestBody PaymentRequestDTO req
    ) {
        OrderDTO receipt = accountService.closeAccountAndBuildReceipt(id, token, req.getCode(), req);
        return ResponseEntity.ok(receipt);
    }

    @GetMapping("/{accountId}/items")
    public ResponseEntity<List<AccountItemDTO>> getItems(@PathVariable Long accountId) {
        List<AccountItem> items = accountService.getItemsByAccountId(accountId);
        List<AccountItemDTO> dtos = items.stream()
                .map(item -> {
                    List<Long> ingrIds = item.getIngredients().stream()
                            .map(Ingredient::getId)
                            .collect(Collectors.toList());
                    return new AccountItemDTO(
                            item.getId(),
                            item.getProduct().getId(),
                            item.getQuantity(),
                            ingrIds,
                            item.isPaid(),
                            Boolean.TRUE.equals(item.isKitchenSent())
                    );
                })
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{accountId}/unit-items")
    public ResponseEntity<List<UnitItemDTO>> getUnitItems(@PathVariable Long accountId) {
        Account account = accountRepository.findByIdWithItemsAndIngredients(accountId)
                .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Cuenta no encontrada"));

        List<UnitItemDTO> flat = account.getItems().stream()
                .flatMap(item -> IntStream.range(0, item.getQuantity())
                .mapToObj(i -> new UnitItemDTO(
                item.getId(),
                item.getProduct().getId(),
                item.getProduct().getName(),
                BigDecimal.valueOf(item.getUnitPrice()),
                item.isPaid()
        ))
                )
                .collect(Collectors.toList());

        return ResponseEntity.ok(flat);
    }

    /**
     * Marca todos los unit-items de la cuenta como pagados (paid=true) sin
     * generar pago ni recibo.
     */
    @PutMapping("/{accountId}/unit-items/mark-all-paid")
    public ResponseEntity<Void> markAllUnitItemsPaid(@PathVariable Long accountId) {
        accountService.markAllUnitItemsPaid(accountId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{accountId}/send-to-kitchen")
    public ResponseEntity<KitchenOrderDTO> sendToKitchen(
            @PathVariable Long accountId,
            @RequestHeader("Authorization") String token) {

        kitchenOrderService.createFromAccount(accountId, token);
        KitchenOrderDTO snapshot = kitchenOrderService.buildAggregatedDtoForAccount(accountId);
        return ResponseEntity.ok(snapshot);
    }

    @PutMapping("/{accountId}/kitchen-status")
    public ResponseEntity<KitchenOrderDTO> updateKitchenStatusForAccount(
            @PathVariable Long accountId,
            @RequestBody UpdateKitchenStatusDTO body) {

        kitchenOrderService.updateStatusForAccount(accountId, body.getKitchenStatus());
        KitchenOrderDTO snapshot = kitchenOrderService.buildAggregatedDtoForAccount(accountId);
        return ResponseEntity.ok(snapshot);
    }

}
