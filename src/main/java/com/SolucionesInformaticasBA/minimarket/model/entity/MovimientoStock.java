package com.SolucionesInformaticasBA.minimarket.model.entity;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.SolucionesInformaticasBA.minimarket.model.enums.TipoMovimiento;

import jakarta.persistence.*;


@Entity
@Table(name = "movimientos_stock")
@Getter
@Setter
@Builder
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id") // id_entidad solo para FK
    private UUID id; // mas seguro para no exponer id incremementales si no es necesario

    @Column(name = "id_producto", nullable = false)
    private UUID idProducto; // revisar si se necesita que sea incremental o cambiar a UUID

    private int cantidad;

    @Enumerated(EnumType.STRING)
    private TipoMovimiento tipo;

    private String motivo;

    @Column(name = "id_usuario") // se usa join column cuando hay herencia, sino es sobrecomplejuzarlo
    private UUID idUsuario;

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