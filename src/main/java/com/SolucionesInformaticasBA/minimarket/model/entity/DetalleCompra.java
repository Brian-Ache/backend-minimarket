package com.SolucionesInformaticasBA.minimarket.model.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "detalle_compra")
@Data
public class DetalleCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // revisar si necesita ser incremental

    @Column(name = "id_compra")
    private UUID idCompra;

    @Column(name = "id_producto")
    private UUID idProducto;

    private Integer cantidad;

    private BigDecimal precioUnitario;
}