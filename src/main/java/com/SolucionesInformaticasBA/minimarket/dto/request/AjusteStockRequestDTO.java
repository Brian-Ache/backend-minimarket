package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.util.UUID;

import lombok.Data;

@Data
public class AjusteStockRequestDTO {

    private UUID idProducto;

    // stock REAL contado
    private int stockReal;

    private String motivo;
}