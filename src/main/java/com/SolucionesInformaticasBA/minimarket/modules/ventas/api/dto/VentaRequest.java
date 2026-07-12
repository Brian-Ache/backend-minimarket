package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class VentaRequest {
    private List<DetalleVentaRequest> detalles;
    private UUID idSesion;
}
