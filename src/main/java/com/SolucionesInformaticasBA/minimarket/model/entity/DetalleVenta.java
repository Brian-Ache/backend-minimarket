package com.SolucionesInformaticasBA.minimarket.model.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "detalle_venta")
@Data
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_detalle")
    private Long id;

    @Column(name = "id_venta", nullable = false)
    private UUID idVenta;

    @Column(name = "id_producto", nullable = false)
    private UUID idProducto;

    @Column(name = "nombre_manual")
    private String nombreManual;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private BigDecimal precioUnitario;
}
