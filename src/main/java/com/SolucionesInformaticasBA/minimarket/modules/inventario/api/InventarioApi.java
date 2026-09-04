package com.SolucionesInformaticasBA.minimarket.modules.inventario.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockResponse;

public interface InventarioApi {
    StockResponse crear(StockRequest request);
    StockResponse getByIdProducto(UUID idProducto);
    List<StockResponse> getByIdProductos(List<UUID> idProductos);
    StockResponse aumentar(MovimientoStockRequest request);
    StockResponse disminuir(MovimientoStockRequest request);
    /** Baja de la fila de stock. Exige que esté en cero. */
    void delete(UUID idProducto);
    void controlarStock(UUID idUsuario, AjusteStockRequest request);

    LoteResponse crear(LoteRequest request);
    List<LoteResponse> getAll();
    List<LoteResponse> getByEstado(String estado);

    /** Historial de movimientos del producto, del más reciente al más viejo. */
    Page<MovimientoStockResponse> obtenerMovimientos(UUID idProducto, Pageable pageable);

    /**
     * Existencias reales de cada producto: la tabla `stock` para los productos comunes y la
     * suma de sus lotes activos para los que manejan lotes. Se resuelve en dos consultas
     * agregadas, no una por producto.
     */
    Map<UUID, Integer> getExistenciasPorProducto();
}
