package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.util.UUID;

import lombok.Data;

@Data
public class DetalleVentaProductoSistemaRequestDTO extends DetalleVentaRequestDTO {

    private UUID idProducto;
}


