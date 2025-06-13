package com.movauy.mova.service.impression;

import com.movauy.mova.dto.OrderDTO;
import java.io.IOException;

/**
 * Servicio de impresión: genera comandos ESC/POS y envía a la impresora.
 */
public interface PrintService {

    /**
     * Genera un array de bytes con comandos ESC/POS para imprimir el ticket.
     *
     * @param order datos del pedido a imprimir
     * @return arreglo de bytes con comandos ESC/POS
     * @throws IOException si ocurre error al construir el ticket
     */
    byte[] buildEscPosTicket(OrderDTO order) throws IOException;

    /**
     * Construye el ESC/POS para el pedido y lo envía al dispositivo de impresión.
     *
     * @param order datos del pedido (OrderDTO)
     * @param branchId identificador de la sucursal (X-Branch-Id header)
     * @param printerId identificador opcional de la impresora (X-Printer-Id header)
     */
    void printOrderDTO(OrderDTO order, String branchId, String printerId);
}
