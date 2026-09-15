package com.SolucionesInformaticasBA.minimarket.modules.ventas.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

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
public class DetalleVenta implements Persistable<UUID> {

    /** UUIDv7 asignado a mano, por el mismo motivo que el de {@link Venta}. */
    @Id
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

    @Column(name = "precio_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioUnitario;

    // Costo del producto al momento de vender. Congelarlo acá es lo que permite calcular
    // la ganancia real después, aunque el costo del producto cambie más adelante.
    @Column(name = "costo_unitario", precision = 12, scale = 2)
    private BigDecimal costoUnitario;

    /** La fecha del ticket, no la del INSERT. Ver {@link Venta#getCreatedAt()}. */
    @Column(name = "created_at", updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder.Default
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt = null;

    /** Ver {@link Venta#isNew()}: el id asignado a mano obliga a decirlo explícitamente. */
    @Builder.Default
    @Transient
    private boolean nuevo = true;

    @Override
    public boolean isNew() {
        return nuevo;
    }

    @PostLoad
    @PrePersist
    void yaEstaEnLaBase() {
        this.nuevo = false;
    }
}
