package com.SolucionesInformaticasBA.minimarket.modules.ventas.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResumenDiarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;

public interface VentasApi {
    VentaResponse realizarVenta(UUID idUsuario, VentaRequest request);
    VentaResponse getById(UUID id);
    /**
     * @param idUsuario acota el listado a un usuario; en null trae las ventas de todos. Quién
     *        puede pedir qué lo decide el controller: un empleado solo ve las suyas.
     */
    Page<VentaResponse> getAll(UUID idUsuario, Pageable pageable);

    Page<VentaResponse> getByFecha(UUID idUsuario, LocalDateTime desde, LocalDateTime hasta,
                                   Pageable pageable);

    /** Solo ventas cobradas, filtradas por fecha de cobro. Es la fuente de todo reporte de dinero. */
    List<VentaResponse> getByFechaCobradas(LocalDateTime desde, LocalDateTime hasta);
    void delete(UUID id);

    /**
     * Anula una venta que quedó sin cobrar más tiempo del permitido, devolviendo su mercadería
     * al stock. La dispara el barrido de reservas vencidas, no una persona.
     */
    void anularPorReservaVencida(UUID id);
    CobrarVentaResponse cobrar(UUID idVenta, UUID idUsuario, CobrarVentaRequest request);
    ResumenDiarioResponse getResumenDiario(LocalDate fecha);
    ResumenDiarioResponse getResumenPorSesion(UUID idSesion);
}
