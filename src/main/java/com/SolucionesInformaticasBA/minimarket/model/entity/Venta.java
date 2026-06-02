package com.SolucionesInformaticasBA.minimarket.model.entity;

import jakarta.persistence.Entity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;

@Entity
@Table(name = "venta")
@Data
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    private LocalDateTime fecha;

    private BigDecimal total;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    private List<DetalleVenta> detalles;

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
    }



}