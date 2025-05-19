package com.movauy.mova.controller.impression;

import lombok.Getter;
import lombok.Setter;
import lombok.Data;

@Getter
@Setter
@Data
public class PrintMessage {
    private String b64;
    private String macAddress;
}
