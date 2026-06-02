package com.SolucionesInformaticasBA.minimarket.dto.request;

import lombok.Data;

@Data
public class MovimientoStockRequestDTO {

    private Long productoId;
    private Integer cantidad;
    private String tipo; // COMPRA, VENTA, AJUSTE, MERMA
    private String motivo;
}
