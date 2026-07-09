package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.util.UUID;

import lombok.Data;

@Data
public class DetalleCompraResponseDTO {

    private UUID idProducto;
    private String nombreProducto;

    private int cantidad;
    private float precioUnitario;
}
