package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class DetalleCompraResponseDTO {

    private Long productoId;
    private String nombreProducto;

    private Integer cantidad;
    private BigDecimal precioUnitario;
}
