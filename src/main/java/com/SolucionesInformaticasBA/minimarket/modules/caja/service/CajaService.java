package com.SolucionesInformaticasBA.minimarket.modules.caja.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.AbrirSesionRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.CorteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.MovimientoCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.ResumenCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.MovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.entity.SesionCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.EstadoSesion;
import com.SolucionesInformaticasBA.minimarket.modules.caja.enums.TipoMovimientoCaja;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.MovimientoCajaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.caja.repository.SesionCajaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class CajaService implements CajaApi {
    private final SesionCajaRepository sesionCajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    @Override
    @Transactional
    public SesionCajaResponse abrirSesion(UUID idUsuario, AbrirSesionRequest request) {
        if (sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA).isPresent()) {
            throw new BadRequestException("Ya existe una sesión de caja abierta. Debe cerrarla antes de abrir una nueva.");
        }

        SesionCaja sesion = SesionCaja.builder()
            .fechaApertura(LocalDateTime.now())
            .saldoInicial(request.getSaldoInicial())
            .idUsuarioApertura(idUsuario)
            .estado(EstadoSesion.ABIERTA)
            .build();

        return toSesionResponse(sesionCajaRepository.save(sesion));
    }

    @Override
    public SesionCajaResponse getSesionActiva() {
        return toSesionResponse(obtenerSesionActiva());
    }

    @Override
    public UUID getIdSesionActiva() {
        return obtenerSesionActiva().getId();
    }

    @Override
    public Optional<UUID> buscarSesionActiva() {
        return sesionCajaRepository
            .findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA)
            .map(SesionCaja::getId);
    }

    @Override
    @Transactional
    public MovimientoCajaResponse registrarEntradaManual(UUID idUsuario, MovimientoCajaRequest request) {
        SesionCaja sesion = obtenerSesionActiva();
        MovimientoCaja movimiento = MovimientoCaja.builder()
            .idSesion(sesion.getId())
            .tipo(TipoMovimientoCaja.ENTRADA)
            .monto(request.getMonto())
            .motivo(request.getMotivo())
            .idUsuario(idUsuario)
            .origen("MANUAL")
            .build();
        return toMovimientoResponse(movimientoCajaRepository.saveAndFlush(movimiento));
    }

    @Override
    @Transactional
    public MovimientoCajaResponse registrarSalidaManual(UUID idUsuario, MovimientoCajaRequest request) {
        SesionCaja sesion = obtenerSesionActiva();
        MovimientoCaja movimiento = MovimientoCaja.builder()
            .idSesion(sesion.getId())
            .tipo(TipoMovimientoCaja.SALIDA)
            .monto(request.getMonto())
            .motivo(request.getMotivo())
            .idUsuario(idUsuario)
            .origen("MANUAL")
            .build();
        return toMovimientoResponse(movimientoCajaRepository.saveAndFlush(movimiento));
    }

    @Override
    @Transactional
    public MovimientoCajaResponse registrarEntradaAutomatica(
            UUID idSesion, UUID idUsuario, float monto, String origen, UUID idReferencia) {
        MovimientoCaja movimiento = MovimientoCaja.builder()
            .idSesion(idSesion)
            .tipo(TipoMovimientoCaja.ENTRADA)
            .monto(monto)
            .idUsuario(idUsuario)
            .origen(origen)
            .idReferencia(idReferencia)
            .build();
        return toMovimientoResponse(movimientoCajaRepository.saveAndFlush(movimiento));
    }

    @Override
    @Transactional
    public MovimientoCajaResponse registrarSalidaAutomatica(
            UUID idSesion, UUID idUsuario, float monto, String origen, UUID idReferencia) {
        MovimientoCaja movimiento = MovimientoCaja.builder()
            .idSesion(idSesion)
            .tipo(TipoMovimientoCaja.SALIDA)
            .monto(monto)
            .idUsuario(idUsuario)
            .origen(origen)
            .idReferencia(idReferencia)
            .build();
        return toMovimientoResponse(movimientoCajaRepository.saveAndFlush(movimiento));
    }

    @Override
    public List<MovimientoCajaResponse> getMovimientos(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null && hasta == null) {
            SesionCaja sesion = sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA)
                .orElseThrow(() -> new BadRequestException("No hay sesión activa. Especifique un rango de fechas."));
            return movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(sesion.getId())
                .stream().map(this::toMovimientoResponse).toList();
        }
        if (desde == null) desde = LocalDateTime.of(2000, 1, 1, 0, 0);
        if (hasta == null) hasta = LocalDateTime.now();
        return movimientoCajaRepository.findEnRango(desde, hasta)
            .stream().map(this::toMovimientoResponse).toList();
    }

    /** Estado del turno abierto: es lo que el cajero mira antes de cerrar. */
    @Override
    public ResumenCajaResponse getResumenSesion() {
        SesionCaja sesion = obtenerSesionActiva();
        return calcularResumen(
            LocalDate.now(),
            sesion.getSaldoInicial(),
            movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(sesion.getId()));
    }

    /**
     * Resumen de un día cualquiera, calculado sobre los movimientos de esa fecha.
     *
     * <p>No exige que haya una sesión abierta: antes devolvía siempre los datos del turno
     * activo e ignoraba la fecha pedida, así que era imposible consultar un día ya cerrado.
     * El saldo inicial es el de las sesiones abiertas ese día.
     */
    @Override
    public ResumenCajaResponse getResumenDiario(LocalDate fecha) {
        LocalDateTime desde = fecha.atStartOfDay();
        LocalDateTime hasta = fecha.plusDays(1).atStartOfDay();

        float saldoInicial = (float) sesionCajaRepository
            .findByFechaAperturaGreaterThanEqualAndFechaAperturaLessThanAndDeletedAtIsNull(desde, hasta)
            .stream().mapToDouble(SesionCaja::getSaldoInicial).sum();

        return calcularResumen(fecha, saldoInicial,
            movimientoCajaRepository.findEnRango(desde, hasta));
    }

    private ResumenCajaResponse calcularResumen(
            LocalDate fecha, float saldoInicial, List<MovimientoCaja> movimientos) {
        List<MovimientoCaja> ventas = movimientos.stream()
            .filter(m -> "VENTA".equals(m.getOrigen()) && m.getTipo() == TipoMovimientoCaja.ENTRADA)
            .toList();
        List<MovimientoCaja> compras = movimientos.stream()
            .filter(m -> "COMPRA".equals(m.getOrigen()) && m.getTipo() == TipoMovimientoCaja.SALIDA)
            .toList();
        List<MovimientoCaja> entradasManuales = movimientos.stream()
            .filter(m -> "MANUAL".equals(m.getOrigen()) && m.getTipo() == TipoMovimientoCaja.ENTRADA)
            .toList();
        List<MovimientoCaja> salidasManuales = movimientos.stream()
            .filter(m -> "MANUAL".equals(m.getOrigen()) && m.getTipo() == TipoMovimientoCaja.SALIDA)
            .toList();

        float totalVentas = (float) ventas.stream().mapToDouble(MovimientoCaja::getMonto).sum();
        float totalCompras = (float) compras.stream().mapToDouble(MovimientoCaja::getMonto).sum();
        float totalEntradasManuales = (float) entradasManuales.stream().mapToDouble(MovimientoCaja::getMonto).sum();
        float totalSalidasManuales = (float) salidasManuales.stream().mapToDouble(MovimientoCaja::getMonto).sum();

        // Se calcula sobre todos los movimientos, no sumando las categorías de arriba:
        // así cualquier origen nuevo (por ejemplo REVERSA) queda contemplado en el arqueo.
        float totalEntradas = (float) movimientos.stream()
            .filter(m -> m.getTipo() == TipoMovimientoCaja.ENTRADA)
            .mapToDouble(MovimientoCaja::getMonto).sum();
        float totalSalidas = (float) movimientos.stream()
            .filter(m -> m.getTipo() == TipoMovimientoCaja.SALIDA)
            .mapToDouble(MovimientoCaja::getMonto).sum();

        float saldoEsperado = saldoInicial + totalEntradas - totalSalidas;

        return ResumenCajaResponse.builder()
            .fecha(fecha)
            .saldoInicial(saldoInicial)
            .totalVentas(totalVentas)
            .cantidadVentas(ventas.size())
            .totalCompras(totalCompras)
            .cantidadCompras(compras.size())
            .totalEntradasManuales(totalEntradasManuales)
            .totalSalidasManuales(totalSalidasManuales)
            .saldoEsperado(saldoEsperado)
            .build();
    }

    @Override
    @Transactional
    public CorteResponse realizarCorte(UUID idUsuario, CorteRequest request) {
        SesionCaja sesion = obtenerSesionActiva();

        ResumenCajaResponse resumen = calcularResumen(
            LocalDate.now(),
            sesion.getSaldoInicial(),
            movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(sesion.getId()));

        sesion.setSaldoEsperado(resumen.getSaldoEsperado());
        sesion.setSaldoFinal(request.getSaldoReal());
        sesion.setDiferencia(request.getSaldoReal() - resumen.getSaldoEsperado());
        sesion.setObservaciones(request.getObservaciones());
        sesion.setFechaCierre(LocalDateTime.now());
        sesion.setIdUsuarioCierre(idUsuario);
        sesion.setEstado(EstadoSesion.CERRADA);

        // El desglose se congela con el cierre: el historial lo devuelve tal cual quedó,
        // sin recalcularlo sobre movimientos que podrían cambiar después.
        sesion.setTotalVentas(resumen.getTotalVentas());
        sesion.setCantidadVentas(resumen.getCantidadVentas());
        sesion.setTotalCompras(resumen.getTotalCompras());
        sesion.setCantidadCompras(resumen.getCantidadCompras());
        sesion.setTotalEntradasManuales(resumen.getTotalEntradasManuales());
        sesion.setTotalSalidasManuales(resumen.getTotalSalidasManuales());

        SesionCaja cerrada = sesionCajaRepository.save(sesion);

        return toCorteResponse(cerrada, resumen, request.getSaldoReal());
    }

    @Override
    public CorteResponse getCorteById(UUID id) {
        SesionCaja sesion = sesionCajaRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("Corte no encontrado"));
        if (sesion.getEstado() != EstadoSesion.CERRADA) {
            throw new BadRequestException("La sesión de caja no está cerrada");
        }
        return toCorteResponse(sesion, null, sesion.getSaldoFinal());
    }

    @Override
    public CorteResponse getUltimoCorte() {
        SesionCaja sesion = sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.CERRADA)
            .orElseThrow(() -> new ResourceNotFoundException("No hay cortes registrados"));
        return toCorteResponse(sesion, null, sesion.getSaldoFinal());
    }

    @Override
    public List<CorteResponse> getHistorialCortes() {
        return sesionCajaRepository
            .findByEstadoAndDeletedAtIsNullOrderByFechaCierreDesc(EstadoSesion.CERRADA)
            .stream()
            .map(s -> toCorteResponse(s, null, s.getSaldoFinal()))
            .toList();
    }

    // findTop en lugar de findBy: si por una carrera quedaran dos sesiones abiertas,
    // esto degrada tomando la más reciente en vez de romper con NonUniqueResultException.
    private SesionCaja obtenerSesionActiva() {
        return sesionCajaRepository.findTopByEstadoAndDeletedAtIsNullOrderByCreatedAtDesc(EstadoSesion.ABIERTA)
            .orElseThrow(() -> new BadRequestException("No hay una sesión de caja abierta"));
    }

    private SesionCajaResponse toSesionResponse(SesionCaja s) {
        return SesionCajaResponse.builder()
            .id(s.getId())
            .fechaApertura(s.getFechaApertura())
            .saldoInicial(s.getSaldoInicial())
            .estado(s.getEstado().name())
            .idUsuarioApertura(s.getIdUsuarioApertura())
            .build();
    }

    private MovimientoCajaResponse toMovimientoResponse(MovimientoCaja m) {
        return MovimientoCajaResponse.builder()
            .id(m.getId())
            .idSesion(m.getIdSesion())
            .tipo(m.getTipo().name())
            .monto(m.getMonto())
            .motivo(m.getMotivo())
            .origen(m.getOrigen())
            .idReferencia(m.getIdReferencia())
            .fecha(m.getCreatedAt())
            .build();
    }

    private CorteResponse toCorteResponse(SesionCaja s, ResumenCajaResponse resumen, Float saldoReal) {
        if (resumen == null) {
            // Se reconstruye desde lo que guardó el cierre. Los cortes anteriores a esta
            // versión no lo tienen: van con null, que es "dato desconocido", y no con 0,
            // que se leería como "no hubo ventas".
            resumen = ResumenCajaResponse.builder()
                .fecha(s.getFechaCierre() != null ? s.getFechaCierre().toLocalDate() : LocalDate.now())
                .saldoInicial(s.getSaldoInicial())
                .saldoEsperado(s.getSaldoEsperado() != null ? s.getSaldoEsperado() : 0)
                .totalVentas(s.getTotalVentas())
                .cantidadVentas(s.getCantidadVentas())
                .totalCompras(s.getTotalCompras())
                .cantidadCompras(s.getCantidadCompras())
                .totalEntradasManuales(s.getTotalEntradasManuales())
                .totalSalidasManuales(s.getTotalSalidasManuales())
                .build();
        }
        return CorteResponse.builder()
            .id(s.getId())
            .fechaApertura(s.getFechaApertura())
            .fechaCierre(s.getFechaCierre())
            .saldoInicial(s.getSaldoInicial())
            .saldoEsperado(s.getSaldoEsperado() != null ? s.getSaldoEsperado() : 0)
            .saldoReal(saldoReal != null ? saldoReal : 0)
            .diferencia(s.getDiferencia() != null ? s.getDiferencia() : 0)
            .observaciones(s.getObservaciones())
            .idUsuarioApertura(s.getIdUsuarioApertura())
            .idUsuarioCierre(s.getIdUsuarioCierre())
            .resumen(resumen)
            .build();
    }
}
