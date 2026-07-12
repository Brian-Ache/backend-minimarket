package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.util.List;
import java.util.UUID;

import lombok.*;

@Data
@Builder
public class CompraRequest {
    private List<DetalleCompraRequest> detalle;
    private UUID idProveedor;
    private String tipoComprobante;
    private String nroComprobante;
    private String observaciones;
    private UUID idSesion;
}
