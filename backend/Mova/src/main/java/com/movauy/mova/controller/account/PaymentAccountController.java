package com.movauy.mova.controller.account;

import com.movauy.mova.dto.PaymentRequestDTO;
import com.movauy.mova.dto.PaymentResponseDTO;
import com.movauy.mova.service.account.PaymentAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/accounts/{accountId}/payments")
@RequiredArgsConstructor
public class PaymentAccountController {

    private final PaymentAccountService service;

    /**
     * Registra un pago para la cuenta y, si closeAfter == true, 
     * el servicio cerrará la cuenta si corresponde.
     */
    @PostMapping
    public ResponseEntity<PaymentResponseDTO> addPayment(
            @PathVariable Long accountId,
            @RequestBody PaymentRequestDTO req
    ) {
        var payment = service.addPayment(
            accountId,
            req.getAmount(),
            req.getPayerName(),
            req.getCloseAfter()     // ← nuevo boolean
        );
        return ResponseEntity.ok(PaymentResponseDTO.from(payment));
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponseDTO>> listPayments(@PathVariable Long accountId) {
        var pagos = service.getPaymentsForAccount(accountId)
                          .stream()
                          .map(PaymentResponseDTO::from)
                          .collect(Collectors.toList());
        return ResponseEntity.ok(pagos);
    }

    /**
     * Divide el total de la cuenta en N partes iguales.
     */
    @GetMapping("/share")
    public ResponseEntity<BigDecimal> getShare(
            @PathVariable Long accountId,
            @RequestParam int people
    ) {
        BigDecimal share = service.getSharePerPerson(accountId, people);
        return ResponseEntity.ok(share);
    }
}
