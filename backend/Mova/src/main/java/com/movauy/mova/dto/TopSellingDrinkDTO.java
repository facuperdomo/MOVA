package com.movauy.mova.dto;

/**
 *
 * @author Facundo
 */
public class TopSellingDrinkDTO {
    private String name;
    private Long totalSold;

    public TopSellingDrinkDTO(String name, Long totalSold) {
        this.name = name;
        this.totalSold = totalSold;
    }

    public String getName() {
        return name;
    }

    public Long getTotalSold() {
        return totalSold;
    }
}
