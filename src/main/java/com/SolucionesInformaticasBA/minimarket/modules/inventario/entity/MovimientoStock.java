package com.SolucionesInformaticasBA.minimarket.modules.inventario.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "movimientos_stock")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovimientoStock {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_producto")
    private UUID idProducto;

    @Column(name = "id_lote")
    private UUID idLote; //puede ser null

    @Column(name = "cantidad")
    private int cantidad;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo")
    private TipoMovimiento tipo;

    @Column(name = "motivo")
    private String motivo;

    @Column(name = "id_usuario")
    private UUID idUsuario;

    // Venta o compra que originó el movimiento. Permite revertirlo al anularla.
    @Column(name = "id_referencia")
    private UUID idReferencia;

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
