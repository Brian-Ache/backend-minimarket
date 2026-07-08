package com.SolucionesInformaticasBA.minimarket.model.entity;

import jakarta.persistence.Entity;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.*;

@Entity
@Table(name = "venta")
@Data
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id")
    private UUID id; // mas seguro que id incrementales

    @Column(name = "id_usuario", nullable = false)
    private UUID idUsuario;

    // estandarizar los 3 timestamp para auditar
    private LocalDateTime fecha;

    private BigDecimal total;

    // tabla pivote en sentido contrario detalle apunta a la venta (menos complejo mas rapido para buscar con indices)
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL)
    private List<DetalleVenta> detalles;

    @PrePersist
    public void prePersist() {
        this.fecha = LocalDateTime.now();
    }



}