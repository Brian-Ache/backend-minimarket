package com.SolucionesInformaticasBA.minimarket.modules.inventario.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteLotesRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockResponse;

public interface InventarioApi {
    StockResponse getByIdProducto(UUID idProducto);
    List<StockResponse> getByIdProductos(List<UUID> idProductos);
    StockResponse aumentar(MovimientoStockRequest request);
    StockResponse disminuir(MovimientoStockRequest request);
    /** Baja de la fila de stock. Exige que esté en cero. */
    void delete(UUID idProducto);
    void controlarStock(UUID idUsuario, AjusteStockRequest request);

    /**
     * Ajuste contra el conteo físico de un producto que maneja lotes, repartido por lote. Es
     * parcial: los lotes que el request no menciona quedan como estaban. Devuelve todos los
     * lotes del producto ya actualizados.
     */
    List<LoteResponse> ajustarLotes(UUID idUsuario, AjusteLotesRequest request);

    /**
     * Los lotes del producto que tiene sentido ofrecer para un conteo: sin los vencidos y sin
     * los que están en cero desde hace más de {@code EstadoLote.DIAS_LOTE_AGOTADO} días.
     */
    List<LoteResponse> getLotesAjustables(UUID idProducto);

    LoteResponse crear(LoteRequest request);

    /**
     * Paginados: la tabla suma un lote por cada línea de compra de un producto con
     * vencimiento, así que el listado completo crecía sin techo.
     */
    Page<LoteResponse> getAll(Pageable pageable);
    Page<LoteResponse> getByEstado(String estado, Pageable pageable);

    /** Historial de movimientos del producto, del más reciente al más viejo. */
    Page<MovimientoStockResponse> obtenerMovimientos(UUID idProducto, Pageable pageable);

    /**
     * Existencias reales de cada producto: la tabla `stock` para los productos comunes y la
     * suma de sus lotes activos para los que manejan lotes. Se resuelve en dos consultas
     * agregadas, no una por producto.
     */
    Map<UUID, Integer> getExistenciasPorProducto();

    /**
     * Lo mismo acotado a los productos pedidos, con las mismas dos consultas agregadas. Es lo
     * que usa un reporte paginado: pedir las existencias de todo el catálogo para armar una
     * página de 20 anulaba el trabajo de paginarla.
     */
    Map<UUID, Integer> getExistenciasPorProductos(Collection<UUID> idProductos);
}
