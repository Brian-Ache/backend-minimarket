package com.SolucionesInformaticasBA.minimarket.modules.productos.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.dto.CategoriaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.MovimientoStock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.TipoMovimiento;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.ProductoProveedor;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoProveedorRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.repository.ProductoRepository;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.dto.ProveedorResponse;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ResourceNotFoundException;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class ProductoService implements ProductosApi{
    /** Motivo del movimiento que deja el alta cuando el producto nace con existencias. */
    private static final String MOTIVO_CARGA_INICIAL = "Carga inicial de stock";

    private final ProductoRepository productoRepository;
    private final ProductoProveedorRepository productoProveedorRepository;
    // Repositorios de inventario y no InventarioApi: el servicio de inventario ya depende
    // de ProductosApi, así que inyectar su API acá cerraría un ciclo de beans.
    private final StockRepository stockRepository;
    private final LoteRepository loteRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final UsuarioApi usuarioApi;
    private final CategoriasApi categoriasApi;
    private final ProveedoresApi proveedoresApi;

    @Transactional
    public ProductoResponse crear(UUID idUsuario, ProductoRequest request){
        // El id sale del JWT, así que llegar acá sin usuario significa que la cuenta se dio
        // de baja con el token todavía vivo. Es un 404 del mismo tipo que usan compras e
        // inventario, no el 500 que salía cuando esto era un RuntimeException pelado.
        if(!usuarioApi.existById(idUsuario)) {
            throw new ResourceNotFoundException("Usuario no encontrado");
        }

        if(productoRepository.findByBarcodeAndDeletedAtIsNull(request.getBarcode()) != null){
            throw new BadRequestException("Ya existe un producto con ese barcode");
        }

        validarCategoriaYProveedor(request.getIdCategoria(), request.getIdProveedor());
        validarCargaInicial(request);

        Producto producto = toEntity(request);
        Producto guardado = productoRepository.save(producto);

        // Producto y existencias en la misma transacción: antes había que completar el alta con
        // una segunda llamada, y si esa fallaba quedaba un producto en cero sin que nadie
        // avisara. Cada fuente por su lado, porque las existencias de un producto son la tabla
        // stock o la suma de sus lotes, nunca las dos.
        if (guardado.isManejaLotes()) {
            crearLoteInicial(idUsuario, guardado, request);
        } else {
            crearStockInicial(idUsuario, guardado, request.getCantidadInicial());
        }

        return toResponse(guardado);
    }

     public boolean existsById(UUID id){
        return productoRepository.existsByIdAndDeletedAtIsNull(id);
     }

    public ProductoResponse getById(UUID id){
        Producto producto = productoRepository.findByIdAndDeletedAtIsNull(id);
        if (producto == null) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        return toResponse(producto);
    }

    @Override
    public Map<UUID, String> getNombresPorId(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        // HashMap y no Collectors.toMap: toMap revienta con NullPointerException si algún
        // nombre es null, y la columna lo admite. Un producto sin nombre no puede tirar abajo
        // el listado de otro módulo.
        Map<UUID, String> nombres = new HashMap<>();
        for (Producto p : productoRepository.findAllById(ids)) {
            nombres.put(p.getId(), p.getNombre());
        }
        return nombres;
    }

    @Override
    public Map<UUID, String> getBarcodesPorId(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        // Mismo criterio que getNombresPorId: HashMap para tolerar el barcode nulo, que la
        // columna admite, e incluyendo bajas para que un reporte histórico siga mostrando el
        // código del producto aunque hoy esté dado de baja.
        Map<UUID, String> barcodes = new HashMap<>();
        for (Producto p : productoRepository.findAllById(ids)) {
            barcodes.put(p.getId(), p.getBarcode());
        }
        return barcodes;
    }

    public Page<ProductoResponse> getAll(Pageable pageable){
        return toResponsePage(productoRepository.findAllPaginated(pageable));
    }

    public ProductoResponse getByBarcode(String barcode){
        Producto producto = productoRepository.findByBarcodeAndDeletedAtIsNull(barcode);
        if(producto == null){
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        return toResponse(producto);
    }

    @Transactional
    public ProductoResponse update(UUID idProducto, ProductoRequest request){
        // Con findById a secas un producto ya borrado seguía siendo editable: respondía 200
        // y hasta permitía moverle el barcode, pisando el de un producto activo.
        Producto producto = productoRepository.findByIdAndDeletedAtIsNull(idProducto);
        if (producto == null) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }

        // request.getBarcode() es @NotBlank; el de la entidad puede ser NULL en filas viejas.
        // Comparar en este orden evita el NullPointerException.
        if(!request.getBarcode().equals(producto.getBarcode())
                && productoRepository.findByBarcodeAndDeletedAtIsNull(request.getBarcode()) != null){
            throw new BadRequestException("Ya existe un producto con ese barcode");
        }

        validarCategoriaYProveedor(request.getIdCategoria(), request.getIdProveedor());

        // Cambiar manejaLotes cambia de dónde salen las existencias del producto: la tabla
        // stock para los comunes, la suma de lotes para los que manejan lotes. Con unidades
        // cargadas, el cambio las hacía desaparecer de toda la aplicación —siguen en la base,
        // pero getExistenciasPorProducto pasa a mirar la otra fuente y devuelve 0— sin dejar
        // movimiento ni error. Mismo orden de bloqueo que delete: primero el stock, después
        // los lotes por la consulta del FEFO.
        if (request.isManejaLotes() != producto.isManejaLotes()) {
            validarSinExistencias(
                stockRepository.findByIdProductoParaActualizar(idProducto).orElse(null),
                loteRepository.findParaDescuentoFefo(idProducto),
                "cambiar el manejo de lotes de un producto con existencias");
        }

        // PUT: el request es la representación completa del producto, así que los campos
        // opcionales se asignan siempre, también cuando vienen nulos. Salteando los nulos
        // no había forma de desasignar la categoría, el proveedor, el costo ni el margen:
        // el pedido se aceptaba con 200 y el valor viejo quedaba intacto.
        producto.setNombre(request.getNombre());
        producto.setBarcode(request.getBarcode());
        producto.setPrecio(request.getPrecio());
        producto.setManejaLotes(request.isManejaLotes());
        producto.setCosto(request.getCosto());
        producto.setMargen(request.getMargen());
        producto.setIdCategoria(request.getIdCategoria());
        producto.setIdProveedor(request.getIdProveedor());

        Producto actualizado = productoRepository.save(producto);
        return toResponse(actualizado);
    }

    @Transactional
    public void delete(UUID id){
        Producto p = productoRepository.findByIdAndDeletedAtIsNull(id);
        if(p == null) throw new ResourceNotFoundException("Producto no encontrado");

        // Con lock: acá se lee para decidir si el producto se puede borrar y después se
        // escriben esas mismas filas. Sin bloquearlas, una venta o una compra que entre en el
        // medio deja stock o lotes vivos colgando de un producto ya dado de baja. Primero el
        // stock y después los lotes, y los lotes por la misma consulta que usa el FEFO: el
        // orden de bloqueo tiene que ser uno solo en todo el sistema para que no haya ciclo.
        Stock stock = stockRepository.findByIdProductoParaActualizar(id).orElse(null);
        List<Lote> lotes = loteRepository.findParaDescuentoFefo(id);

        validarSinExistencias(stock, lotes, "dar de baja un producto con existencias");

        LocalDateTime ahora = LocalDateTime.now();
        p.setDeletedAt(ahora);
        productoRepository.save(p);

        // La fila de stock la da de alta `crear`, así que la baja también corre por acá.
        // Si quedaba activa, el producto borrado seguía apareciendo en los reportes de
        // inventario, que leen la tabla stock sin pasar por el catálogo.
        if (stock != null) {
            stock.setDeletedAt(ahora);
            stockRepository.save(stock);
        }

        // Mismo problema del lado de los lotes: sumCantidadAgrupadaPorProducto y el listado
        // de vencimientos los recorren por su cuenta, así que sobrevivían a la baja del
        // producto. Acá ya sabemos que están todos en cero.
        lotes.forEach(lote -> lote.setDeletedAt(ahora));
        loteRepository.saveAll(lotes);

        // Y las referencias de precio, por el mismo motivo que el stock y los lotes: colgaban
        // de un producto que ya no existe, y el índice único las seguía contando, así que un
        // alta nueva del mismo par producto-proveedor chocaba contra una fila fantasma.
        List<ProductoProveedor> referencias =
            productoProveedorRepository.findByIdProductoAndDeletedAtIsNull(id);
        referencias.forEach(r -> r.setDeletedAt(ahora));
        productoProveedorRepository.saveAll(referencias);
    }

    /**
     * Alta o corrección del precio que un proveedor lista por el producto. Es un upsert: el
     * front no tiene por qué saber de antemano si la referencia ya existía, y el valor se
     * ajusta a mano siempre.
     */
    @Override
    @Transactional
    public PrecioReferenciaResponse guardarPrecioReferencia(UUID idProducto, UUID idProveedor,
            BigDecimal precioReferencia) {
        if (!existsById(idProducto)) {
            throw new ResourceNotFoundException("Producto no encontrado");
        }
        // En pie: una referencia nueva a un proveedor dado de baja es cargar un precio de
        // alguien con quien no se puede operar. Las que ya existían sí sobreviven a su baja.
        if (!proveedoresApi.existsById(idProveedor)) {
            throw new BadRequestException("El proveedor especificado no existe");
        }

        ProductoProveedor referencia = productoProveedorRepository
            .findByIdProductoAndIdProveedorAndDeletedAtIsNull(idProducto, idProveedor)
            .orElseGet(() -> ProductoProveedor.builder()
                .idProducto(idProducto)
                .idProveedor(idProveedor)
                .build());

        referencia.setPrecioReferencia(precioReferencia);

        return toPrecioReferenciaResponse(productoProveedorRepository.save(referencia));
    }

    @Override
    @Transactional
    public void borrarPrecioReferencia(UUID idProducto, UUID idProveedor) {
        ProductoProveedor referencia = productoProveedorRepository
            .findByIdProductoAndIdProveedorAndDeletedAtIsNull(idProducto, idProveedor)
            .orElseThrow(() -> new ResourceNotFoundException(
                "El producto no tiene un precio de referencia de ese proveedor"));

        referencia.setDeletedAt(LocalDateTime.now());
        productoProveedorRepository.save(referencia);
    }

    @Override
    public List<PrecioReferenciaResponse> getProveedoresDeProducto(UUID idProducto) {
        return productoProveedorRepository
            .findByIdProductoAndDeletedAtIsNullOrderByPrecioReferenciaAsc(idProducto)
            .stream()
            .map(this::toPrecioReferenciaResponse)
            .toList();
    }

    @Override
    public Page<ProductoResponse> search(String q, Pageable pageable) {
        return toResponsePage(productoRepository.findByNombreContainingIgnoreCase(q, pageable));
    }

    @Override
    public Page<ProductoResponse> searchByNombreAndCategoria(String q, UUID idCategoria, Pageable pageable) {
        return toResponsePage(productoRepository.searchByNombreAndCategoria(q, idCategoria, pageable));
    }

    @Override
    public Page<ProductoResponse> searchByNombreAndProveedor(String q, UUID idProveedor, Pageable pageable) {
        return toResponsePage(productoRepository.searchByNombreAndProveedor(q, idProveedor, pageable));
    }

    @Override
    public Page<ProductoResponse> searchByNombreAndCategoriaAndProveedor(String q, UUID idCategoria, UUID idProveedor, Pageable pageable) {
        return toResponsePage(
            productoRepository.searchByNombreAndCategoriaAndProveedor(q, idCategoria, idProveedor, pageable));
    }

    @Override
    public Page<ProductoResponse> getByCategoria(UUID idCategoria, Pageable pageable) {
        return toResponsePage(productoRepository.findByIdCategoriaAndDeletedAtIsNull(idCategoria, pageable));
    }

    @Override
    public Page<ProductoResponse> getByProveedor(UUID idProveedor, Pageable pageable) {
        return toResponsePage(productoRepository.findByIdProveedorAndDeletedAtIsNull(idProveedor, pageable));
    }

    @Override
    public Page<ProductoResponse> getByCategoriaAndProveedor(UUID idCategoria, UUID idProveedor, Pageable pageable) {
        return toResponsePage(
            productoRepository.findByIdCategoriaAndIdProveedorAndDeletedAtIsNull(idCategoria, idProveedor, pageable));
    }

    // Helpers

    /**
     * Un producto con existencias no se da de baja ni cambia de forma de llevarlas en
     * silencio: las dos cosas se llevarían puestas unidades que siguen en la góndola y que
     * después ningún reporte vuelve a mostrar. Primero hay que descargarlas, con una venta o
     * con un ajuste de stock.
     *
     * @param accion qué se estaba intentando, en infinitivo y con su complemento, para que el
     *        mensaje diga qué se rechazó y no solo que había existencias.
     */
    private void validarSinExistencias(Stock stock, List<Lote> lotes, String accion) {
        if (stock != null && stock.getCantidad() > 0) {
            throw new BadRequestException("No se puede " + accion + ": quedan "
                + stock.getCantidad() + " unidades en stock. Ajustá el stock a 0 primero");
        }

        int unidadesEnLotes = lotes.stream().mapToInt(Lote::getCantidad).sum();
        if (unidadesEnLotes > 0) {
            long lotesConUnidades = lotes.stream().filter(lote -> lote.getCantidad() > 0).count();
            throw new BadRequestException("No se puede " + accion + ": quedan "
                + unidadesEnLotes + " unidades en " + lotesConUnidades
                + (lotesConUnidades == 1 ? " lote activo" : " lotes activos")
                + ". Descargá los lotes primero");
        }
    }

    /**
     * Las combinaciones de {@code manejaLotes}, {@code cantidadInicial} y {@code loteInicial}
     * que no describen un alta posible. Se rechazan acá y no más adelante para que el pedido
     * no llegue a escribir el producto y después falle a mitad de camino.
     */
    private void validarCargaInicial(ProductoRequest request) {
        if (!request.isManejaLotes() && request.getLoteInicial() != null) {
            throw new BadRequestException(
                "El producto no maneja lotes: la carga inicial no lleva loteInicial");
        }
        if (request.isManejaLotes() && request.getCantidadInicial() == 0
                && request.getLoteInicial() != null) {
            throw new BadRequestException(
                "Un lote sin unidades no se crea: cargá cantidadInicial o sacá loteInicial");
        }
        if (request.isManejaLotes() && request.getCantidadInicial() > 0
                && request.getLoteInicial() == null) {
            throw new BadRequestException(
                "El producto maneja lotes: la cantidad inicial tiene que venir con su loteInicial");
        }
    }

    /**
     * La fila de stock se crea siempre, también en cero: es la que sostiene el índice único
     * por producto y la que después leen el ajuste y los reportes.
     */
    private void crearStockInicial(UUID idUsuario, Producto producto, int cantidadInicial) {
        stockRepository.save(Stock.builder()
            .idProducto(producto.getId())
            .cantidad(cantidadInicial)
            .build());

        if (cantidadInicial > 0) {
            registrarCargaInicial(idUsuario, producto.getId(), null, cantidadInicial);
        }
    }

    /**
     * Un producto con lotes <b>no</b> lleva fila de stock: sus existencias son la suma de sus
     * lotes y esa fila no la lee nadie. El alta la creaba igual, en cero, y quedaba ocupando
     * el índice único sin representar nada.
     */
    private void crearLoteInicial(UUID idUsuario, Producto producto, ProductoRequest request) {
        if (request.getCantidadInicial() == 0) return;

        LoteInicialRequest loteInicial = request.getLoteInicial();

        // El estado se escribe al crear y no se refresca nunca, igual que en el alta de lotes
        // de inventario: lo que la API devuelve se calcula al leer, contra el día de hoy.
        Lote lote = loteRepository.save(Lote.builder()
            .idProducto(producto.getId())
            .numeroLote(loteInicial.getNumeroLote())
            .fechaVencimiento(loteInicial.getFechaVencimiento())
            .estado(EstadoLote.calcularPara(loteInicial.getFechaVencimiento()))
            .cantidad(request.getCantidadInicial())
            .build());

        registrarCargaInicial(idUsuario, producto.getId(), lote.getId(), request.getCantidadInicial());
    }

    /**
     * Deja la carga inicial en el kardex. Tipo {@code AJUSTE} y no un valor propio:
     * {@code movimientos_stock.tipo} es un ENUM de MySQL, sumarle un valor obliga a migrar la
     * tabla y a revisar todo lo que filtra por tipo, y el motivo ya distingue el caso.
     */
    private void registrarCargaInicial(UUID idUsuario, UUID idProducto, UUID idLote, int cantidad) {
        movimientoStockRepository.save(MovimientoStock.builder()
            .idProducto(idProducto)
            .idLote(idLote)
            .cantidad(cantidad)
            .tipo(TipoMovimiento.AJUSTE)
            .motivo(MOTIVO_CARGA_INICIAL)
            .idUsuario(idUsuario)
            .build());
    }

    private void validarCategoriaYProveedor(UUID idCategoria, UUID idProveedor) {
        if (idCategoria != null && !categoriasApi.existsById(idCategoria)) {
            throw new BadRequestException("La categoría especificada no existe");
        }
        if (idProveedor != null && !proveedoresApi.existsById(idProveedor)) {
            throw new BadRequestException("El proveedor especificado no existe");
        }
    }

    /**
     * Resuelve el proveedor incluyendo las bajas: una baja de proveedor no borra los precios
     * que se le conocían —es reversible, y perderlos en cada una sería destructivo—, así que
     * la referencia se sigue mostrando con el {@code deletedAt} cargado para que el front lo
     * distinga. Que no se pueda cargar una referencia nueva a alguien de baja ya lo cubre
     * {@link #guardarPrecioReferencia}.
     */
    private PrecioReferenciaResponse toPrecioReferenciaResponse(ProductoProveedor referencia) {
        ProveedorResponse proveedor;
        try {
            proveedor = proveedoresApi.getByIdIncluyendoBajas(referencia.getIdProveedor());
        } catch (ResourceNotFoundException e) {
            proveedor = null;
        }

        return PrecioReferenciaResponse.builder()
            .proveedor(proveedor)
            .precioReferencia(referencia.getPrecioReferencia())
            .actualizado(referencia.getUpdatedAt())
            .build();
    }

    private Producto toEntity(ProductoRequest request){
        return Producto.builder()
            .nombre(request.getNombre())
            .barcode(request.getBarcode())
            .precio(request.getPrecio())
            .manejaLotes(request.isManejaLotes())
            .costo(request.getCosto())
            .margen(request.getMargen())
            .idCategoria(request.getIdCategoria())
            .idProveedor(request.getIdProveedor())
            .build();
    }

    /**
     * Arma la página resolviendo cada categoría y cada proveedor una sola vez por id
     * distinto. Fila por fila eran dos consultas extra por producto: una página de 20
     * salían 41, y el reporte de inventario —que pide el catálogo entero— más de 200,
     * anulando el trabajo de las consultas agregadas de existencias.
     */
    private Page<ProductoResponse> toResponsePage(Page<Producto> productos) {
        Map<UUID, CategoriaResponse> categorias = new HashMap<>();
        Map<UUID, ProveedorResponse> proveedores = new HashMap<>();
        return productos.map(p -> toResponse(p, categorias, proveedores));
    }

    private ProductoResponse toResponse(Producto p) {
        return toResponse(p, new HashMap<>(), new HashMap<>());
    }

    private ProductoResponse toResponse(Producto p,
            Map<UUID, CategoriaResponse> cacheCategorias,
            Map<UUID, ProveedorResponse> cacheProveedores) {
        CategoriaResponse categoria = null;
        if (p.getIdCategoria() != null) {
            // containsKey y no computeIfAbsent: una categoría borrada resuelve a null, y
            // computeIfAbsent no guarda los nulos, con lo que se repreguntaría por cada fila.
            if (cacheCategorias.containsKey(p.getIdCategoria())) {
                categoria = cacheCategorias.get(p.getIdCategoria());
            } else {
                try {
                    categoria = categoriasApi.getById(p.getIdCategoria());
                } catch (ResourceNotFoundException e) {
                    categoria = null;
                }
                cacheCategorias.put(p.getIdCategoria(), categoria);
            }
        }

        ProveedorResponse proveedor = null;
        if (p.getIdProveedor() != null) {
            if (cacheProveedores.containsKey(p.getIdProveedor())) {
                proveedor = cacheProveedores.get(p.getIdProveedor());
            } else {
                try {
                    // Incluyendo bajas: el producto sigue mostrando su proveedor aunque esté
                    // dado de baja, con deletedAt cargado para que el front lo distinga. Que
                    // no se pueda asignar uno de baja ya lo cubre validarCategoriaYProveedor.
                    proveedor = proveedoresApi.getByIdIncluyendoBajas(p.getIdProveedor());
                } catch (ResourceNotFoundException e) {
                    proveedor = null;
                }
                cacheProveedores.put(p.getIdProveedor(), proveedor);
            }
        }

        return ProductoResponse.builder()
            .id(p.getId())
            .nombre(p.getNombre())
            .barcode(p.getBarcode())
            .precio(p.getPrecio())
            .manejaLotes(p.isManejaLotes())
            .costo(p.getCosto())
            .margen(p.getMargen())
            .categoria(categoria)
            .proveedor(proveedor)
            .build();
    }
}
