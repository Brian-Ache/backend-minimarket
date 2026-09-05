package com.SolucionesInformaticasBA.minimarket.modules.compras.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraRequest;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.CompraResponse;
import com.SolucionesInformaticasBA.minimarket.modules.compras.api.dto.ProveedorDeProductoResponse;

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
     * Total comprado por día en el rango (semiabierto: incluye {@code desde}, excluye
     * {@code hasta}). Para los reportes, que solo necesitan importes: pedir el listado
     * completo traía todos los detalles y una consulta de proveedor por compra.
     *
     * <p>Los días sin compras no aparecen en el mapa.
     */
    Map<LocalDate, Float> getTotalesPorDia(LocalDateTime desde, LocalDateTime hasta);

    /**
     * Los proveedores que tienen o tuvieron el producto, con el precio que cada uno lista y lo
     * que se le pagó la última vez. Vive en compras porque es el único módulo que ya depende de
     * productos y de proveedores; el catálogo de referencia lo tiene productos.
     *
     * <p>Orden: primero los del catálogo, del más barato al más caro; después, por nombre, los
     * que solo aparecen en el historial de compras.
     */
    List<ProveedorDeProductoResponse> getProveedoresDeProducto(UUID idProducto);

    /**
     * Anula la compra. Quién la anula sale del JWT, no de un parámetro: ese id queda escrito
     * en el movimiento de reversa de stock y en el de caja, que son las dos tablas con las que
     * después se audita quién tocó qué.
     */
    void delete(UUID id);
}
