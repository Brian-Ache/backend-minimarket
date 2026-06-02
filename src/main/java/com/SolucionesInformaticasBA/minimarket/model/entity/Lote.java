package com.SolucionesInformaticasBA.minimarket.model.entity;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import jakarta.persistence.*;


@Entity
@Table(name = "lote")
@Data
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lote")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(name = "numero_lote")
    private String numeroLote;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false)
    private Integer cantidad; 

    @ManyToOne
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
    }

    @Transient
    public String getEstado() {

        if (fechaVencimiento == null) return "SIN_FECHA";

        LocalDate hoy = LocalDate.now();

        if (fechaVencimiento.isBefore(hoy)) {
            return "VENCIDO";
        }

        if (fechaVencimiento.isBefore(hoy.plusDays(7))) {
            return "PROXIMO";
        }

        return "VIGENTE";
    }
}

