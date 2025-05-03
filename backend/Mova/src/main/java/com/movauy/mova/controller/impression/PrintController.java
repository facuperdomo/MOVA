package com.movauy.mova.controller.impression;

import com.movauy.mova.model.sale.Sale;
import com.movauy.mova.service.impression.PrintService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/print")
public class PrintController {

    private final PrintService printService;
    private final RestTemplate restTemplate = new RestTemplate();

    /** URL de tu bridge Android, p.ej. http://192.168.1.50:8080 */
    @Value("${printbridge.url}")
    private String bridgeUrl;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> printOrder(@RequestBody Sale sale) {
        // 1) Generar CPCL
        String cpcl = printService.buildCpclTicket(sale);
        byte[] payload = cpcl.getBytes();

        // 2) Enviar al bridge Android
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        restTemplate.postForEntity(bridgeUrl + "/print",
            new org.springframework.http.HttpEntity<>(payload, headers),
            String.class);

        // 3) Responder OK
        return ResponseEntity.ok().build();
    }
}
