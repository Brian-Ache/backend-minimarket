package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AjusteStockRequest {
    private UUID idProducto;
    private int stockReal;
    private String tipo;
    private String motivo;
}
