package com.movauy.mova.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentRequestDTO {
    private String code;
    private BigDecimal amount;
    private String payerName;
    private String paymentMethod;
    private Boolean closeAfter;
}
