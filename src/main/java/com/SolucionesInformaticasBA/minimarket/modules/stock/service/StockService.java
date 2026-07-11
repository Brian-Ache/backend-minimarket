package com.SolucionesInformaticasBA.minimarket.modules.stock.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.StockApi;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.AjusteStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.MovimientoStockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.StockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.stock.api.dto.StockResponse;
import com.SolucionesInformaticasBA.minimarket.modules.stock.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.stock.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.stock.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.stock.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.stock.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.entity.Usuario;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class StockService implements StockApi{
    private final MovimientoStockRepository movimientoStockRepository;
    private final StockRepository stockRepository;
    private final ProductosApi productosApi;
    private final UsuarioApi usuarioApi;

    //Crud de Stock
    @Transactional
    public StockResponse crear(StockRequest request){
        Stock stock = toEntity(request);
        Stock guadado = stockRepository.save(stock);

        return toResponse(guadado);
    }

    public StockResponse getByIdProducto(UUID idProducto){
        Stock s = stockRepository.findByIdProductoAndDeletedAtIsNull(idProducto);
        return toResponse(s);
    }

    public StockResponse aumentar(StockRequest request){
        Stock stock = stockRepository.findByIdProductoAndDeletedAtIsNull(request.getIdProducto());
        int nuevoStock = stock.getCantidad() + request.getCantidad();

        stock.setCantidad(nuevoStock);
        Stock guardado = stockRepository.save(stock);

        return toResponse(guardado);
    }

    public StockResponse disminuir(StockRequest request){
        Stock stock = stockRepository.findByIdProductoAndDeletedAtIsNull(request.getIdProducto());
        int nuevoStock = stock.getCantidad() - request.getCantidad();
        
        stock.setCantidad(nuevoStock);
        Stock guardado = stockRepository.save(stock);

        return toResponse(guardado);
    }

    public void delete(UUID idProducto){
        Stock s = stockRepository.findByIdProductoAndDeletedAtIsNull(idProducto);
        s.setDeletedAt(LocalDateTime.now());
        stockRepository.save(s);
    }

    @Transactional
    public void ajustar(UUID idUsuario, AjusteStockRequest request){
        if(!usuarioApi.existById(idUsuario)){
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        if(!productosApi.existsById(request.getIdProducto())){
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        if(request.getStockReal() < 0) throw new RuntimeException("Stock invalido");

        int stockActual = productosApi.getById(request.getIdProducto()).getStock();
        int stockReal = request.getStockReal();

        // Diferencia
        int diferencia = stockReal - stockActual;

        if(diferencia == 0) return; // no hay cambios

        // Registro motivo (usar helper)
        if(request.getMotivo() == null) ??  request.setMotivo("Ajuste manual de stock");

        MovimientoStock m = MovimientoStock.builder().idProducto(producto.getId())
        .cantidad(diferencia).tipo(TipoMovimiento.AJUSTE).motivo(motivo).idUsuario(usuario.getId()).build();

        movimientoStockRepository.save(m);
    }

    // Helpers

    private Stock toEntity(StockRequest request){
        return Stock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(request.getCantidad()).build();
    }

    private StockResponse toResponse(Stock stock){
        return StockResponse.builder().idProducto(stock.getIdProducto())
            .cantidad(stock.getCantidad()).build();
    }
}
