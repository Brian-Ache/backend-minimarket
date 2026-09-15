package com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto;

import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.CodigoErrorSync;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.enums.TipoEventoSync;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Qué pasó con un evento del lote. Hay uno por evento mandado, en el orden en que se procesaron. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoEventoSync {

    private UUID uuid;
    private TipoEventoSync tipo;

    /** OK o ERROR. */
    private String estado;

    /**
     * En un OK: el evento se persistió pero dejó una discrepancia —stock regularizado, o un
     * total que no coincidió—. <b>El evento ya está sincronizado igual</b>: el front lo puede
     * mostrar, pero lo saca de la cola.
     */
    private boolean requiereRevision;

    /** En un ERROR: cuál de los casos. Null en un OK. */
    private CodigoErrorSync codigo;

    /** En un ERROR: si volver a mandarlo puede cambiar algo. */
    private boolean reintentable;

    /** Texto para el operador o para el log. */
    private String mensaje;

    public static ResultadoEventoSync ok(EventoSyncRequest evento) {
        return ResultadoEventoSync.builder()
            .uuid(evento.getUuid())
            .tipo(evento.getTipo())
            .estado("OK")
            .build();
    }

    public static ResultadoEventoSync okConRevision(EventoSyncRequest evento, String mensaje) {
        return ResultadoEventoSync.builder()
            .uuid(evento.getUuid())
            .tipo(evento.getTipo())
            .estado("OK")
            .requiereRevision(true)
            .mensaje(mensaje)
            .build();
    }

    public static ResultadoEventoSync error(EventoSyncRequest evento, CodigoErrorSync codigo,
                                            String mensaje) {
        return ResultadoEventoSync.builder()
            .uuid(evento.getUuid())
            .tipo(evento.getTipo())
            .estado("ERROR")
            .codigo(codigo)
            .reintentable(codigo.isReintentable())
            .mensaje(mensaje)
            .build();
    }
}
