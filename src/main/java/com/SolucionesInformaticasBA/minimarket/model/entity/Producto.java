package com.SolucionesInformaticasBA.minimarket.model.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "producto")
@Data
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    private String barcode;

    @Column(nullable = false)
    private BigDecimal precio;

    @Column(name = "maneja_lotes")
    private boolean manejaLotes;

    private Integer stock;

    @Column(name = "id_usuario_creador")
    private UUID idUsuarioCreador;

    // estandarizar timestamp en ingles (algunas librerias de auditoria lo manejan mejor)
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
