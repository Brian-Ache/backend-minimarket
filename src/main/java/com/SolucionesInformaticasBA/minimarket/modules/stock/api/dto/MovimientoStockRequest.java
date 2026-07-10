package com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MovimientoStockRequest {
    private UUID idProducto;
    private int cantidad;
    private String tipo; // COMPRA, VENTA, AJUSTE, MERMA
    private String motivo;
}
