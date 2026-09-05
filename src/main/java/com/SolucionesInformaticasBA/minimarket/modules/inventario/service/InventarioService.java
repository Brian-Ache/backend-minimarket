package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
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
    /** Motivo de un control que se hizo y no encontró nada que corregir. */
    private static final String MOTIVO_SIN_DIFERENCIAS = "Control manual — sin diferencias";

    private final StockRepository stockRepository;
    private final LoteRepository loteRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final ProductosApi productosApi;
    private final UsuarioApi usuarioApi;

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
                "El producto maneja lotes: su existencia se ajusta por lote, con POST /api/inventario/v1/lotes/ajustar");
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

        MovimientoStock m = MovimientoStock.builder()
            .idProducto(request.getIdProducto())
            .cantidad(diferencia)
            .tipo(TipoMovimiento.AJUSTE)
            .motivo(motivoOPorDefecto(request.getMotivo(), diferencia == 0
                ? MOTIVO_SIN_DIFERENCIAS
                : "Ajuste manual de stock"))
            .idUsuario(idUsuario)
            .build();

        movimientoStockRepository.save(m);
    }

    /**
     * Ajuste de un producto con lotes contra el conteo físico, que es lo que
     * {@link #controlarStock} no puede hacer: para estos productos la existencia es la suma de
     * los lotes, así que corregir el total sin decir de qué lote sale no significa nada.
     *
     * <p>Hace falta porque el FEFO reparte por vencimiento, pero en la góndola el cliente
     * agarra cualquier envase: con el tiempo el total puede seguir siendo correcto y el
     * reparto por lote no serlo.
     *
     * <p>Es <b>parcial</b>: los lotes que el request no menciona quedan como estaban.
     */
    @Transactional
    public List<LoteResponse> ajustarLotes(UUID idUsuario, AjusteLotesRequest request){
        if(!usuarioApi.existById(idUsuario)){
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        ProductoResponse producto = productosApi.getById(request.getIdProducto());

        // Espejo exacto de la validación de controlarStock, del otro lado del mostrador.
        if (!producto.isManejaLotes()) {
            throw new BadRequestException(
                "El producto no maneja lotes: su existencia se ajusta con POST /api/inventario/v1/controlar");
        }

        // Todos los lotes del producto de una vez y por la única puerta de bloqueo que hay.
        // Bloquearlos uno por uno en el orden en que los mandó el cliente los tomaría en un
        // orden distinto al del resto del sistema, que es justo lo que abre un ciclo con las
        // ventas y las anulaciones.
        List<Lote> lotes = loteRepository.findParaDescuentoFefo(request.getIdProducto());
        Map<UUID, Lote> porId = new HashMap<>();
        for (Lote lote : lotes) {
            porId.put(lote.getId(), lote);
        }

        // Dos pasadas: primero se valida todo el conteo y recién después se escribe. Un
        // request con un lote ajeno en la última línea no deja las anteriores aplicadas.
        Set<UUID> contados = new HashSet<>();
        for (ConteoLoteRequest conteo : request.getConteos()) {
            if (!contados.add(conteo.getIdLote())) {
                throw new BadRequestException(
                    "El lote " + conteo.getIdLote() + " viene contado más de una vez");
            }
            if (!porId.containsKey(conteo.getIdLote())) {
                throw new BadRequestException("El lote " + conteo.getIdLote()
                    + " no existe, está dado de baja o no es de este producto");
            }
        }

        List<MovimientoStock> movimientos = new ArrayList<>();
        for (ConteoLoteRequest conteo : request.getConteos()) {
            Lote lote = porId.get(conteo.getIdLote());
            int diferencia = conteo.getCantidadReal() - lote.getCantidad();
            if (diferencia == 0) continue;

            lote.setCantidad(conteo.getCantidadReal());
            loteRepository.save(lote);

            movimientos.add(MovimientoStock.builder()
                .idProducto(request.getIdProducto())
                .idLote(lote.getId())
                .cantidad(diferencia)
                .tipo(TipoMovimiento.AJUSTE)
                .motivo(motivoOPorDefecto(request.getMotivo(), "Ajuste manual de stock por lote"))
                .idUsuario(idUsuario)
                .build());
        }

        // Un conteo que no encontró diferencias también queda registrado, igual que en
        // controlarStock: lo que prueba el movimiento es que alguien contó, no solo que hubo
        // que corregir. Sin idLote, porque no es de ninguno en particular.
        if (movimientos.isEmpty()) {
            movimientos.add(MovimientoStock.builder()
                .idProducto(request.getIdProducto())
                .cantidad(0)
                .tipo(TipoMovimiento.AJUSTE)
                .motivo(motivoOPorDefecto(request.getMotivo(), MOTIVO_SIN_DIFERENCIAS))
                .idUsuario(idUsuario)
                .build());
        }
        movimientoStockRepository.saveAll(movimientos);

        return aLoteResponsesDe(producto, lotes);
    }

    /**
     * Los lotes que tiene sentido ofrecer en la pantalla de ajuste. Es un filtro <b>de la
     * lista</b> y no del ajuste: {@link #ajustarLotes} acepta cualquier lote activo del
     * producto, porque si no un error de carga sobre un lote ya vencido no tendría forma de
     * corregirse.
     */
    public List<LoteResponse> getLotesAjustables(UUID idProducto){
        ProductoResponse producto = productosApi.getById(idProducto);

        if (!producto.isManejaLotes()) {
            throw new BadRequestException("El producto no maneja lotes");
        }

        return aLoteResponsesDe(producto, loteRepository.findAjustables(
            idProducto,
            LocalDate.now(),
            LocalDateTime.now().minusDays(EstadoLote.DIAS_LOTE_AGOTADO)));
    }

    @Override
    public Map<UUID, Integer> getExistenciasPorProducto(){
        return existencias(
            stockRepository.cantidadesPorProducto(),
            loteRepository.sumCantidadAgrupadaPorProducto());
    }

    @Override
    public Map<UUID, Integer> getExistenciasPorProductos(Collection<UUID> idProductos){
        if (idProductos == null || idProductos.isEmpty()) {
            return Map.of();
        }
        return existencias(
            stockRepository.cantidadesDeProductos(idProductos),
            loteRepository.sumCantidadAgrupadaDeProductos(idProductos));
    }

    /**
     * Arma el mapa a partir de las dos consultas agregadas. Los lotes van segundos y pisan:
     * un producto con lotes puede tener además una fila de stock vieja —el alta la creaba— y
     * su existencia es la suma de los lotes, nunca esa fila.
     */
    private Map<UUID, Integer> existencias(List<Object[]> porStock, List<Object[]> porLote){
        Map<UUID, Integer> existencias = new HashMap<>();

        for (Object[] fila : porStock) {
            if (fila[0] != null) {
                existencias.put((UUID) fila[0], ((Number) fila[1]).intValue());
            }
        }
        for (Object[] fila : porLote) {
            if (fila[0] != null) {
                existencias.put((UUID) fila[0], ((Number) fila[1]).intValue());
            }
        }
        return existencias;
    }

    /**
     * Paginado: es la única tabla del módulo que crece con cada venta y cada compra, así que un
     * listado completo se traía años de historia en un solo pedido.
     */
    public Page<MovimientoStockResponse> obtenerMovimientos(UUID idProducto, Pageable pageable){
        return movimientoStockRepository.findByIdProductoAndDeletedAtIsNull(idProducto, pageable)
            .map(this::toMovimientoResponse);
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

        // Con el producto que ya trajo la validación: el overload de una sola fila lo volvía
        // a pedir, y esta alta la llama una compra por cada línea con lotes. singletonMap y no
        // Map.of porque el nombre puede ser null y Map.of no admite valores nulos.
        return toLoteResponse(guardado,
            Collections.singletonMap(guardado.getIdProducto(), producto.getNombre()));
    }

    /**
     * Paginado: la tabla suma un lote por cada línea de compra de un producto con vencimiento,
     * así que el listado completo crecía sin techo y traía años de lotes ya agotados.
     */
    public Page<LoteResponse> getAll(Pageable pageable){
        return aLoteResponses(loteRepository.findAllByDeletedAtIsNull(pageable));
    }

    /**
     * Cada estado es un rango de fechas, así que se resuelve con una consulta y no trayendo
     * todos los lotes —y antes también todo el catálogo— para descartar en memoria. Los
     * límites salen de las mismas constantes que usa {@link EstadoLote#calcularPara}, que
     * sigue siendo la única definición de qué es estar próximo a vencer.
     */
    public Page<LoteResponse> getByEstado(String estado, Pageable pageable) {
        LocalDate hoy = LocalDate.now();
        LocalDate ultimoDiaProximo = hoy.plusDays(EstadoLote.DIAS_PROXIMO_A_VENCER - 1L);

        Page<Lote> lotes = switch (parseEstadoLote(estado)) {
            case VENCIDO -> loteRepository.findByFechaVencimientoBeforeAndDeletedAtIsNull(hoy, pageable);
            case PROXIMO -> loteRepository.findByFechaVencimientoBetweenAndDeletedAtIsNull(hoy, ultimoDiaProximo, pageable);
            case VIGENTE -> loteRepository.findByFechaVencimientoAfterAndDeletedAtIsNull(ultimoDiaProximo, pageable);
            case SIN_FECHA -> loteRepository.findByFechaVencimientoIsNullAndDeletedAtIsNull(pageable);
        };

        return aLoteResponses(lotes);
    }

    // Helpers

    /**
     * Resuelve los nombres de los productos involucrados en un solo pedido a productos, en vez
     * de traerse el catálogo completo para armar un mapa que casi no se usa.
     */
    /**
     * Variante para los listados de un solo producto, que ya lo tienen resuelto: arma el mapa
     * de nombres con lo que ya está en memoria en vez de repreguntárselo a productos. Mismo
     * criterio que el alta de lote, y singletonMap y no Map.of porque el nombre puede ser null.
     */
    private List<LoteResponse> aLoteResponsesDe(ProductoResponse producto, List<Lote> lotes) {
        Map<UUID, String> nombre = Collections.singletonMap(producto.getId(), producto.getNombre());
        return lotes.stream()
            .map(l -> toLoteResponse(l, nombre))
            .toList();
    }

    /** El motivo que cargó el usuario, o el que describe la operación si no cargó ninguno. */
    private String motivoOPorDefecto(String motivo, String porDefecto) {
        return (motivo != null && !motivo.isBlank()) ? motivo : porDefecto;
    }

    private List<LoteResponse> aLoteResponses(List<Lote> lotes) {
        Map<UUID, String> nombres = nombresDeLosProductosDe(lotes);

        return lotes.stream()
            .map(l -> toLoteResponse(l, nombres))
            .toList();
    }

    /**
     * Igual para una página: el mapa se arma con el contenido de la página y recién después se
     * mapea, así que sigue siendo un solo pedido a productos y no uno por fila.
     */
    private Page<LoteResponse> aLoteResponses(Page<Lote> lotes) {
        Map<UUID, String> nombres = nombresDeLosProductosDe(lotes.getContent());

        return lotes.map(l -> toLoteResponse(l, nombres));
    }

    private Map<UUID, String> nombresDeLosProductosDe(List<Lote> lotes) {
        Set<UUID> idsProducto = lotes.stream()
            .map(Lote::getIdProducto)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        return productosApi.getNombresPorId(idsProducto);
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
            .idLote(m.getIdLote())
            .idReferencia(m.getIdReferencia())
            .idUsuario(m.getIdUsuario())
            .build();
    }
}
