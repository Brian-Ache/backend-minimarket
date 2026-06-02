package com.SolucionesInformaticasBA.minimarket.dto.request;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ProductoRequestDTO {

    private String nombre;
    private String barcode;
    private BigDecimal precio;
    private boolean manejaLotes;
    private Integer stock;

    public boolean getManejaLotes() {
        return manejaLotes;
    }
}

