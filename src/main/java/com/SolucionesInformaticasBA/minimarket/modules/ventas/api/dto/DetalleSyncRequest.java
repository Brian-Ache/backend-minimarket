package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Una línea del ticket offline.
 *
 * <p>A diferencia de {@link DetalleVentaRequest}, acá el {@code precioUnitario} viaja también
 * para las líneas de tipo {@code PRODUCTO} y se guarda tal cual: es el precio que el cliente
 * pagó hace dos días, no el de la lista de hoy. Es el mismo criterio de snapshot que el resto
 * del sistema usa para el costo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetalleSyncRequest {

    /** PRODUCTO o MANUAL. */
    @NotBlank
    private String tipo;

    /** Null en las líneas MANUAL. */
    private UUID idProducto;

    /** Solo en las líneas MANUAL. */
    private String nombreManual;

    @Positive
    private int cantidad;

    @NotNull
    private BigDecimal precioUnitario;
}
