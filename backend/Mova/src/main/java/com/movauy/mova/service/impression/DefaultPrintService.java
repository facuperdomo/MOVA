package com.movauy.mova.service.impression;

import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.SaleItemDTO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class DefaultPrintService implements PrintService {

    @Override
    public byte[] buildEscPosTicket(OrderDTO o) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1) Init printer y cambiar code page a CP850
        out.write(new byte[]{0x1B, '@'});           // ESC @
        out.write(new byte[]{0x1B, 't', 0x02});     // ESC t 2 = CP850

        // 2) Encabezado centrado + negrita para nombre de empresa
        out.write(new byte[]{0x1B, 'a', 0x01});     // center
        out.write(new byte[]{0x1B, 'E', 0x01});     // bold on
        out.write(o.getCompanyName().getBytes("CP850"));
        out.write("\n".getBytes("CP850"));
        out.write(new byte[]{0x1B, 'E', 0x00});     // bold off

        // RUT y dirección centrados
        out.write(("RUT: " + o.getBranchRut() + "\n").getBytes("CP850"));
        out.write((o.getBranchAddress() + "\n\n").getBytes("CP850"));

        // 3) Alinear a la izquierda
        out.write(new byte[]{0x1B, 'a', 0x00});     // left

        // 4) Datos de sucursal, fecha/hora, ticket
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        LocalDateTime dateTime = LocalDateTime.parse(o.getDateTime()); // asegúrate que sea ISO-8601
        String datePart = dateTime.format(dateFormatter);
        String timePart = dateTime.format(timeFormatter);
        
        out.write(("Sucursal: " + o.getBranchName() + "\n").getBytes("CP850"));
        out.write(("Hora:     " + timePart + "\n").getBytes("CP850"));
        out.write(("Fecha:    " + datePart + "\n").getBytes("CP850"));
        out.write(("Ticket #: " + o.getId() + "\n").getBytes("CP850"));
        out.write("--------------------------------\n".getBytes("CP850"));

        // 5) Agrupar ítems por nombre ignorando modificaciones de ingredientes
        List<SaleItemDTO> items = o.getItems();
        Map<String, Integer> qtyMap = new LinkedHashMap<>();
        Map<String, Double> priceMap = new LinkedHashMap<>();
        // acumular
        for (SaleItemDTO item : items) {
            qtyMap.merge(item.getName(), item.getQuantity(), Integer::sum);
            // guardo precio unitario (asumo mismo precio por nombre)
            priceMap.putIfAbsent(item.getName(), item.getUnitPrice());
        }

        // imprimir líneas agrupadas
        for (Map.Entry<String, Integer> entry : qtyMap.entrySet()) {
            String name = entry.getKey();
            int qty = entry.getValue();
            double unitPrice = priceMap.getOrDefault(name, 0.0);
            String line = String.format("  %2dx %-12s %8.2f\n", qty, name, unitPrice);
            out.write(line.getBytes("CP850"));
        }
        out.write("\n".getBytes("CP850"));

        // 6) Total y forma de pago
        String pago = "Pago:     "
                + ("CASH".equalsIgnoreCase(o.getPaymentMethod()) ? "Efectivo" : o.getPaymentMethod());
        out.write(String.format("TOTAL:    %8.2f\n", o.getTotalAmount()).getBytes("CP850"));
        out.write((pago + "\n\n").getBytes("CP850"));

        // 7) Pie de página centrado
        out.write(new byte[]{0x1B, 'a', 0x01});
        out.write("Gracias por tu compra!\n\n\n\n\n".getBytes("CP850"));

        // 8) Corte de papel
        out.write(new byte[]{0x1D, 'V', 1});

        return out.toByteArray();
    }
}
