// src/main/java/com/movauy/mova/service/impression/DefaultPrintService.java
package com.movauy.mova.service.impression;

import com.movauy.mova.dto.OrderDTO;
import org.springframework.stereotype.Service;

@Service
public class DefaultPrintService implements PrintService {

    @Override
    public String buildCpclTicket(OrderDTO o) {
        StringBuilder cpcl = new StringBuilder();
        cpcl.append("! 0 200 200 400 1\n");
        cpcl.append("TEXT 4 0 10 10 Order: ").append(o.getId()).append("\n");
        cpcl.append("TEXT 4 0 10 40 Total: ").append(o.getTotalAmount()).append("\n");
        cpcl.append("FORM\n");
        cpcl.append("PRINT\n");
        return cpcl.toString();
    }
}
