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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

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
    private final RestTemplate restTemplate = new RestTemplate();
    private final SaleService saleService;

    public PrintController(PrintService printService,
                           PrinterRepository printerRepo,
                           BranchService branchService,
                           SaleService saleService) {
        this.printService = printService;
        this.printerRepo = printerRepo;
        this.branchService = branchService;
        this.saleService = saleService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> printOrder(
            @RequestBody OrderDTO orderDto,
            @RequestHeader("X-Branch-Id") Long branchId,
            @RequestHeader(value = "X-Printer-Id", required = false) Long printerId
    ) {
        // 0) Rellenar datos de la sucursal en el DTO
        Branch b = branchService.findById(branchId);
        orderDto.setBranchRut(b.getRut());
        orderDto.setBranchName(b.getName());
        orderDto.setBranchAddress(b.getLocation());

        SaleResponseDTO sale = saleService.getById(orderDto.getId());
        orderDto.setDateTime(sale.getDateTime().toString());
        orderDto.setPaymentMethod(sale.getPaymentMethod());
        orderDto.setTotalAmount(sale.getTotalAmount());

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

        // Medir tiempo de generación de ticket
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

        // 2) elegir impresora
        List<Printer> printers = printerRepo.findByBranchId(branchId);
        Printer target = (printerId != null)
            ? printers.stream()
                .filter(p -> p.getId().equals(printerId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Printer no encontrada"))
            : printers.isEmpty()
                ? null
                : printers.get(0);

        if (target == null) {
            log.warn("No se encontró impresora para branchId={}", branchId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 3) construir URL del bridge
        String rawUrl = target.getDevice().getBridgeUrl();
        if (rawUrl == null || rawUrl.isBlank()) {
            log.warn("Bridge URL no configurada para deviceId={}", target.getDevice().getId());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .header("X-Error", "Bridge URL no configurada para el dispositivo")
                    .build();
        }
        String bridgeUrl = rawUrl.startsWith("http://") || rawUrl.startsWith("https://") 
                ? rawUrl 
                : "http://" + rawUrl;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add("X-Branch-Id", branchId.toString());
        headers.add("X-Printer-Mac", target.getMacAddress());
        headers.add("X-Device-Id", target.getDevice().getId().toString());

        // Medir tiempo de envío al bridge
        long t2 = System.currentTimeMillis();
        ResponseEntity<String> bridgeResp;
        try {
            bridgeResp = restTemplate.exchange(
                bridgeUrl + "/print",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
            );
        } catch (ResourceAccessException ex) {
            log.error("Timeout conectando al bridge {}: {}", bridgeUrl, ex.getMessage());
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT)
                    .header("X-Error", "No se pudo conectar al bridge de impresión")
                    .build();
        }
        long t3 = System.currentTimeMillis();
        log.info("Tiempo enviando payload al bridge: {} ms", t3 - t2);

        if (!bridgeResp.getStatusCode().is2xxSuccessful()) {
            log.warn("Bridge respondió con código {}", bridgeResp.getStatusCode());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        log.info("Impresión completada en total {} ms", t3 - t0);
        return ResponseEntity.ok().build();
    }
}
