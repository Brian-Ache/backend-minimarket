package com.SolucionesInformaticasBA.minimarket.modules.productos.service;

import java.time.LocalDateTime;
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
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Stock;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.dto.*;
import com.SolucionesInformaticasBA.minimarket.modules.productos.entity.Producto;
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
    private final ProductoRepository productoRepository;
    // Repositorios de inventario y no InventarioApi: el servicio de inventario ya depende
    // de ProductosApi, así que inyectar su API acá cerraría un ciclo de beans.
    private final StockRepository stockRepository;
    private final LoteRepository loteRepository;
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

        Producto producto = toEntity(request);
        Producto guardado = productoRepository.save(producto);

        Stock stock = Stock.builder()
            .idProducto(guardado.getId())
            .cantidad(0)
            .build();
        stockRepository.save(stock);

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

        Stock stock = stockRepository.findByIdProductoAndDeletedAtIsNull(id).orElse(null);
        List<Lote> lotes = loteRepository.findByIdProductoAndDeletedAtIsNull(id);

        validarSinExistencias(stock, lotes);

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
     * Un producto con existencias no se da de baja en silencio: el soft delete se llevaría
     * puestas unidades que siguen en la góndola y que después ningún reporte vuelve a
     * mostrar. Primero hay que descargarlas, con una venta o con un ajuste de stock.
     */
    private void validarSinExistencias(Stock stock, List<Lote> lotes) {
        if (stock != null && stock.getCantidad() > 0) {
            throw new BadRequestException("No se puede borrar un producto con existencias: quedan "
                + stock.getCantidad() + " unidades en stock. Ajustá el stock a 0 antes de darlo de baja");
        }

        int unidadesEnLotes = lotes.stream().mapToInt(Lote::getCantidad).sum();
        if (unidadesEnLotes > 0) {
            long lotesConUnidades = lotes.stream().filter(lote -> lote.getCantidad() > 0).count();
            throw new BadRequestException("No se puede borrar un producto con existencias: quedan "
                + unidadesEnLotes + " unidades en " + lotesConUnidades
                + (lotesConUnidades == 1 ? " lote activo" : " lotes activos")
                + ". Descargá los lotes antes de darlo de baja");
        }
    }

    private void validarCategoriaYProveedor(UUID idCategoria, UUID idProveedor) {
        if (idCategoria != null && !categoriasApi.existsById(idCategoria)) {
            throw new BadRequestException("La categoría especificada no existe");
        }
        if (idProveedor != null && !proveedoresApi.existsById(idProveedor)) {
            throw new BadRequestException("El proveedor especificado no existe");
        }
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
