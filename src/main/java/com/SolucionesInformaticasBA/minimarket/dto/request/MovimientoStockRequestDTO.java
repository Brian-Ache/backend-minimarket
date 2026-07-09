package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.util.UUID;

import lombok.Data;

@Data
public class MovimientoStockRequestDTO {

    private UUID idProducto;
    private int cantidad;
    private String tipo; // COMPRA, VENTA, AJUSTE, MERMA
    private String motivo;
}
