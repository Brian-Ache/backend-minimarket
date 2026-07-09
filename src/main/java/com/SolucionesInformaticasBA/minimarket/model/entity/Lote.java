package com.SolucionesInformaticasBA.minimarket.model.entity;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;


@Entity
@Table(name = "lotes")
@Getter
@Setter
@Builder
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id; // revisar si necesita ser incremental

    @Column(name = "id_producto", nullable = false)
    private UUID idProducto;

    @Column(name = "numero_lote")
    private String numeroLote;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(nullable = false)
    private int cantidad; 

    @Column(name = "id_usuario_creador")
    private UUID idUsuarioCreador;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt = null;


    // migrar al service
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

