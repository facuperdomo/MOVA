package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SplitStatusDTO {

    /** Número total de porciones originalmente */
    private int total;

    /** Cuántas porciones quedan por pagar */
    private int remaining;

    /** Monto ya pagado hasta ahora */
    private BigDecimal paidMoney;

    /** Total actual de la cuenta */
    private BigDecimal currentTotal;

    /** Monto que debe pagar cada persona de lo que queda */
    private BigDecimal share;

    /** Detalle de pagos por ítem, si lo necesitas */
    private List<ItemPaymentInfoDTO> itemPayments;

    /**
     * Fábrica para crear una instancia de SplitStatusDTO
     */
    public static SplitStatusDTO of(int total,
                                    int remaining,
                                    BigDecimal paidMoney,
                                    BigDecimal currentTotal,
                                    BigDecimal share,
                                    List<ItemPaymentInfoDTO> itemPayments) {
        return SplitStatusDTO.builder()
                .total(total)
                .remaining(remaining)
                .paidMoney(paidMoney)
                .currentTotal(currentTotal)
                .share(share)
                .itemPayments(itemPayments)
                .build();
    }
}
