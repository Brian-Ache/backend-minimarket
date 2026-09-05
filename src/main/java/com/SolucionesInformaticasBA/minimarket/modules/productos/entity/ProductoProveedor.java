package com.SolucionesInformaticasBA.minimarket.modules.productos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Precio que un proveedor lista por un producto. Es un catálogo de consulta, cargado y
 * actualizado siempre a mano: no es lo que se pagó la última vez —eso sale del historial de
 * compras— ni influye en ninguna compra ni en ningún cálculo.
 *
 * <p>Vive en el módulo de productos y no en el de proveedores porque {@code productos ->
 * proveedores} ya existe: colgar esto del otro lado obligaría a que proveedores consultara
 * productos y cerraría un ciclo entre los dos módulos.
 *
 * <p>El importe es {@link BigDecimal} y la columna {@code DECIMAL}, a diferencia del resto de
 * los importes del sistema, que todavía son {@code float}. Es deuda conocida; una columna
 * nueva no tiene por qué nacer con el problema, y esta no se suma con ninguna otra.
 */
@Entity
@Table(name = "producto_proveedor")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoProveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "id_producto", nullable = false)
    private UUID idProducto;

    @Column(name = "id_proveedor", nullable = false)
    private UUID idProveedor;

    @Column(name = "precio_referencia", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioReferencia;

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
