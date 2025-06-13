package com.movauy.mova.dto;

import com.movauy.mova.model.account.PaymentAccount;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class PaymentResponseDTO {
    private Long id;
    private BigDecimal amount;
    private String payerName;
    private PaymentAccount.Status status;
    private LocalDateTime paidAt;

    public static PaymentResponseDTO from(PaymentAccount p) {
        PaymentResponseDTO dto = new PaymentResponseDTO();
        dto.id        = p.getId();
        dto.amount    = p.getAmount();
        dto.payerName = p.getPayerName();
        dto.status    = p.getStatus();
        dto.paidAt    = p.getPaidAt();
        return dto;
    }
}