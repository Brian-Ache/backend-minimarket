package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.math.BigDecimal;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import lombok.Data;

@Data
public class VentaResponse {
    private UUID id;
    private LocalDateTime fecha;
    private BigDecimal total;
    private List<DetalleVentaResponse> detalles;
    private Boolean cobrada;
    private LocalDateTime fechaCobro;
    private String metodoPago;
    private BigDecimal montoRecibido;

    /** ONLINE u OFFLINE: por dónde entró el ticket al sistema. */
    private String origen;

    /** Qué caja lo generó. Solo en las ventas offline. */
    private String dispositivo;

    /** Cuándo llegó a MySQL. Null en las online, que llegan en el momento. */
    private LocalDateTime sincronizadoEn;

    /**
     * El ticket entró pero dejó una discrepancia: stock regularizado o un total que no
     * coincidió. No es un ticket con problemas, es un ticket que apunta a uno.
     */
    private boolean requiereRevision;
}
