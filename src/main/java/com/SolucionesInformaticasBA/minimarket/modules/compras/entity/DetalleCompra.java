package com.SolucionesInformaticasBA.minimarket.modules.compras.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "detalles_compras")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleCompra {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_compra")
    private UUID idCompra;

    @Column(name = "id_producto")
    private UUID idProducto;

    @Column(name = "nombre_producto")
    private String nombreProducto;

    @Column(name = "barcode")
    private String barcode;

    @Column(name = "precio_unitario")
    private float precioUnitario;

    @Column(name = "cantidad")
    private int cantidad;

    @Column(name = "total")
    private float total;

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
