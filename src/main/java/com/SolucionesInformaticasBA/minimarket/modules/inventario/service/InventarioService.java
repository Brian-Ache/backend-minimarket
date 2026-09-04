package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    public List<StockResponse> getByIdProductos(List<UUID> idProductos){
        return stockRepository.findByIdProductoInAndDeletedAtIsNull(idProductos)
            .stream()
            .map(this::toStockResponse)
            .toList();
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

    /**
     * Da de baja la fila de stock del producto, que es la operación inversa de {@link #crear}.
     * Exige que esté en cero: borrarla con unidades hacía desaparecer existencias reales sin
     * dejar movimiento, y era además la forma de saltearse la validación equivalente de la
     * baja de producto.
     */
    @Transactional
    public void delete(UUID idProducto){
        Stock s = buscarStock(idProducto);

        if (s.getCantidad() != 0) {
            throw new BadRequestException("No se puede borrar el stock de un producto con existencias: quedan "
                + s.getCantidad() + " unidades. Ajustá el stock a 0 antes de darlo de baja");
        }

        s.setDeletedAt(LocalDateTime.now());
        stockRepository.save(s);
    }

    @Transactional
    public void controlarStock(UUID idUsuario, AjusteStockRequest request){
        if(!usuarioApi.existById(idUsuario)){
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        ProductoResponse producto = productosApi.getById(request.getIdProducto());

        // La existencia de un producto con lotes es la suma de sus lotes, no la tabla stock:
        // ajustar por acá escribía una fila que después getExistenciasPorProducto ignora, así
        // que el ajuste quedaba registrado y no cambiaba nada de lo que ve el usuario.
        if (producto.isManejaLotes()) {
            throw new BadRequestException(
                "El producto maneja lotes: su existencia se ajusta cargando o descargando lotes");
        }

        if(request.getStockReal() < 0) throw new BadRequestException("El stock real no puede ser negativo");

        // Un producto sin fila de stock es stock 0 y no un error, igual que en getByIdProducto:
        // antes la lectura devolvía 0 y el ajuste sobre ese mismo producto respondía 404.
        Stock stock = stockRepository.findByIdProductoParaActualizar(request.getIdProducto())
            .orElseGet(() -> stockRepository.save(Stock.builder()
                .idProducto(request.getIdProducto())
                .cantidad(0)
                .build()));

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
        return aLoteResponses(loteRepository.findAllByDeletedAtIsNull());
    }

    /**
     * Cada estado es un rango de fechas, así que se resuelve con una consulta y no trayendo
     * todos los lotes —y antes también todo el catálogo— para descartar en memoria. Los
     * límites salen de las mismas constantes que usa {@link EstadoLote#calcularPara}, que
     * sigue siendo la única definición de qué es estar próximo a vencer.
     */
    public List<LoteResponse> getByEstado(String estado) {
        LocalDate hoy = LocalDate.now();
        LocalDate ultimoDiaProximo = hoy.plusDays(EstadoLote.DIAS_PROXIMO_A_VENCER - 1L);

        List<Lote> lotes = switch (parseEstadoLote(estado)) {
            case VENCIDO -> loteRepository.findByFechaVencimientoBeforeAndDeletedAtIsNull(hoy);
            case PROXIMO -> loteRepository.findByFechaVencimientoBetweenAndDeletedAtIsNull(hoy, ultimoDiaProximo);
            case VIGENTE -> loteRepository.findByFechaVencimientoAfterAndDeletedAtIsNull(ultimoDiaProximo);
            case SIN_FECHA -> loteRepository.findByFechaVencimientoIsNullAndDeletedAtIsNull();
        };

        return aLoteResponses(lotes);
    }

    // Helpers

    /**
     * Resuelve los nombres de los productos involucrados en un solo pedido a productos, en vez
     * de traerse el catálogo completo para armar un mapa que casi no se usa.
     */
    private List<LoteResponse> aLoteResponses(List<Lote> lotes) {
        Set<UUID> idsProducto = lotes.stream()
            .map(Lote::getIdProducto)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        Map<UUID, String> nombres = productosApi.getNombresPorId(idsProducto);

        return lotes.stream()
            .map(l -> toLoteResponse(l, nombres))
            .toList();
    }

    /**
     * Solo la usan aumentar, disminuir y controlarStock, que escriben la cantidad, así que
     * toma el lock de la fila. Sin él, dos operaciones simultáneas sobre el mismo producto
     * leían el mismo valor y la segunda pisaba a la primera: se vendía de más y el faltante
     * no quedaba registrado en ningún lado.
     */
    private Stock buscarStock(UUID idProducto){
        return stockRepository.findByIdProductoParaActualizar(idProducto)
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
     * alguien consultara.
     *
     * <p>La columna `lote.estado` se sigue escribiendo al crear el lote, pero <b>no se
     * refresca nunca</b>: es el estado al momento del alta y no sirve para saber si hoy está
     * vencido. Para eso está este cálculo, que es lo que devuelve la API.
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
            .nombreProducto(nombreDeProducto(l.getIdProducto(), nombresProductos))
            .numeroLote(l.getNumeroLote())
            .fechaVencimiento(l.getFechaVencimiento())
            .cantidad(l.getCantidad())
            .estado(calcularEstado(l.getFechaVencimiento()).name())
            .build();
    }

    /**
     * El nombre puede faltar por dos motivos distintos y conviene no confundirlos: que el
     * producto ya no exista, o que exista con la columna en null (la base lo admite).
     */
    private String nombreDeProducto(UUID idProducto, Map<UUID, String> nombres){
        if (!nombres.containsKey(idProducto)) return "Producto no encontrado";
        String nombre = nombres.get(idProducto);
        return nombre != null ? nombre : "Producto sin nombre";
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
