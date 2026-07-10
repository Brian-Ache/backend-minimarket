package com.SolucionesInformaticasBA.minimarket.modules.stock.service;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.StockApi;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.MovimientoStockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.stock.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.stock.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.stock.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class StockService implements StockApi{
    private final MovimientoStockRepository movimientoStockRepository;
    private final ProductosApi productosApi;
    private final UsuarioApi usuarioApi;

    @Transactional
    public void ajustarStock(UUID idUsuario, AjusteStockRequest request){
        Usuario usuario = usuarioApi.getUsuarioById(idUsuario);

        Producto producto = productosApi.getProductoById(request.getIdProducto());

        if(request.getStockReal() < 0) throw new RuntimeException("Stock invalido");

        int stockActual = producto.getStock();
        int stockReal = request.getStockReal();

        // Diferencia
        int diferencia = stockReal - stockActual;

        if(diferencia == 0) return; // no hay cambios

        producto.setStock(stockReal);
        productosApi.saveEntity(producto);

        // Registro motivo (usar helper)
        String motivo = (request.getMotivo() != null) ? request.getMotivo() : "Ajuste manual de stock";
        MovimientoStock m = MovimientoStock.builder().idProducto(producto.getId())
        .cantidad(diferencia).tipo(TipoMovimiento.AJUSTE).motivo(motivo).idUsuario(usuario.getId()).build();

        movimientoStockRepository.save(m);
    }

    // Helpers

    private MovimientoStock toEntity(MovimientoStockRequest request){
        String motivo = (request.getMotivo() != null) ? request.getMotivo() : "Ajuste manual de stock";
        return MovimientoStock
            .builder().idProducto(request.getIdProducto())
            .cantidad(request.getCantidad())
            .tipo(TipoMovimiento.valueOf(request.getTipo()))
            .motivo(motivo)
            .build();
    }

    private MovimientoStockResponse toResponse(MovimientoStock m){
        return MovimientoStockResponse.builder()
            .id(m.getId())
            .idProducto(m.getIdProducto())
            .cantidad(m.getCantidad())
            .tipo(m.getTipo().name())
            .motivo(m.getMotivo())
            .fecha(m.getCreatedAt())
            .build();
    }
}
