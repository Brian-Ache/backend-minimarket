package com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AjusteStockRequest {
    private UUID idProducto;
    private int stockReal;    // stock REAL contado
    private String motivo;
}
