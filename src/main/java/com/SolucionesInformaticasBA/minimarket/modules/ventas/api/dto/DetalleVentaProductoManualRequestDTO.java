package com.SolucionesInformaticasBA.minimarket.dto.request;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class DetalleVentaProductoManualRequestDTO extends DetalleVentaRequestDTO {

    private String nombreManual;
    private BigDecimal precioUnitario;
}