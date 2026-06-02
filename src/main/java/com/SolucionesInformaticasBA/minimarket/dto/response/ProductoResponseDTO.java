package com.SolucionesInformaticasBA.minimarket.dto.response;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class ProductoResponseDTO {

    private Long id;
    private String nombre;
    private String barcode;
    private BigDecimal precio;
    private boolean manejaLotes;
    private Integer stock;
}
