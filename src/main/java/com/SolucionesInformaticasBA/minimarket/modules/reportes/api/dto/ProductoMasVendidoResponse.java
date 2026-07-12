package com.SolucionesInformaticasBA.minimarket.modules.reportes.api.dto;

import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductoMasVendidoResponse {
    private UUID idProducto;
    private String nombre;
    private String barcode;
    private int cantidadVendida;
    private float totalVendido;
}
