package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class VentaRequest {
    @NotEmpty(message = "La venta debe tener al menos un detalle")
    @Valid
    private List<DetalleVentaRequest> detalles;

    // idSesion ya no se recibe del cliente: la sesión de caja se resuelve al cobrar,
    // y solo para pagos en efectivo. Ver CobrarVentaRequest.
}
