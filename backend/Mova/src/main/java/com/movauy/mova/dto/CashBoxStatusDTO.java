package com.movauy.mova.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CashBoxStatusDTO {
  private boolean open;
  private String code;
}