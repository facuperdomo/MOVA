// src/main/java/com/movauy/mova/model/impression/PrintJob.java
package com.movauy.mova.model.impression;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrintJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    private byte[] payload;      // aquí tu CPCL en bytes

    @Enumerated(EnumType.STRING)
    private Status status;       // PENDING, IN_PROGRESS, DONE, ERROR

    private String deviceId;     // opcional, si quieres filtrar por tablet
    private String companyId;
    private long createdAt;    // System.currentTimeMillis()

    public enum Status {
        PENDING, IN_PROGRESS, DONE, ERROR
    }
}
