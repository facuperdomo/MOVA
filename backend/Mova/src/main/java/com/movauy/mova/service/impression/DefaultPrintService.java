package com.movauy.mova.service.impression;

import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.SaleItemDTO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import org.springframework.stereotype.Service;

@Service
public class DefaultPrintService implements PrintService {

    @Override
    public byte[] buildEscPosTicket(OrderDTO o) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        // 1) Init printer y cambiar code page a CP850
        out.write(new byte[]{0x1B, '@'});           // ESC @
        out.write(new byte[]{0x1B, 't', 0x02});     // ESC t 2 = CP850

        // 2) Encabezado centrado + negrita para nombre de empresa
        out.write(new byte[]{0x1B, 'a', 0x01});     // ESC a 1 = center
        out.write(new byte[]{0x1B, 'E', 0x01});     // ESC E 1 = bold on
        out.write(o.getCompanyName().getBytes("CP850"));
        out.write("\n".getBytes("CP850"));
        out.write(new byte[]{0x1B, 'E', 0x00});     // ESC E 0 = bold off

        // RUT y dirección centrados
        out.write(("RUT: " + o.getBranchRut() + "\n").getBytes("CP850"));
        out.write((o.getBranchAddress() + "\n\n").getBytes("CP850"));

        // 3) Alinear a la izquierda
        out.write(new byte[]{0x1B, 'a', 0x00});     // ESC a 0 = left

        // 4) Sucursal, hora, fecha, ticket
        String[] parts = o.getDateTime().split("T");
        String datePart = parts[0];
        String timePart = parts.length > 1 ? parts[1] : "";
        out.write(("Sucursal: " + o.getBranchName() + "\n").getBytes("CP850"));
        out.write(("Hora:     " + timePart + "\n").getBytes("CP850"));
        out.write(("Fecha:    " + datePart + "\n").getBytes("CP850"));
        out.write(("Ticket #: " + o.getId() + "\n").getBytes("CP850"));
        out.write("--------------------------------\n".getBytes("CP850"));

        // 5) Detalle de ítems con sangría
        for (SaleItemDTO item : o.getItems()) {
            String line = String.format("  %2dx %-12s %8.2f\n",
                    item.getQuantity(), item.getName(), item.getUnitPrice());
            out.write(line.getBytes("CP850"));
        }
        out.write("\n".getBytes("CP850"));

        // 6) Total y forma de pago
        String pago = "Pago:     "
                + ("CASH".equalsIgnoreCase(o.getPaymentMethod()) ? "Efectivo" : o.getPaymentMethod());
        out.write(String.format("TOTAL:    %8.2f\n", o.getTotalAmount()).getBytes("CP850"));
        out.write((pago + "\n\n").getBytes("CP850"));

        out.write(new byte[]{0x1B, 'a', 0x01});     // ESC a 1 = center
        // 7) Pie de página alineado a la izquierda (¡Gracias…!)
        out.write("Gracias por tu compra!\n\n\n\n\n".getBytes("CP850"));

        // 8) Corte de papel
        out.write(new byte[]{0x1D, 'V', 1});       // GS V 1

        return out.toByteArray();
    }

}
