package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CobrarVentaResponse {
    private VentaResponse venta;
    private BigDecimal cambio;
}
