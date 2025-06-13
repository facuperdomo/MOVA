package com.movauy.mova.service.impression;

public interface PrinterGateway {
    /**
     * Envía el payload ESC/POS al dispositivo correspondiente.
     *
     * @param branchId  id de la sucursal (header X-Branch-Id)
     * @param printerId id opcional de la impresora (header X-Printer-Id)
     * @param payload   bytes ESC/POS
     */
    void send(String branchId, String printerId, byte[] payload);
}