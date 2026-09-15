package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.TipoEventoSync;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un evento de la cola del front.
 *
 * <p>Es deliberadamente plano: los campos de los cuatro tipos conviven en la misma clase y cada
 * tipo usa los suyos. Una jerarquía con {@code @JsonTypeInfo} sería más elegante en Java, pero
 * le agregaría al front —que escribe esto desde SQLite— una forma de serializar distinta por
 * tipo de evento, para ganar muy poco.
 *
 * <p>Lo que <b>no</b> lleva es un id de evento. La identidad es siempre el par
 * {@code tipo} + {@code uuid}, donde el uuid es el de la <b>entidad</b>: el del ticket en
 * {@code CREAR} y {@code ANULAR}, el de la sesión en los de caja. De ahí sale la idempotencia:
 * reenviar el lote entero no produce efectos nuevos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventoSyncRequest {

    /** La identidad de la entidad que el evento toca. UUIDv7 obligatorio. */
    @NotNull
    private UUID uuid;

    @NotNull
    private TipoEventoSync tipo;

    /**
     * Contador monótono del dispositivo. Solo se usa para desempatar dos eventos que quedaron
     * con el mismo {@code ocurridoEn}, cosa que pasa seguido: un ticket y su anulación
     * inmediata caen en el mismo segundo.
     */
    private long secuencia;

    /** Cuándo pasó en el local. Es la fecha que se persiste, no la de llegada. */
    @NotNull
    private LocalDateTime ocurridoEn;

    // --- CREAR -------------------------------------------------------------------------

    /** Quién vendió. Es un hecho de hace dos días, no quien está sincronizando. */
    private UUID idVendedor;

    /** El turno al que pertenece el ticket. Sin esto no se puede persistir. */
    private UUID idSesion;

    /** El total que sumó el front. Se compara contra el recalculado, no se guarda a ciegas. */
    private BigDecimal total;

    /** EFECTIVO, TARJETA o TRANSFERENCIA. */
    private String metodoPago;

    /** Lo que puso el cliente sobre el mostrador. Solo en efectivo. */
    private BigDecimal montoRecibido;

    @Valid
    private List<DetalleSyncRequest> detalles;

    // --- ANULAR ------------------------------------------------------------------------

    /** Quién anuló, o quién abrió o cerró el turno según el tipo. */
    private UUID idUsuario;

    private String motivo;

    // --- ABRIR_SESION / CERRAR_SESION --------------------------------------------------

    private BigDecimal saldoInicial;

    /** El conteo físico del cajero al cerrar. */
    private BigDecimal saldoFinal;

    private BigDecimal montoRetirado;

    private String observaciones;
}
