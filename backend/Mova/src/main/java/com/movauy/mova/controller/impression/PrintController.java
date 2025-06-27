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
    public ResponseEntity<Void> printOrder(
            @RequestBody OrderDTO orderDto,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader("X-Device-Id") Long deviceId
    ) {
        // 0) Rellenar datos de la sucursal
        Branch b = branchService.findById(branchId);
        orderDto.setBranchRut(b.getRut());
        orderDto.setBranchName(b.getName());
        orderDto.setBranchAddress(b.getLocation());

        // 1) Consultar la venta y completar datos
        SaleResponseDTO sale = saleService.getById(orderDto.getId());
        orderDto.setDateTime(sale.getDateTime().toString());
        orderDto.setTotalAmount(sale.getTotalAmount());

        if (orderDto.getPaymentMethod() == null || orderDto.getPaymentMethod().isBlank()) {
            orderDto.setPaymentMethod(sale.getPaymentMethod());
        }

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

        // 3) Generar ticket ESC/POS
        byte[] payload;
        try {
            payload = printService.buildEscPosTicket(orderDto);
        } catch (IOException e) {
            log.error("Error generando ESC/POS ticket", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        // 4) Elegir impresora a partir del deviceId
        Printer target = printerRepo.findByDeviceId(deviceId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay impresora asignada al device " + deviceId));

        // 5) Publicar por WebSocket
        String deviceUuid = target.getDevice().getBridgeUrl();
        String b64 = Base64.getEncoder().encodeToString(payload);
        PrintMessage msg = new PrintMessage();
        msg.setB64(b64);
        msg.setMacAddress(target.getMacAddress());

        messaging.convertAndSend("/topic/print/" + deviceUuid, msg);
        return ResponseEntity.accepted().build();
    }

    /**
     * Imprime **solo** los items que vienen en el DTO
     */
    @PostMapping("/receipt/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void printItemsReceipt(
            @RequestBody OrderDTO dto,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader("X-Device-Id") Long deviceId
    ) {
        // 0) Rellenar datos de sucursal/empresa
        Branch b = branchService.findById(branchId);
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

        // 3) Generar ticket ESC/POS
        byte[] payload;
        try {
            payload = printService.buildEscPosTicket(dto);
        } catch (IOException e) {
            log.error("Error generando ESC/POS ticket", e);
            throw new RuntimeException(e);
        }

        // 4) Elegir impresora según deviceId
        Printer target = printerRepo.findByDeviceId(deviceId).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No hay impresora asignada al device " + deviceId));

        // 5) Publicar por WebSocket
        String deviceUuid = target.getDevice().getBridgeUrl();
        String b64 = Base64.getEncoder().encodeToString(payload);
        PrintMessage msg = new PrintMessage();
        msg.setB64(b64);
        msg.setMacAddress(target.getMacAddress());

        messaging.convertAndSend("/topic/print/" + deviceUuid, msg);
    }
}
