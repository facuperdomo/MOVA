package com.movauy.mova.dto;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    public enum ReceiptType { PRODUCT, PARTIAL, FULL }

    private Long             id;
    private Double           totalAmount;
    private String           dateTime;
    private String           paymentMethod;

    private ReceiptType      receiptType;
    private String           payerName;

    @Builder.Default
    private List<SaleItemDTO> items = new ArrayList<>();

    private String branchName;
    private String branchRut;
    private String branchAddress;

    private String companyName;
}
