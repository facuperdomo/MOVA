package com.movauy.mova.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Getter
@Builder
public class DeviceDTO {
  private Long id;
  private String name;
  private String bridgeUrl;
  private Long branchId;
  private String uuid;
}