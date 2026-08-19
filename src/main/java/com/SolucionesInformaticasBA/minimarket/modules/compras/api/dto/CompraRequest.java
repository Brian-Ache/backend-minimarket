package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@Builder
public class CompraRequest {
    @NotEmpty(message = "La compra debe tener al menos un detalle")
    @Valid
    private List<DetalleCompraRequest> detalle;

    private UUID idProveedor;

    @Size(max = 20)
    private String tipoComprobante;

    @Size(max = 50)
    private String nroComprobante;

    @Size(max = 255)
    private String observaciones;

    /**
     * true = se pagó con la plata de la caja, así que genera la salida en la sesión abierta.
     * Reemplaza al idSesion que antes mandaba el cliente.
     */
    private boolean pagoEnEfectivo;
}
