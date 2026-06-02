package com.SolucionesInformaticasBA.minimarket.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "producto")
@Data
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_producto")
    private Long id;

    private String nombre;

    private String barcode;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(name = "maneja_lotes")
    private boolean manejaLotes;

    private Integer stock;

    @ManyToOne
    @JoinColumn(name = "creado_por")
    private Usuario creadoPor;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_update")
    private LocalDateTime fechaUpdate;

    @Column(name = "fecha_eliminacion")
    private LocalDateTime fechaEliminacion;

    @PrePersist// Antes de insertar
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
    }

    @PreUpdate// Antes de actualizar
    public void preUpdate() {
        this.fechaUpdate = LocalDateTime.now();
    }
    @PreRemove// Antes de eliminar (soft delete)
    public void preRemove() {
        this.fechaEliminacion = LocalDateTime.now();    
    }
}
