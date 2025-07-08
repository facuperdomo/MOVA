// src/main/java/com/movauy/mova/controller/impression/PrintController.java
package com.movauy.mova.controller.impression;

import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.SaleItemDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.repository.print.PrinterRepository;
import com.movauy.mova.repository.product.ProductRepository;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.impression.PrintService;
import com.movauy.mova.service.sale.SaleService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/print/direct")
@RequiredArgsConstructor
public class PrintController {

    private static final Logger log = LoggerFactory.getLogger(PrintController.class);

    private final PrintService printService;
    private final PrinterRepository printerRepo;
    private final BranchService branchService;
    private final SaleService saleService;
    private final SimpMessagingTemplate messaging;
    private final ProductRepository productRepo;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> printOrder(
            @RequestBody OrderDTO orderDto,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader("X-Device-Id") Long deviceId
    ) {
        Branch b = branchService.findById(branchId);

        if (!b.isEnablePrinting()) {
            log.warn("[printOrder] La sucursal {} tiene impresión desactivada", b.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "PrintingDisabled",
                            "message", "La sucursal tiene la impresión desactivada."
                    ));
        }

        // continuar normalmente
        orderDto.setBranchRut(b.getRut());
        orderDto.setBranchName(b.getName());
        orderDto.setBranchAddress(b.getLocation());

        SaleResponseDTO sale = saleService.getById(orderDto.getId());
        orderDto.setDateTime(sale.getDateTime().toString());
        orderDto.setTotalAmount(sale.getTotalAmount());
        if (orderDto.getPaymentMethod() == null || orderDto.getPaymentMethod().isBlank()) {
            orderDto.setPaymentMethod(sale.getPaymentMethod());
        }

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
        log.debug("[printOrder] X-Branch-Id={}  X-Device-Id={}", branchId, deviceId);
        sendAndLog(orderDto, deviceId, "printOrder");
        return ResponseEntity.accepted().build();
    }

    /**
     * Imprime **solo** los items que vienen en el DTO
     */
    @PostMapping("/receipt/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<?> printItemsReceipt(
            @RequestBody OrderDTO dto,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader("X-Device-Id") Long deviceId
    ) {
        // 0) Rellenar datos de sucursal/empresa
        Branch b = branchService.findById(branchId);

        if (!b.isEnablePrinting()) {
            log.warn("[printItemsReceipt] La sucursal {} tiene impresión desactivada", b.getId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                            "error", "PrintingDisabled",
                            "message", "La sucursal tiene la impresión desactivada."
                    ));
        }

        dto.setBranchRut(b.getRut());
        dto.setBranchName(b.getName());
        dto.setBranchAddress(b.getLocation());
        dto.setCompanyName(b.getCompany().getName());

        // 1) Completar datos de la venta
        SaleResponseDTO sale = saleService.getById(dto.getId());
        dto.setDateTime(sale.getDateTime().toString());
        dto.setPaymentMethod(sale.getPaymentMethod());
        dto.setTotalAmount(sale.getTotalAmount());

        // 2) Mapear nombres de ítems desde la BBDD
        List<SaleItemDTO> fixedItems = dto.getItems().stream()
                .map(i -> {
                    String nombre = productRepo.findById(i.getProductId())
                            .map(p -> p.getName())
                            .orElse("Producto");
                    return SaleItemDTO.builder()
                            .productId(i.getProductId())
                            .name(nombre)
                            .quantity(i.getQuantity())
                            .unitPrice(i.getUnitPrice())
                            .ingredientIds(i.getIngredientIds())
                            .build();
                })
                .toList();
        dto.setItems(fixedItems);

        // 3–5) Generar ticket, loguear y enviar
        sendAndLog(dto, deviceId, "printItemsReceipt");
        return ResponseEntity.noContent().build();
    }

    // -----------------------------------------------
// Helper privado para generar, loguear y enviar
// -----------------------------------------------
    private void sendAndLog(OrderDTO dto, Long deviceId, String context) {
        byte[] payload;
        try {
            payload = printService.buildEscPosTicket(dto);
        } catch (IOException e) {
            log.error("[{}] Error generando ESC/POS ticket", context, e);
            throw new RuntimeException(e);
        }

        // Log longitud y snippet del payload decodificado
        log.info("[{}] Payload bytes: {}", context, payload.length);
        try {
            String asText = new String(payload, "CP850");
            if (asText.length() > 200) {
                log.info("[{}] Payload (CP850 decoded) snippet:\n{}…", context, asText.substring(0, 200));
            } else {
                log.info("[{}] Payload (CP850 decoded):\n{}", context, asText);
            }
        } catch (Exception e) {
            log.warn("[{}] No pude decodificar payload a texto", context, e);
        }

        // Log snippet de Base64
        String b64 = Base64.getEncoder().encodeToString(payload);
log.info("[{}] Payload Base64 (COMPLETO): {}", context, b64);


        // Elegir impresora según deviceId
        Printer target = printerRepo.findByDeviceId(deviceId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                "[" + context + "] No hay impresora asignada al device " + deviceId));

        String uuid = target.getDevice().getBridgeUrl();
        PrintMessage msg = new PrintMessage();
        msg.setB64(b64);
        msg.setMacAddress(target.getMacAddress());

        log.info("[{}] Enviando a /topic/print/{} (mac={})", context, uuid, target.getMacAddress());
        messaging.convertAndSend("/topic/print/" + uuid, msg);
    }
}
