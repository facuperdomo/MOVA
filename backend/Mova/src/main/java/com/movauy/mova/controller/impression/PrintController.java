// src/main/java/com/movauy/mova/controller/impression/PrintController.java
package com.movauy.mova.controller.impression;

import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.SaleItemDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.repository.print.PrinterRepository;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.impression.PrintService;
import com.movauy.mova.service.sale.SaleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/print/direct")
public class PrintController {

    private static final Logger log = LoggerFactory.getLogger(PrintController.class);

    private final PrintService printService;
    private final PrinterRepository printerRepo;
    private final BranchService branchService;
    private final SaleService saleService;
    private final SimpMessagingTemplate messaging;

    public PrintController(PrintService printService,
                           PrinterRepository printerRepo,
                           BranchService branchService,
                           SaleService saleService,
                           SimpMessagingTemplate messaging) {
        this.printService   = printService;
        this.printerRepo    = printerRepo;
        this.branchService  = branchService;
        this.saleService    = saleService;
        this.messaging      = messaging;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> printOrder(
            @RequestBody OrderDTO orderDto,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader(value = "X-Printer-Id", required = false) Long printerId
    ) {
        // 0) Rellenar datos de la sucursal
        Branch b = branchService.findById(branchId);
        orderDto.setBranchRut(b.getRut());
        orderDto.setBranchName(b.getName());
        orderDto.setBranchAddress(b.getLocation());

        // 1) Consultar la venta y completar datos
        SaleResponseDTO sale = saleService.getById(orderDto.getId());
        orderDto.setDateTime(sale.getDateTime().toString());
        orderDto.setPaymentMethod(sale.getPaymentMethod());
        orderDto.setTotalAmount(sale.getTotalAmount());

        // 2) Mapear ítems
        List<SaleItemDTO> items = sale.getItems().stream()
                .map(i -> SaleItemDTO.builder()
                        .productId(i.getProductId())
                        .name(i.getName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .ingredientIds(i.getIngredientIds())
                        .build())
                .collect(Collectors.toList());
        orderDto.setItems(items);
        orderDto.setCompanyName(b.getCompany().getName());

        // 3) Generar ticket ESC/POS y medir tiempo
        long t0 = System.currentTimeMillis();
        byte[] payload;
        try {
            payload = printService.buildEscPosTicket(orderDto);
        } catch (IOException e) {
            log.error("Error generando ESC/POS ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        long t1 = System.currentTimeMillis();
        log.info("Tiempo generando ticket ESC/POS: {} ms", t1 - t0);

        // 4) Elegir impresora
        List<Printer> printers = printerRepo.findByBranchId(branchId);
        Printer target = (printerId != null)
                ? printers.stream()
                    .filter(p -> p.getId().equals(printerId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Printer no encontrada"))
                : printers.isEmpty() ? null : printers.get(0);

        if (target == null) {
            log.warn("No se encontró impresora para branchId={}", branchId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 5) Publicar por WebSocket un PrintMessage
        String deviceUuid = target.getDevice().getBridgeUrl();
        String b64      = java.util.Base64.getEncoder().encodeToString(payload);
        String mac      = target.getMacAddress();

        PrintMessage msg = new PrintMessage();
        msg.setB64(b64);
        msg.setMacAddress(mac);

        log.info("Enviando PrintMessage a /topic/print/{} → mac={}", deviceUuid, mac);
        messaging.convertAndSend("/topic/print/" + deviceUuid, msg);

        // 6) Responder 202 Accepted (la tablet imprimirá)
        long t2 = System.currentTimeMillis();
        log.info("Impresión delegada en total {} ms", t2 - t0);
        return ResponseEntity.accepted().build();
    }
    
    /** Imprime un recibo **solo** con los items que vienen en el DTO */
    @PostMapping("/receipt/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void printItemsReceipt(@RequestBody OrderDTO dto,
                                  @RequestHeader("X-Branch-Id") String branchId,
                                  @RequestHeader(value="X-Printer-Id", required=false) String printerId) {
        // aquí tu lógica interna de formateo ESC/POS usando dto.getItems(), dto.getTotalAmount(), dto.getMetadata()
        printService.printOrderDTO(dto, branchId, printerId);
    }
}
