package com.movauy.mova.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Builder;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Long id;
    private Double totalAmount;
    private String dateTime;
    private String paymentMethod;
    
    @Builder.Default
    private List<SaleItemDTO> items = new ArrayList<>();
    
    private String branchName;
    private String branchRut;       
    private String branchAddress;
    
    private String companyName;
}
