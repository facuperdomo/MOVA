// src/main/java/com/movauy/mova/controller/impression/PrintController.java
package com.movauy.mova.controller.impression;

import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.service.impression.PrintService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/print/direct")
public class PrintController {

    private final PrintService printService;
    private final RestTemplate restTemplate = new RestTemplate();

    /** URL de tu bridge Android, p.ej. http://tablet.local:8080 */
    @Value("${printbridge.url}")
    private String bridgeUrl;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    /**
     * Direct printing (sin cola), recibe también la cabecera X-Company-Id
     * para que el bridge Android sepa a qué empresa pertenece esta impresora.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> printOrder(
            @RequestBody OrderDTO orderDto,
            @RequestHeader("X-Company-Id") String companyId
    ) {
        // 1) Generar CPCL
        String cpcl = printService.buildCpclTicket(orderDto);
        byte[] payload = cpcl.getBytes();

        // 2) Enviar al bridge Android, incluyendo companyId
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.add("X-Company-Id", companyId);

        ResponseEntity<String> bridgeResp = restTemplate.postForEntity(
            bridgeUrl + "/print",
            new HttpEntity<>(payload, headers),
            String.class
        );

        // 3) Si algo falla en el bridge, devolvemos 502
        if (!bridgeResp.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
        }

        // 4) Responder OK
        return ResponseEntity.ok().build();
    }
}
