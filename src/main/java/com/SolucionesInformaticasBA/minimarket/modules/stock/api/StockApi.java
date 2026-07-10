package com.SolucionesInformaticasBA.minimarket.modules.stock.api;

import java.util.UUID;

import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.AjusteStockRequest;

public interface StockApi {
    void ajustarStock(UUID idUsuario, AjusteStockRequest request);
}
