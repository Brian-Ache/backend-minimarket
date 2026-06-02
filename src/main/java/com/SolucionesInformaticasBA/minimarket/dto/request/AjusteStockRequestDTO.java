package com.SolucionesInformaticasBA.minimarket.dto.request;

import lombok.Data;

@Data
public class AjusteStockRequestDTO {

    private Long productoId;

    // stock REAL contado
    private Integer stockReal;

    private String motivo;
}