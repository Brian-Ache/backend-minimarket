package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class CompraRequestDTO {

    private List<DetalleCompraRequestDTO> detalles;
}