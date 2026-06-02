package com.SolucionesInformaticasBA.minimarket.model.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "compra")
@Data
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idCompra;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    private LocalDateTime fecha;

    private BigDecimal total;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL)
    private List<DetalleCompra> detalles;
}