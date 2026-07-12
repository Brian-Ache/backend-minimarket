package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.*;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.*;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.ProductoResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class InventarioService implements InventarioApi{
    private final StockRepository stockRepository;
    private final LoteRepository loteRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final ProductosApi productosApi;
    private final UsuarioApi usuarioApi;

    @Transactional
    public StockResponse crear(StockRequest request){
        if (!productosApi.existsById(request.getIdProducto())) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        Stock stock = toStockEntity(request);
        Stock guadado = stockRepository.save(stock);

        return toStockResponse(guadado);
    }

    public StockResponse getByIdProducto(UUID idProducto){
        Stock s = stockRepository.findByIdProductoAndDeletedAtIsNull(idProducto);
        return toStockResponse(s);
    }

    @Transactional
    public StockResponse aumentar(MovimientoStockRequest request){
        Stock stock = stockRepository.findByIdProductoAndDeletedAtIsNull(request.getIdProducto());
        if (stock == null) {
            throw new ResourceNotFoundException("Stock no encontrado para el producto");
        }
        int nuevoStock = stock.getCantidad() + request.getCantidad();

        stock.setCantidad(nuevoStock);
        stockRepository.save(stock);

        MovimientoStock m = MovimientoStock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(request.getCantidad())
            .tipo(TipoMovimiento.valueOf(request.getTipo()))
            .motivo(request.getMotivo())
            .idUsuario(request.getIdUsuario())
            .build();
        movimientoStockRepository.save(m);

        return toStockResponse(stock);
    }

    @Transactional
    public StockResponse disminuir(MovimientoStockRequest request){
        Stock stock = stockRepository.findByIdProductoAndDeletedAtIsNull(request.getIdProducto());
        if (stock == null) {
            throw new ResourceNotFoundException("Stock no encontrado para el producto");
        }

        int nuevoStock = stock.getCantidad() - request.getCantidad();
        if (nuevoStock < 0) {
            throw new BadRequestException("Stock insuficiente. Disponible: " + stock.getCantidad() + ", solicitado: " + request.getCantidad());
        }

        stock.setCantidad(nuevoStock);
        stockRepository.save(stock);

        MovimientoStock m = MovimientoStock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(-request.getCantidad())
            .tipo(TipoMovimiento.valueOf(request.getTipo()))
            .motivo(request.getMotivo())
            .idUsuario(request.getIdUsuario())
            .build();
        movimientoStockRepository.save(m);

        return toStockResponse(stock);
    }

    @Transactional
    public void delete(UUID idProducto){
        Stock s = stockRepository.findByIdProductoAndDeletedAtIsNull(idProducto);
        s.setDeletedAt(LocalDateTime.now());
        stockRepository.save(s);
    }

    @Transactional
    public void controlarStock(UUID idUsuario, AjusteStockRequest request){
        if(!usuarioApi.existById(idUsuario)){
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        if(!productosApi.existsById(request.getIdProducto())){
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        if(request.getStockReal() < 0) throw new RuntimeException("Stock invalido");

        Stock stock = stockRepository.findByIdProductoAndDeletedAtIsNull(request.getIdProducto());
        if(stock == null) throw new ResourceNotFoundException("Stock no encontrado para el producto");

        int diferencia = request.getStockReal() - stock.getCantidad();

        if(diferencia != 0){
            stock.setCantidad(request.getStockReal());
            stockRepository.save(stock);
        }

        String motivo = request.getMotivo();
        if(motivo == null || motivo.isBlank()){
            motivo = diferencia == 0
                ? "Control manual — sin diferencias"
                : "Ajuste manual de stock";
        }

        MovimientoStock m = MovimientoStock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(diferencia)
            .tipo(TipoMovimiento.AJUSTE)
            .motivo(motivo)
            .idUsuario(idUsuario)
            .build();

        movimientoStockRepository.save(m);
    }

    public List<MovimientoStockResponse> obtenerMovimientos(UUID idProducto){
        return movimientoStockRepository.findByIdProductoAndDeletedAtIsNullOrderByCreatedAtDesc(idProducto)
            .stream()
            .map(this::toMovimientoResponse)
            .toList();
    }

    @Transactional
    public LoteResponse crear(LoteRequest request){
        if(request.getFechaVencimiento() == null) throw new RuntimeException("Fecha de vencimiento obligatoria");

        ProductoResponse producto = productosApi.getById(request.getIdProducto());
        if (!producto.isManejaLotes()) {
            throw new BadRequestException("El producto no maneja lotes");
        }

        Lote lote = toLoteEntity(request);
        Lote guardado = loteRepository.save(lote);

        return toLoteResponse(guardado);
    }

    public List<LoteResponse> getAll(){
        List<Lote> lotes = loteRepository.findAllByDeletedAtIsNull().stream()
            .map(this::actualizarEstado)
            .toList();

        Map<UUID, String> nombresProductos = productosApi.getAll().stream()
            .collect(Collectors.toMap(ProductoResponse::getId, ProductoResponse::getNombre));

        return lotes.stream()
            .map(l -> toLoteResponse(l, nombresProductos))
            .toList();
    }

    public List<LoteResponse> getByEstado(String estado) {
        return getAll().stream()
            .filter(lote -> lote.getEstado().equals(EstadoLote.valueOf(estado.toUpperCase()).name()))
            .toList();
    }

    // Helpers

    private EstadoLote calcularEstado(LocalDate fechaVencimiento){
        LocalDate hoy = LocalDate.now();

        if(fechaVencimiento == null) return EstadoLote.SIN_FECHA;
        if(fechaVencimiento.isBefore(hoy)) return EstadoLote.VENCIDO;
        if(fechaVencimiento.isBefore(hoy.plusDays(7))) return EstadoLote.PROXIMO;

        return EstadoLote.VIGENTE;
    }

    private Lote actualizarEstado(Lote lote){
        EstadoLote actual = lote.getEstado();
        EstadoLote nuevo = calcularEstado(lote.getFechaVencimiento());

        if (actual != nuevo){
            lote.setEstado(nuevo);
            loteRepository.save(lote);
        }

        return lote;
    }

    private Stock toStockEntity(StockRequest request){
        return Stock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(request.getCantidad()).build();
    }

    private StockResponse toStockResponse(Stock stock){
        return StockResponse.builder().idProducto(stock.getIdProducto())
            .cantidad(stock.getCantidad()).build();
    }

    private Lote toLoteEntity(LoteRequest request){
        return Lote.builder()
            .idProducto(request.getIdProducto())
            .numeroLote(request.getNumeroLote())
            .estado(calcularEstado(request.getFechaVencimiento()))
            .fechaVencimiento(request.getFechaVencimiento())
            .cantidad(request.getCantidad())
            .build();
    }

    private LoteResponse toLoteResponse(Lote l){
        return LoteResponse.builder()
            .id(l.getId())
            .idProducto(l.getIdProducto())
            .nombreProducto(productosApi.getById(l.getIdProducto()).getNombre())
            .numeroLote(l.getNumeroLote())
            .fechaVencimiento(l.getFechaVencimiento())
            .cantidad(l.getCantidad())
            .estado(l.getEstado().name())
            .build();
    }

    private LoteResponse toLoteResponse(Lote l, Map<UUID, String> nombresProductos){
        return LoteResponse.builder()
            .id(l.getId())
            .idProducto(l.getIdProducto())
            .nombreProducto(nombresProductos.getOrDefault(l.getIdProducto(), "Producto no encontrado"))
            .numeroLote(l.getNumeroLote())
            .fechaVencimiento(l.getFechaVencimiento())
            .cantidad(l.getCantidad())
            .estado(l.getEstado().name())
            .build();
    }

    private MovimientoStockResponse toMovimientoResponse(MovimientoStock m){
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
