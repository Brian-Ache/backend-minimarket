package com.SolucionesInformaticasBA.minimarket.modules.ventas.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "detalles_ventas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_venta", nullable = false)
    private UUID idVenta;

    // Nullable: los ítems MANUAL (venta suelta sin producto de catálogo) no lo tienen.
    @Column(name = "id_producto")
    private UUID idProducto;

    // nombre del producto vendido, tanto si existe en el sistema como si es manual
    @Column(name = "nombre_producto")
    private String nombreProducto;

    @Column(nullable = false)
    private int cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private float precioUnitario;

    // Costo del producto al momento de vender. Congelarlo acá es lo que permite calcular
    // la ganancia real después, aunque el costo del producto cambie más adelante.
    @Column(name = "costo_unitario")
    private Float costoUnitario;

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
