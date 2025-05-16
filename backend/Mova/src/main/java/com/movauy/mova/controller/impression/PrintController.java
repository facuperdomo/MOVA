// src/main/java/com/movauy/mova/controller/impression/PrintController.java
package com.movauy.mova.controller.impression;

import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.dto.SaleItemDTO;
import com.movauy.mova.dto.SaleResponseDTO;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.print.Printer;
import com.movauy.mova.repository.print.PrinterRepository;
import com.movauy.mova.service.branch.BranchService;
import com.movauy.mova.service.impression.PrintService;
import com.movauy.mova.service.sale.SaleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/print/direct")
public class PrintController {

    private final PrintService printService;
    private final PrinterRepository printerRepo;
    private final BranchService branchService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final SaleService saleService;

    @Value("${printbridge.url}")
    private String bridgeUrl;

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
        // convierto los SaleItemResponseDTO a SaleItemDTO (mismo campo quantity, name, unitPrice…)
        List<SaleItemDTO> items = sale.getItems().stream()
                .map(i -> SaleItemDTO.builder()
                .productId(i.getProductId())
                .name(i.getName()) // ← ya existe getName()
                .quantity(i.getQuantity())
                .unitPrice(i.getUnitPrice())
                .ingredientIds(i.getIngredientIds())
                .build()
                )
                .collect(Collectors.toList());
        orderDto.setItems(items);

        // 4) además, mete el nombre de la compañía en lugar de “MiEmpresa S.A.”
        orderDto.setCompanyName(b.getCompany().getName());

        // 1) generar ticket ESC/POS (o CPCL)
        byte[] payload;
        try {
            payload = printService.buildEscPosTicket(orderDto);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

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
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 3) llamar al bridge Android
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add("X-Branch-Id", branchId.toString());
        headers.add("X-Printer-Mac", target.getMacAddress());

        ResponseEntity<String> bridgeResp = restTemplate.exchange(
                bridgeUrl + "/print",
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                String.class
        );

        if (!bridgeResp.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }
        return ResponseEntity.ok().build();
    }
}
