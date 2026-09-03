package com.SolucionesInformaticasBA.minimarket.modules.caja.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.AbrirSesionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.ResumenCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;

public interface CajaApi {
    SesionCajaResponse abrirSesion(UUID idUsuario, AbrirSesionRequest request);
    SesionCajaResponse getSesionActiva();

    /** Id de la sesión abierta. Falla si no hay ninguna. Nunca aceptar un idSesion del cliente. */
    UUID getIdSesionActiva();

    /** Igual que getIdSesionActiva pero vacío en vez de error cuando no hay turno abierto. */
    Optional<UUID> buscarSesionActiva();

    MovimientoCajaResponse registrarEntradaManual(UUID idUsuario, MovimientoCajaRequest request);
    MovimientoCajaResponse registrarSalidaManual(UUID idUsuario, MovimientoCajaRequest request);

    MovimientoCajaResponse registrarEntradaAutomatica(UUID idSesion, UUID idUsuario, float monto, String origen, UUID idReferencia);
    MovimientoCajaResponse registrarSalidaAutomatica(UUID idSesion, UUID idUsuario, float monto, String origen, UUID idReferencia);

    List<MovimientoCajaResponse> getMovimientos(LocalDateTime desde, LocalDateTime hasta);
    ResumenCajaResponse getResumenSesion();
    ResumenCajaResponse getResumenDiario(LocalDate fecha);

    CorteResponse realizarCorte(UUID idUsuario, CorteRequest request);
    CorteResponse getCorteById(UUID id);
    CorteResponse getUltimoCorte();
    List<CorteResponse> getHistorialCortes();
}
