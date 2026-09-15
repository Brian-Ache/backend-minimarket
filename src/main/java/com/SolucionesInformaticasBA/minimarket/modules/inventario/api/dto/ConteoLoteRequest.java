package com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Lo que se contó de un lote. Cero es un conteo válido: el lote se agotó. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConteoLoteRequest {
    @NotNull
    private UUID idLote;

    @PositiveOrZero
    private int cantidadReal;
}
