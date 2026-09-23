package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MovimientoStockResponse {
    private UUID id;
    private UUID idProducto;
    private int cantidad;
    private String tipo;
    private String motivo;
    private LocalDateTime fecha;

    // Sin estos tres, el historial de stock no alcanzaba para auditar: no se podía saber de
    // qué lote salió cada unidad, qué comprobante lo originó ni quién lo cargó.
    private UUID idLote;
    private UUID idReferencia;
    private UUID idUsuario;
}
