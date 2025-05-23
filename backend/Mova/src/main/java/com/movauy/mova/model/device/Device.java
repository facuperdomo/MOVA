package com.movauy.mova.model.device;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.movauy.mova.model.branch.Branch;
import com.movauy.mova.model.print.Printer;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

/**
 *
 * @author facue
 */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class Device {

    @Id
    @GeneratedValue
    Long id;
    private String name;
    private String bridgeUrl;     // ej: http://192.168.1.103:8080
    @ManyToOne(optional = false)
    private Branch branch;        // a qué sucursal pertenece esta tablet/bridge

    @OneToMany(mappedBy = "device", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference("device-printers")
    private List<Printer> printers = new ArrayList<>();
}
