package com.SolucionesInformaticasBA.minimarket.modules.inventario.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.LoteResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.entity.Lote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.enums.EstadoLote;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.StockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.BadRequestException;

@ExtendWith(MockitoExtension.class)
class InventarioServiceLotesTest {

    @Mock private StockRepository stockRepository;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private ProductosApi productosApi;
    @Mock private UsuarioApi usuarioApi;

    @InjectMocks private InventarioService inventarioService;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();

    @Test
    @DisplayName("los vencidos se piden a la base por fecha, no filtrando todo en memoria")
    void vencidosSeConsultanPorFecha() {
        LocalDate hoy = LocalDate.now();
        when(loteRepository.findByFechaVencimientoBeforeAndDeletedAtIsNull(hoy))
                .thenReturn(List.of(lote(hoy.minusDays(1))));
        when(productosApi.getNombresPorId(anyCollection())).thenReturn(Map.of(ID_PRODUCTO, "Leche"));

        List<LoteResponse> lotes = inventarioService.getByEstado("VENCIDO");

        assertEquals(1, lotes.size());
        assertEquals("VENCIDO", lotes.get(0).getEstado());
        verify(loteRepository, never()).findAllByDeletedAtIsNull();
        verify(productosApi, never()).getAll(any());
    }

    @Test
    @DisplayName("los próximos se piden por el rango que define EstadoLote")
    void proximosUsanElRangoDeEstadoLote() {
        LocalDate hoy = LocalDate.now();
        LocalDate ultimoDiaProximo = hoy.plusDays(EstadoLote.DIAS_PROXIMO_A_VENCER - 1L);
        when(loteRepository.findByFechaVencimientoBetweenAndDeletedAtIsNull(hoy, ultimoDiaProximo))
                .thenReturn(List.of());
        when(productosApi.getNombresPorId(anyCollection())).thenReturn(Map.of());

        inventarioService.getByEstado("proximo");

        verify(loteRepository).findByFechaVencimientoBetweenAndDeletedAtIsNull(hoy, ultimoDiaProximo);
    }

    /**
     * Los límites de las consultas por estado y EstadoLote.calcularPara son dos definiciones de
     * lo mismo, y si se separan el listado empieza a mentir. Esto ancla los bordes.
     */
    @Test
    @DisplayName("los bordes de cada rango coinciden con lo que calcula EstadoLote")
    void losBordesCoincidenConElCalculo() {
        LocalDate hoy = LocalDate.now();
        LocalDate ultimoDiaProximo = hoy.plusDays(EstadoLote.DIAS_PROXIMO_A_VENCER - 1L);

        assertEquals(EstadoLote.VENCIDO, EstadoLote.calcularPara(hoy.minusDays(1)));
        assertEquals(EstadoLote.PROXIMO, EstadoLote.calcularPara(hoy));
        assertEquals(EstadoLote.PROXIMO, EstadoLote.calcularPara(ultimoDiaProximo));
        assertEquals(EstadoLote.VIGENTE, EstadoLote.calcularPara(ultimoDiaProximo.plusDays(1)));
        assertEquals(EstadoLote.SIN_FECHA, EstadoLote.calcularPara(null));
    }

    @Test
    @DisplayName("un estado que no existe es 400 y no un 500")
    void estadoInvalidoEsBadRequest() {
        assertThrows(BadRequestException.class, () -> inventarioService.getByEstado("CUALQUIERA"));
    }

    @Test
    @DisplayName("un producto sin nombre no tira abajo el listado de lotes")
    void productoSinNombreNoRompeElListado() {
        // La columna nombre admite NULL, y Collectors.toMap no admite valores nulos: con el
        // mapa armado así, un solo producto sin nombre devolvía 500 en los cinco endpoints.
        Map<UUID, String> conNulo = new HashMap<>();
        conNulo.put(ID_PRODUCTO, null);
        when(loteRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(lote(LocalDate.now().plusMonths(2))));
        when(productosApi.getNombresPorId(anyCollection())).thenReturn(conNulo);

        List<LoteResponse> lotes = inventarioService.getAll();

        assertEquals("Producto sin nombre", lotes.get(0).getNombreProducto());
    }

    @Test
    @DisplayName("un lote de un producto que ya no está se muestra como no encontrado")
    void productoInexistenteSeMarcaEnElListado() {
        when(loteRepository.findAllByDeletedAtIsNull()).thenReturn(List.of(lote(LocalDate.now().plusMonths(2))));
        when(productosApi.getNombresPorId(anyCollection())).thenReturn(Collections.emptyMap());

        List<LoteResponse> lotes = inventarioService.getAll();

        assertEquals("Producto no encontrado", lotes.get(0).getNombreProducto());
    }

    private Lote lote(LocalDate fechaVencimiento) {
        return Lote.builder()
                .id(UUID.randomUUID())
                .idProducto(ID_PRODUCTO)
                .numeroLote("L-1")
                .fechaVencimiento(fechaVencimiento)
                .cantidad(5)
                .build();
    }
}
