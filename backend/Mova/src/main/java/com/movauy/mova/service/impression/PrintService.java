package com.movauy.mova.service.impression;

import org.springframework.stereotype.Service;
import com.movauy.mova.model.sale.Sale;

@Service
public class PrintService {

    /**
     * Construye un string CPCL para imprimir la orden.
     */
    public String buildCpclTicket(Sale sale) {
        StringBuilder sb = new StringBuilder();
        // Formato: ! <rotation> <width> <height> <copies>
        sb.append("! 0 200 200 1\r\n");
        sb.append("TEXT 4 0 20 20 Pedido: ").append(sale.getId()).append("\r\n");
        sb.append("TEXT 4 0 20 60 Total: ").append(sale.getTotalAmount()).append("\r\n");
        // … más líneas según tu diseño …
        sb.append("FORM\r\n");
        sb.append("PRINT\r\n");
        return sb.toString();
    }
}

