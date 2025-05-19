package com.movauy.mova.model.print;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.device.Device;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Printer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "branch_id", nullable = false)
    // si también quieres impedir recursión branch→printers→…
    @JsonBackReference("branch-printers")
    private Branch branch;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String macAddress;

    private String type;

    @ManyToOne(optional = true)
    @JoinColumn(name = "device_id", nullable = true)
    // rompe el ciclo device→printers→device
    @JsonBackReference("device-printers")
    private Device device;

    public void setId(Long id) {
        this.id = id;
    }
}
