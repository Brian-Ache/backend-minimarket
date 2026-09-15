package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un lote de eventos de una caja que estuvo sin conexión.
 *
 * <p>El tope de eventos no es por tamaño del payload —cien tickets de cinco líneas son unos
 * 70 KB— sino por cuánto tiempo el request retiene la base: cada evento es su propia
 * transacción y toma locks de fila sobre stock y lotes. El límite vive en
 * {@code sync.lote.maximo} y se valida en el servicio, que es donde se puede poner el número
 * en el mensaje de error para que el front trocee sin adivinarlo.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncLoteRequest {

    /** Qué caja generó estos eventos ("caja-01"). Se guarda en cada venta del lote. */
    @Size(max = 50)
    private String dispositivo;

    @NotEmpty(message = "El lote tiene que traer al menos un evento")
    @Valid
    private List<EventoSyncRequest> eventos;
}
