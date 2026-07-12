package com.SolucionesInformaticasBA.minimarket.modules.caja.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
        if (sesionCajaRepository.findByEstadoAndDeletedAtIsNull(EstadoSesion.ABIERTA).isPresent()) {
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
        SesionCaja sesion = sesionCajaRepository.findByEstadoAndDeletedAtIsNull(EstadoSesion.ABIERTA)
            .orElseThrow(() -> new BadRequestException("No hay una sesión de caja abierta"));
        return toSesionResponse(sesion);
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
        return toMovimientoResponse(movimientoCajaRepository.save(movimiento));
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
        return toMovimientoResponse(movimientoCajaRepository.save(movimiento));
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
        return toMovimientoResponse(movimientoCajaRepository.save(movimiento));
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
        return toMovimientoResponse(movimientoCajaRepository.save(movimiento));
    }

    @Override
    public List<MovimientoCajaResponse> getMovimientos(LocalDateTime desde, LocalDateTime hasta) {
        if (desde == null && hasta == null) {
            SesionCaja sesion = sesionCajaRepository.findByEstadoAndDeletedAtIsNull(EstadoSesion.ABIERTA)
                .orElseThrow(() -> new BadRequestException("No hay sesión activa. Especifique un rango de fechas."));
            return movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(sesion.getId())
                .stream().map(this::toMovimientoResponse).toList();
        }
        if (desde == null) desde = LocalDateTime.of(2000, 1, 1, 0, 0);
        if (hasta == null) hasta = LocalDateTime.now();
        return movimientoCajaRepository.findByCreatedAtBetweenAndDeletedAtIsNull(desde, hasta)
            .stream().map(this::toMovimientoResponse).toList();
    }

    @Override
    public ResumenCajaResponse getResumenDiario(LocalDate fecha) {
        SesionCaja sesion = obtenerSesionActiva();

        List<MovimientoCaja> movimientos = movimientoCajaRepository.findByIdSesionAndDeletedAtIsNull(sesion.getId());
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

        float saldoEsperado = sesion.getSaldoInicial() + totalVentas - totalCompras
            + totalEntradasManuales - totalSalidasManuales;

        return ResumenCajaResponse.builder()
            .fecha(fecha)
            .saldoInicial(sesion.getSaldoInicial())
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

        ResumenCajaResponse resumen = getResumenDiario(LocalDate.now());

        sesion.setSaldoEsperado(resumen.getSaldoEsperado());
        sesion.setSaldoFinal(request.getSaldoReal());
        sesion.setDiferencia(request.getSaldoReal() - resumen.getSaldoEsperado());
        sesion.setObservaciones(request.getObservaciones());
        sesion.setFechaCierre(LocalDateTime.now());
        sesion.setIdUsuarioCierre(idUsuario);
        sesion.setEstado(EstadoSesion.CERRADA);

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
        return sesionCajaRepository.findAll().stream()
            .filter(s -> s.getDeletedAt() == null && s.getEstado() == EstadoSesion.CERRADA)
            .map(s -> toCorteResponse(s, null, s.getSaldoFinal()))
            .toList();
    }

    private SesionCaja obtenerSesionActiva() {
        return sesionCajaRepository.findByEstadoAndDeletedAtIsNull(EstadoSesion.ABIERTA)
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
            resumen = ResumenCajaResponse.builder()
                .fecha(s.getFechaCierre() != null ? s.getFechaCierre().toLocalDate() : LocalDate.now())
                .saldoInicial(s.getSaldoInicial())
                .saldoEsperado(s.getSaldoEsperado() != null ? s.getSaldoEsperado() : 0)
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
