package com.movauy.mova.service.impression;

import com.movauy.mova.controller.impression.PrintMessage;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.repository.print.PrinterRepository;
import com.movauy.mova.service.branch.BranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

@Component
public class WebSocketPrinterGateway implements PrinterGateway {

    private final PrinterRepository printerRepo;
    private final BranchService branchService;
    private final SimpMessagingTemplate messaging;

    @Autowired
    public WebSocketPrinterGateway(PrinterRepository printerRepo,
                                   BranchService branchService,
                                   SimpMessagingTemplate messaging) {
        this.printerRepo   = printerRepo;
        this.branchService = branchService;
        this.messaging     = messaging;
    }

    @Override
    public void send(String branchIdHeader, String printerIdHeader, byte[] payload) {
        // 1) El branchIdHeader viene como String, conviértelo a Long
        Long branchId = Long.valueOf(branchIdHeader);
        Branch branch = branchService.findById(branchId);

        // 2) Busca impresoras de la sucursal
        List<Printer> printers = printerRepo.findByBranchId(branchId);
        Printer target = null;
        if (printerIdHeader != null) {
            Long pid = Long.valueOf(printerIdHeader);
            target = printers.stream()
                     .filter(p -> p.getId().equals(pid))
                     .findFirst()
                     .orElse(null);
        }
        if (target == null && !printers.isEmpty()) {
            target = printers.get(0);
        }
        if (target == null) {
            throw new IllegalArgumentException("No se encontró impresora para branchId=" + branchId);
        }

        // 3) Construye el PrintMessage
        String deviceUuid = target.getDevice().getBridgeUrl();
        String b64        = Base64.getEncoder().encodeToString(payload);
        PrintMessage msg  = new PrintMessage();
        msg.setB64(b64);
        msg.setMacAddress(target.getMacAddress());

        // 4) Manda por WebSocket
        messaging.convertAndSend("/topic/print/" + deviceUuid, msg);
    }
}