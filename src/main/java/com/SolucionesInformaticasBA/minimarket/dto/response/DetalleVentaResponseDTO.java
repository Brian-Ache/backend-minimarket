package com.SolucionesInformaticasBA.minimarket.dto.response;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class DetalleVentaResponseDTO {

    private Long productoId; // null si es manual

    private String nombre;   // 🔥 SIEMPRE lleno

    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;

    private String tipo; // 🔥 opcional pero MUY útil
}
