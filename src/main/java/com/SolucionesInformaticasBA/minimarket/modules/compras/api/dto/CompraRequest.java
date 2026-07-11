package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.util.List;

import lombok.*;

@Data
@Builder
public class CompraRequest {
    private List<DetalleCompraRequest> detalle;
}
