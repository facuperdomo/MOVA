// src/main/java/com/movauy/mova/controller/impression/PrintQueueController.java
package com.movauy.mova.controller.impression;

import com.movauy.mova.dto.OrderDTO;
import com.movauy.mova.model.impression.PrintJob;
import com.movauy.mova.service.impression.PrintQueueService;
import com.movauy.mova.service.impression.PrintService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;

@RestController
@RequestMapping("/api/print")
public class PrintQueueController {

    private final PrintService printer;   // para generar CPCL
    private final PrintQueueService queue;

    public PrintQueueController(PrintService printer, PrintQueueService queue) {
        this.printer = printer;
        this.queue = queue;
    }

    /**
     * 1) El front llama aquí para encolar un ticket. Debe enviar la cabecera
     * X-Company-Id para indicar de qué empresa es.
     */
    @PostMapping
    public ResponseEntity<Void> enqueue(
            @RequestBody OrderDTO o,
            @RequestHeader("X-Company-Id") String companyId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId
    ) {
        byte[] cpcl = printer.buildCpclTicket(o).getBytes();
        queue.enqueue(cpcl, deviceId, companyId);
        return ResponseEntity.accepted().build();
    }

    /**
     * 2) La tablet hace polling a /api/print/jobs/next con la cabecera
     * X-Company-Id; solo recibirá jobs de esa empresa.
     */
    @GetMapping("/jobs/next")
    public ResponseEntity<PrintJobDto> nextJob(
            @RequestHeader("X-Company-Id") String companyId,
    @RequestHeader(value="X-Device-Id", required=false) String deviceId
    ) {
        PrintJob job = queue.fetchNextFor(companyId);
        if (job == null) {
            return ResponseEntity.noContent().build();
        }

        String b64 = Base64.getEncoder().encodeToString(job.getPayload());
        return ResponseEntity.ok(new PrintJobDto(job.getId(), b64));
    }

    /**
     * 3) La tablet confirma (ACK) o marca error.
     */
    @PostMapping("/jobs/{id}/ack")
    public ResponseEntity<Void> ack(
            @PathVariable Long id,
            @RequestParam boolean success,
            @RequestHeader("X-Company-Id") String companyId
    ) {
        // (opcional) podrías verificar que el job id pertenezca a companyId
        if (success) {
            queue.markDone(id);
        } else {
            queue.markError(id);
        }
        return ResponseEntity.ok().build();
    }

    // DTO interno para la tablet
    static class PrintJobDto {

        public Long id;
        public String b64payload;

        public PrintJobDto(Long id, String p) {
            this.id = id;
            this.b64payload = p;
        }
    }
}
