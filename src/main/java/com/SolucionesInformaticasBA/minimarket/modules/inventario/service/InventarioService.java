package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
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
        // Un producto tiene una sola fila de stock activa: con dos, las consultas por
        // producto pasarían a fallar de forma permanente.
        if (stockRepository.findByIdProductoAndDeletedAtIsNull(request.getIdProducto()).isPresent()) {
            throw new BadRequestException("El producto ya tiene stock inicializado");
        }
        Stock guadado = stockRepository.save(toStockEntity(request));

        return toStockResponse(guadado);
    }

    /** Un producto sin fila de stock todavía no tiene movimientos: es stock 0, no un error. */
    public StockResponse getByIdProducto(UUID idProducto){
        return stockRepository.findByIdProductoAndDeletedAtIsNull(idProducto)
            .map(this::toStockResponse)
            .orElseGet(() -> StockResponse.builder().idProducto(idProducto).cantidad(0).build());
    }

    @Transactional
    public StockResponse aumentar(MovimientoStockRequest request){
        validarCantidadPositiva(request.getCantidad());
        Stock stock = buscarStock(request.getIdProducto());

        stock.setCantidad(stock.getCantidad() + request.getCantidad());
        stockRepository.save(stock);

        movimientoStockRepository.save(MovimientoStock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(request.getCantidad())
            .tipo(parseTipo(request.getTipo()))
            .motivo(request.getMotivo())
            .idUsuario(request.getIdUsuario())
            .idReferencia(request.getIdReferencia())
            .build());

        return toStockResponse(stock);
    }

    @Transactional
    public StockResponse disminuir(MovimientoStockRequest request){
        validarCantidadPositiva(request.getCantidad());
        Stock stock = buscarStock(request.getIdProducto());

        int nuevoStock = stock.getCantidad() - request.getCantidad();
        if (nuevoStock < 0) {
            throw new BadRequestException("Stock insuficiente. Disponible: " + stock.getCantidad() + ", solicitado: " + request.getCantidad());
        }

        stock.setCantidad(nuevoStock);
        stockRepository.save(stock);

        movimientoStockRepository.save(MovimientoStock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(-request.getCantidad())
            .tipo(parseTipo(request.getTipo()))
            .motivo(request.getMotivo())
            .idUsuario(request.getIdUsuario())
            .idReferencia(request.getIdReferencia())
            .build());

        return toStockResponse(stock);
    }

    @Transactional
    public void delete(UUID idProducto){
        Stock s = buscarStock(idProducto);
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
        if(request.getStockReal() < 0) throw new BadRequestException("El stock real no puede ser negativo");

        Stock stock = buscarStock(request.getIdProducto());

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

    @Override
    public Map<UUID, Integer> getExistenciasPorProducto(){
        Map<UUID, Integer> existencias = new HashMap<>();

        for (Object[] fila : stockRepository.cantidadesPorProducto()) {
            if (fila[0] != null) {
                existencias.put((UUID) fila[0], ((Number) fila[1]).intValue());
            }
        }
        // Los productos con lotes no usan la tabla stock: su existencia es la suma de lotes.
        for (Object[] fila : loteRepository.sumCantidadAgrupadaPorProducto()) {
            if (fila[0] != null) {
                existencias.put((UUID) fila[0], ((Number) fila[1]).intValue());
            }
        }
        return existencias;
    }

    public List<MovimientoStockResponse> obtenerMovimientos(UUID idProducto){
        return movimientoStockRepository.findByIdProductoAndDeletedAtIsNullOrderByCreatedAtDesc(idProducto)
            .stream()
            .map(this::toMovimientoResponse)
            .toList();
    }

    @Transactional
    public LoteResponse crear(LoteRequest request){
        if(request.getFechaVencimiento() == null) throw new BadRequestException("Fecha de vencimiento obligatoria");
        validarCantidadPositiva(request.getCantidad());

        ProductoResponse producto = productosApi.getById(request.getIdProducto());
        if (!producto.isManejaLotes()) {
            throw new BadRequestException("El producto no maneja lotes");
        }

        Lote lote = toLoteEntity(request);
        Lote guardado = loteRepository.save(lote);

        return toLoteResponse(guardado);
    }

    public List<LoteResponse> getAll(){
        Map<UUID, String> nombresProductos = productosApi.getAll().stream()
            .collect(Collectors.toMap(ProductoResponse::getId, ProductoResponse::getNombre));

        return loteRepository.findAllByDeletedAtIsNull().stream()
            .map(l -> toLoteResponse(l, nombresProductos))
            .toList();
    }

    public List<LoteResponse> getByEstado(String estado) {
        String buscado = parseEstadoLote(estado).name();
        return getAll().stream()
            .filter(lote -> buscado.equals(lote.getEstado()))
            .toList();
    }

    // Helpers

    private Stock buscarStock(UUID idProducto){
        return stockRepository.findByIdProductoAndDeletedAtIsNull(idProducto)
            .orElseThrow(() -> new ResourceNotFoundException("Stock no encontrado para el producto"));
    }

    private void validarCantidadPositiva(int cantidad){
        if (cantidad <= 0) {
            throw new BadRequestException("La cantidad debe ser mayor a 0");
        }
    }

    private TipoMovimiento parseTipo(String tipo){
        try {
            return TipoMovimiento.valueOf(tipo);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Tipo de movimiento inválido: " + tipo);
        }
    }

    private EstadoLote parseEstadoLote(String estado){
        try {
            return EstadoLote.valueOf(estado.toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Estado de lote inválido: " + estado);
        }
    }

    /**
     * El estado es una función de la fecha de vencimiento y del día de hoy, así que se calcula
     * al leer. Antes se persistía y se "refrescaba" desde el GET de lotes: un endpoint de
     * lectura que escribía en la base, y que además dejaba el dato desactualizado hasta que
     * alguien consultara. La columna `lote.estado` se sigue guardando al crear el lote, para
     * que las consultas SQL directas tengan un valor razonable.
     */
    private EstadoLote calcularEstado(LocalDate fechaVencimiento){
        return EstadoLote.calcularPara(fechaVencimiento);
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
            .estado(calcularEstado(l.getFechaVencimiento()).name())
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
            .estado(calcularEstado(l.getFechaVencimiento()).name())
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
