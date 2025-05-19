package com.movauy.mova.dto;

import lombok.Data;

@Data
public class CreatePrinterRequestDTO {
  private String name;
  private String macAddress;
  private String type;
  private Long deviceId;
}