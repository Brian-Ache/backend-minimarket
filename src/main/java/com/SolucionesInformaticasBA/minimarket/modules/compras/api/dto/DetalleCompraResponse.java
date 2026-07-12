package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.util.UUID;

import lombok.*;

@Data
@Builder
public class DetalleCompraResponse {
    private UUID idCompra;
    private UUID idProducto;
    private String nombreProducto;
    private String barcode;

    private int cantidad;
    private float precioUnitario;
    private float total;
}
