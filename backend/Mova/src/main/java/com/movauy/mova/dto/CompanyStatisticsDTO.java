package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompanyStatisticsDTO {
    private int totalSalesCount;
    private BigDecimal totalRevenue;
    private int totalProductsSold;
    private List<BranchStatisticsDTO> branches;

    // Nuevos campos
    private BigDecimal averageTicket;
    private int activeBranchesCount;
    private BigDecimal averageSalesPerBranch;
    private int cancelledSalesCount;
    private String topDayOfWeek;
    private Map<String, BigDecimal> revenueByCategory;
}

