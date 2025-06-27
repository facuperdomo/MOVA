package com.movauy.mova.dto;

import com.movauy.mova.model.finance.CashRegister;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CashRegisterDTO {
    private Long   id;
    private Long   boxId;
    private String boxCode;
    private Long   branchId;

    // datos de apertura
    private Double initialAmount;
    private LocalDateTime openDate;

    // datos de cierre (pueden quedar null si aún está abierta)
    private Double totalSales;
    private Double closingAmount;
    private LocalDateTime closeDate;

    // movimiento concreto: + para apertura o ingresos, – para cierre
    private Double amount;

    private LocalDateTime timestamp; // createdAt
    private Long   userId;
    private String username;

    public static CashRegisterDTO from(CashRegister cr) {
        CashRegisterDTO dto = new CashRegisterDTO();
        dto.setId(cr.getId());
        dto.setBoxId(cr.getCashBox().getId());
        dto.setBoxCode(cr.getCode());
        dto.setBranchId(cr.getBranch().getId());

        dto.setInitialAmount(cr.getInitialAmount());
        dto.setOpenDate(cr.getOpenDate());

        dto.setTotalSales(cr.getTotalSales());
        dto.setClosingAmount(cr.getClosingAmount());
        dto.setCloseDate(cr.getCloseDate());

        dto.setUserId(cr.getUser().getId());
        dto.setUsername(cr.getUser().getUsername());
        return dto;
    }
}
