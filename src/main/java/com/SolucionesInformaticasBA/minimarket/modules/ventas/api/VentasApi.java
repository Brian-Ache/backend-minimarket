package com.SolucionesInformaticasBA.minimarket.modules.ventas.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.CobrarVentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResumenDiarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaRequest;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;

public interface VentasApi {
    VentaResponse realizarVenta(UUID idUsuario, VentaRequest request);
    VentaResponse getById(UUID id);
    List<VentaResponse> getAll();
    List<VentaResponse> getByUsuario(UUID idUsuario);
    List<VentaResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta);

    /** Solo ventas cobradas, filtradas por fecha de cobro. Es la fuente de todo reporte de dinero. */
    List<VentaResponse> getByFechaCobradas(LocalDateTime desde, LocalDateTime hasta);
    void delete(UUID id);
    CobrarVentaResponse cobrar(UUID idVenta, UUID idUsuario, CobrarVentaRequest request);
    ResumenDiarioResponse getResumenDiario(LocalDate fecha);
    ResumenDiarioResponse getResumenPorSesion(UUID idSesion);
}
