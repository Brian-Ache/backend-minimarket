package com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto;

import java.util.UUID;

import lombok.*;

@Data
@Builder
public class StockRequest {
    private UUID idProducto;
    private int cantidad;
}
