package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.List;

import lombok.Data;

@Data
public class VentaRequest {
    private List<DetalleVentaRequest> detalles;
}
