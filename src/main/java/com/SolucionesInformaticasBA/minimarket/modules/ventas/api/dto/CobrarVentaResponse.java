package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CobrarVentaResponse {
    private VentaResponse venta;
    private float cambio;
}
