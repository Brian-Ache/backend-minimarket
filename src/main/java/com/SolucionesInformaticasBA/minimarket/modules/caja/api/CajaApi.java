package com.SolucionesInformaticasBA.minimarket.modules.caja.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

    MovimientoCajaResponse registrarEntradaManual(UUID idUsuario, MovimientoCajaRequest request);
    MovimientoCajaResponse registrarSalidaManual(UUID idUsuario, MovimientoCajaRequest request);

    MovimientoCajaResponse registrarEntradaAutomatica(UUID idSesion, UUID idUsuario, float monto, String origen, UUID idReferencia);
    MovimientoCajaResponse registrarSalidaAutomatica(UUID idSesion, UUID idUsuario, float monto, String origen, UUID idReferencia);

    List<MovimientoCajaResponse> getMovimientos(LocalDateTime desde, LocalDateTime hasta);
    ResumenCajaResponse getResumenDiario(LocalDate fecha);

    CorteResponse realizarCorte(UUID idUsuario, CorteRequest request);
    CorteResponse getCorteById(UUID id);
    CorteResponse getUltimoCorte();
    List<CorteResponse> getHistorialCortes();
}
