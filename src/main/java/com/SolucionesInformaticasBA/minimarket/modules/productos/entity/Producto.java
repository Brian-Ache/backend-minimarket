package com.SolucionesInformaticasBA.minimarket.modules.productos.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.*;

@Entity
@Table(name = "productos")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String nombre;

    private String barcode;

    @Column(nullable = false)
    private float precio;

    @Column(name = "maneja_lotes")
    private boolean manejaLotes;

    @Column(nullable = true)
    private Float costo;

    @Column(nullable = true)
    private Float margen;

    @Column(name = "id_categoria", nullable = true)
    private UUID idCategoria;

    @Column(name = "id_proveedor", nullable = true)
    private UUID idProveedor;

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
