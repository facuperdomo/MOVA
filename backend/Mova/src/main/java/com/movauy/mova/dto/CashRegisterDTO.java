package com.movauy.mova.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author Facundo
 */
@Getter
@Setter
public class CashRegisterDTO {
    private LocalDateTime openDate;
    private LocalDateTime closeDate;
    private Double totalSales;

    public CashRegisterDTO(LocalDateTime openDate, LocalDateTime closeDate, Double totalSales) {
        this.openDate = openDate;
        this.closeDate = closeDate;
        this.totalSales = totalSales;
    }

}
