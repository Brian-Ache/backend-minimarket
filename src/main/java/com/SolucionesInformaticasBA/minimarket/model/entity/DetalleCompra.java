package com.SolucionesInformaticasBA.minimarket.model.entity;

import java.math.BigDecimal;

import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "detalle_compra")
@Data
public class DetalleCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idDetalle;

    @ManyToOne
    @JoinColumn(name = "id_compra")
    private Compra compra;

    @ManyToOne
    @JoinColumn(name = "id_producto")
    private Producto producto;

    private Integer cantidad;

    private BigDecimal precioUnitario;
}