package com.SolucionesInformaticasBA.minimarket.modules.caja.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.AbrirSesionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.ResumenCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.OrigenMovimientoCaja;

public interface CajaApi {
    SesionCajaResponse abrirSesion(UUID idUsuario, AbrirSesionRequest request);
    SesionCajaResponse getSesionActiva();

    /**
     * Una sesión cualquiera, abierta o cerrada. La usa el resumen de ventas por turno para
     * fecharse con la apertura del turno y no con el día en que se lo consulta.
     */
    SesionCajaResponse getSesionById(UUID id);

    /** Id de la sesión abierta. Falla si no hay ninguna. Nunca aceptar un idSesion del cliente. */
    UUID getIdSesionActiva();

    /** Igual que getIdSesionActiva pero vacío en vez de error cuando no hay turno abierto. */
    Optional<UUID> buscarSesionActiva();

    MovimientoCajaResponse registrarEntradaManual(UUID idUsuario, MovimientoCajaRequest request);
    MovimientoCajaResponse registrarSalidaManual(UUID idUsuario, MovimientoCajaRequest request);

    /**
     * Movimientos que escribe el sistema al registrar un comprobante. Exigen que la sesión
     * exista y siga abierta: aceptaban cualquier id, así que se podía imputar plata a un turno
     * ya cerrado y correrle el arqueo a un corte firmado.
     */
    MovimientoCajaResponse registrarEntradaAutomatica(UUID idSesion, UUID idUsuario, float monto, OrigenMovimientoCaja origen, UUID idReferencia);
    MovimientoCajaResponse registrarSalidaAutomatica(UUID idSesion, UUID idUsuario, float monto, OrigenMovimientoCaja origen, UUID idReferencia);

    /**
     * Movimientos del turno abierto —sin fechas— o de un rango. El rango es semiabierto y las
     * dos fechas van juntas: con una sola no hay período que consultar.
     */
    Page<MovimientoCajaResponse> getMovimientos(LocalDateTime desde, LocalDateTime hasta,
                                                Pageable pageable);
    ResumenCajaResponse getResumenSesion();
    ResumenCajaResponse getResumenDiario(LocalDate fecha);

    CorteResponse realizarCorte(UUID idUsuario, CorteRequest request);
    CorteResponse getCorteById(UUID id);
    CorteResponse getUltimoCorte();
    /** Paginado: suma una fila por turno cerrado y crece para siempre. */
    Page<CorteResponse> getHistorialCortes(Pageable pageable);
}
