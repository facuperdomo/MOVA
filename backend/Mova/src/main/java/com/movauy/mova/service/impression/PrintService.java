// src/main/java/com/movauy/mova/service/impression/PrintService.java
package com.movauy.mova.service.impression;

import com.movauy.mova.dto.OrderDTO;
import java.io.IOException;

public interface PrintService {

    /** Genera un array de bytes con comandos ESC/POS para imprimir el ticket */
    byte[] buildEscPosTicket(OrderDTO o) throws IOException;
}
