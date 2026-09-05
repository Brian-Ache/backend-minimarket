package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

/**
 * Conteo físico de un producto que maneja lotes, repartido por lote.
 *
 * <p>Es <b>parcial</b>: los lotes que no aparecen en {@code conteos} quedan como estaban. Se
 * cuenta lo que se ve en la góndola, y un conteo incompleto no puede borrar existencias que
 * nadie miró.
 */
@Data
@Builder
public class AjusteLotesRequest {
    @NotNull
    private UUID idProducto;

    @NotEmpty
    @Valid
    private List<ConteoLoteRequest> conteos;

    private String motivo;
}
