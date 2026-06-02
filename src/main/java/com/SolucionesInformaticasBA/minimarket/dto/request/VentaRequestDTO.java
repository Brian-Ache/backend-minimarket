package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class VentaRequestDTO {
    private List<DetalleVentaRequestDTO> detalles;
}
