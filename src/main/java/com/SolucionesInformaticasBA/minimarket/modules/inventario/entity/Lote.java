package com.SolucionesInformaticasBA.minimarket.modules.inventario.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lote")
@Getter
@Setter
@Builder
public class Lote {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_producto")
    private UUID idProducto;

    @Column(name = "numero_lote")
    private String numeroLote;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoLote estado;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    @Column(name = "cantidad")
    private int cantidad;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    @Builder.Default
    private LocalDateTime deletedAt = null;
}
