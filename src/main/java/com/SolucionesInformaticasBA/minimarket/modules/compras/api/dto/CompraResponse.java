package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;

import lombok.*;

@Data
@Builder
public class CompraResponse {
    private UUID id;
    private LocalDateTime fecha;
    private float total;
    private List<DetalleCompraResponse> detalle;
    private ProveedorResponse proveedor;
    private String tipoComprobante;
    private String nroComprobante;
    private String observaciones;
}
