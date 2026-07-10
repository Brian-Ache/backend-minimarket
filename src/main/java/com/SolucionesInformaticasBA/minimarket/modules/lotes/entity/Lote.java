package com.SolucionesInformaticasBA.minimarket.modules.lotes.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.SolucionesInformaticasBA.minimarket.modules.lotes.enums.EstadoLote;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lotes")
@Getter
@Setter
@Builder
public class Lote {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_producto", nullable = false)
    private UUID idProducto;

    @Column(name = "numero_lote")
    private String numeroLote;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado")
    private EstadoLote estado;

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
}
