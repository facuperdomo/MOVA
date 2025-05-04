// src/main/java/com/movauy/mova/service/impression/InMemoryPrintQueueService.java
package com.movauy.mova.service.impression;

import com.movauy.mova.model.impression.PrintJob;
import com.movauy.mova.model.impression.PrintJob.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class InMemoryPrintQueueService implements PrintQueueService {

    private final AtomicLong idGen = new AtomicLong(1);
    private final Map<Long, PrintJob> allJobs = new ConcurrentHashMap<>();
    private final Map<String, Queue<PrintJob>> queues = new ConcurrentHashMap<>();

    @Override
    public void enqueue(byte[] payload, String deviceId, String companyId) {
        long id = idGen.getAndIncrement();

        PrintJob job = PrintJob.builder()
                .id(id)                              // id generado
                .payload(payload)                    // tus bytes de CPCL
                .status(Status.PENDING)              // estado inicial
                .deviceId(deviceId)                  // opcional
                .companyId(companyId)                // para filtrar por empresa
                .createdAt(System.currentTimeMillis()) // timestamp millis
                .build();

        allJobs.put(id, job);
        queues
          .computeIfAbsent(companyId, k -> new ConcurrentLinkedQueue<>())
          .add(job);

        log.debug("Enqueued print job {} for company={} device={}", id, companyId, deviceId);
    }

    @Override
    public void markDone(Long jobId) {
        PrintJob job = allJobs.get(jobId);
        if (job != null) job.setStatus(Status.DONE);
    }

    @Override
    public void markError(Long jobId) {
        PrintJob job = allJobs.get(jobId);
        if (job != null) job.setStatus(Status.ERROR);
    }
    
    @Override
    public PrintJob fetchNextFor(String companyId) {
        var q = queues.get(companyId);
        if (q == null) return null;

        PrintJob job;
        while ((job = q.poll()) != null) {
            if (job.getStatus() == PrintJob.Status.PENDING) {
                job.setStatus(PrintJob.Status.IN_PROGRESS);
                return job;
            }
        }
        return null;
    }
}
