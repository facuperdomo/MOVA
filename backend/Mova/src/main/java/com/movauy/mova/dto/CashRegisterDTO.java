package com.movauy.mova.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 *
 * @author Facundo
 */
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CashRegisterDTO {
    private LocalDateTime openDate;
    private LocalDateTime closeDate;
    private Double totalSales;
}

