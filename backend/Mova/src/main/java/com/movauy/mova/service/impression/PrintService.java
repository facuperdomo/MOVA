// src/main/java/com/movauy/mova/service/impression/PrintService.java
package com.movauy.mova.service.impression;

import com.movauy.mova.dto.OrderDTO;

public interface PrintService {
    /**
     * Genera la cadena CPCL para imprimir la orden.
     * @param order datos del pedido
     * @return texto CPCL
     */
    String buildCpclTicket(OrderDTO order);
}
