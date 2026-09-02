package com.SolucionesInformaticasBA.minimarket.modules.compras.api;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraResponse;

public interface CompraApi {
    CompraResponse crear(UUID idUsuario, CompraRequest request);
    CompraResponse getById(UUID id);

    /**
     * Búsqueda unificada de compras con filtros opcionales.
     * Reemplaza getAll(), getByUsuario() y getByFecha() con un solo método
     * que acepta todos los filtros como parámetros null-safe.
     */
    Page<CompraResponse> getAllFiltered(UUID idProveedor, String tipoComprobante,
                                        LocalDateTime desde, LocalDateTime hasta,
                                        Pageable pageable);

    void delete(UUID id, UUID idUsuario);

    // MÉTODOS COMENTADOS: Se reemplazaron por getAllFiltered() que cubre todos los casos
    // con un solo query parametrizado. Se mantienen comentados por si en el futuro
    // se necesitan endpoints dedicados (ej: historial por un usuario específico).
    // List<CompraResponse> getAll();
    // List<CompraResponse> getByUsuario(UUID idUsuario);
    // List<CompraResponse> getByFecha(LocalDateTime desde, LocalDateTime hasta);
}
