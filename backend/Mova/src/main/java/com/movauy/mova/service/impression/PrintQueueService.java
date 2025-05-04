// src/main/java/com/movauy/mova/service/impression/PrintQueueService.java
package com.movauy.mova.service.impression;

import com.movauy.mova.model.impression.PrintJob;

public interface PrintQueueService {

    /** Encola un trabajo de impresión para la empresa/device dado */
    void enqueue(byte[] payload, String deviceId, String companyId);
    
    /** Marca un job como DONE */
    void markDone(Long jobId);

    /** Marca un job como ERROR */
    void markError(Long jobId);
    
    /** Extrae el siguiente job PENDING, marcándolo In-Progress */
    PrintJob fetchNextFor(String companyId);
}
