package com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;

import lombok.Builder;
import lombok.Data;

/**
 * Un proveedor que tiene o tuvo este producto: lo que lista y lo que se le pagó la última vez.
 *
 * <p>Es estrictamente informativo, para consultar al momento de cargar una compra. No propone
 * ni valida nada: a quién comprarle sigue siendo criterio del usuario.
 */
@Data
@Builder
public class ProveedorDeProductoResponse {
    private ProveedorResponse proveedor;

    /** Lo que el proveedor lista, cargado a mano. Null si nunca se cargó. */
    private BigDecimal precioReferencia;

    /** Null si a este proveedor nunca se le compró el producto. */
    private UltimaCompra ultimaCompra;

    /**
     * Lo que realmente se pagó la última vez, que puede no tener nada que ver con el precio de
     * referencia: ahí está la gracia de mostrar los dos juntos.
     */
    @Data
    @Builder
    public static class UltimaCompra {
        private LocalDateTime fecha;
        private float precioUnitario;
        private String tipoComprobante;
        private String nroComprobante;
    }
}
