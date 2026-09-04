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

    /**
     * Anula la compra. Quién la anula sale del JWT, no de un parámetro: ese id queda escrito
     * en el movimiento de reversa de stock y en el de caja, que son las dos tablas con las que
     * después se audita quién tocó qué.
     */
    void delete(UUID id);
}
