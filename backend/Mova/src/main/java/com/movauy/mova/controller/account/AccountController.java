// src/main/java/com/movauy/mova/controller/account/AccountController.java
package com.movauy.mova.controller.account;

import com.movauy.mova.dto.AccountCreateDTO;
import com.movauy.mova.dto.AccountItemDTO;
import com.movauy.mova.dto.AccountItemPaymentDTO;
import com.movauy.mova.dto.AccountResponseDTO;
import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.PayItemsRequestDTO;
import com.movauy.mova.dto.PaymentRequestDTO;
import com.movauy.mova.dto.SaleDTO;
import com.movauy.mova.dto.SplitStatusDTO;
import com.movauy.mova.model.account.Account;
import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.service.account.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

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

    @PutMapping("/{id}/close")
    public ResponseEntity<SaleDTO> closeAccount(
            @PathVariable Long id,
            @RequestHeader("Authorization") String rawToken
    ) {
        SaleDTO saleDto = accountService.closeAccount(id, rawToken);
        return ResponseEntity.ok(saleDto);
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
    @PostMapping("/{id}/payments/items")
    public ResponseEntity<OrderDTO> payItems(
            @PathVariable Long id,
            @RequestBody PayItemsRequestDTO body
    ) {
        OrderDTO receipt = accountService.payItemsAndBuildReceipt(id, body);
        return ResponseEntity.ok(receipt);
    }

    // ——— Pago parcial (split) + recibo ———
    @PostMapping("/{id}/payments")
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
            @RequestHeader("Authorization") String token
    ) {
        OrderDTO receipt = accountService.closeAccountAndBuildReceipt(id, token);
        return ResponseEntity.ok(receipt);
    }

}
