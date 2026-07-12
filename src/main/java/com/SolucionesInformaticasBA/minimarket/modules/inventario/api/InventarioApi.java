package com.SolucionesInformaticasBA.minimarket.modules.inventario.api;

import java.util.List;
import java.util.UUID;

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
    StockResponse aumentar(MovimientoStockRequest request);
    StockResponse disminuir(MovimientoStockRequest request);
    void delete(UUID idProducto);
    void controlarStock(UUID idUsuario, AjusteStockRequest request);

    LoteResponse crear(LoteRequest request);
    List<LoteResponse> getAll();
    List<LoteResponse> getByEstado(String estado);

    List<MovimientoStockResponse> obtenerMovimientos(UUID idProducto);
}
