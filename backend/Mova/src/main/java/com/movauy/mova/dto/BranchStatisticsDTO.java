package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BranchStatisticsDTO {

    private Long branchId;
    private String branchName;
    private int totalSalesCount;
    private BigDecimal totalRevenue;
    private int totalProductsSold;

}
