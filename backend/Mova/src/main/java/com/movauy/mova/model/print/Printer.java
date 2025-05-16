package com.movauy.mova.model.print;

import com.movauy.mova.model.branch.Branch;
import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Entity
@Data        // @Data incluye @Getter, @Setter, @RequiredArgsConstructor, equals/hashCode y toString
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Printer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JsonIgnoreProperties({"branches", "users"})
    @JoinColumn(name = "branch_id", nullable = false)
    private Branch branch;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false, unique = true)
    private String macAddress;
    private String type;
    
    public void setId(Long id) {
        this.id = id;
    }
}

