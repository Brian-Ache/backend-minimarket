package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.*;

@Data
@Builder
public class CompraResponse {
    private UUID id;
    private LocalDateTime fecha;
    private float total;
    private List<DetalleCompraResponse> detalle;
}
