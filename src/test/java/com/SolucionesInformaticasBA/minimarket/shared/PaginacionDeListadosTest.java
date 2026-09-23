package com.SolucionesInformaticasBA.minimarket.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.controller.CorteController;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.api.CategoriasApi;
import com.SolucionesInformaticasBA.minimarket.modules.categorias.controller.CategoriaController;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.controller.InventarioController;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.api.ProveedoresApi;
import com.SolucionesInformaticasBA.minimarket.modules.proveedores.controller.ProveedorController;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.ReportesApi;
import com.SolucionesInformaticasBA.minimarket.modules.reportes.controller.ReporteController;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.GlobalExceptionHandler;

/**
 * Los seis listados que pasaron a paginar en esta versión: que respeten el mismo rango de
 * {@code page} y {@code size} que el resto de la API, y que el orden lleve siempre el desempate
 * por id.
 *
 * <p>El desempate no es decorativo: sin él, el orden dentro de un empate lo elige la base y las
 * filas se repiten o se saltean al pasar de página. Y ninguno de estos listados ordena por una
 * columna única —vencimiento, nombre, fecha de cierre— así que los empates son la norma.
 */
@ExtendWith(MockitoExtension.class)
class PaginacionDeListadosTest {

    @Mock private InventarioApi inventarioApi;
    @Mock private CajaApi cajaApi;
    @Mock private ReportesApi reportesApi;
    @Mock private CategoriasApi categoriasApi;
    @Mock private ProveedoresApi proveedoresApi;

    private MockMvc mvc(Object controller) {
        return MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("los lotes ordenan por vencimiento ascendente, con el id de desempate")
    void lotesOrdenanPorVencimiento() throws Exception {
        when(inventarioApi.getAll(any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc(new InventarioController(inventarioApi))
                .perform(get("/api/inventario/v1/lotes"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventarioApi).getAll(captor.capture());

        assertEquals(
                Sort.by(Sort.Direction.ASC, "fechaVencimiento").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("los atajos por vencimiento pasan el mismo orden que el listado por estado")
    void losAtajosUsanElMismoOrden() throws Exception {
        when(inventarioApi.getByEstado(anyString(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc(new InventarioController(inventarioApi))
                .perform(get("/api/inventario/v1/lotes/vencimiento/vencidos"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventarioApi).getByEstado(eq("VENCIDO"), captor.capture());

        assertEquals(
                Sort.by(Sort.Direction.ASC, "fechaVencimiento").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("el historial de cortes va del más reciente al más viejo, con id de desempate")
    void cortesOrdenanPorFechaDeCierre() throws Exception {
        when(cajaApi.getHistorialCortes(any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc(new CorteController(cajaApi))
                .perform(get("/api/caja/v1/corte/historial"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(cajaApi).getHistorialCortes(captor.capture());

        assertEquals(
                Sort.by(Sort.Direction.DESC, "fechaCierre").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("el reporte de inventario ordena igual que el catálogo, que es su misma consulta")
    void inventarioOrdenaComoElCatalogo() throws Exception {
        when(reportesApi.getReporteInventario(any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc(new ReporteController(reportesApi))
                .perform(get("/api/reportes/v1/inventario"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportesApi).getReporteInventario(captor.capture());

        assertEquals(
                Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("las categorías ordenan por nombre, con el id de desempate")
    void categoriasOrdenanPorNombre() throws Exception {
        when(categoriasApi.getAll(any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mvc(new CategoriaController(categoriasApi))
                .perform(get("/api/categorias/v1"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(categoriasApi).getAll(captor.capture());

        assertEquals(Sort.by(Sort.Direction.ASC, "nombre").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("los proveedores conservan incluirBajas además de paginar")
    void proveedoresConservanIncluirBajas() throws Exception {
        when(proveedoresApi.getAll(anyBoolean(), any())).thenReturn(Page.empty(PageRequest.of(1, 5)));

        mvc(new ProveedorController(proveedoresApi))
                .perform(get("/api/proveedores/v1")
                        .param("incluirBajas", "true")
                        .param("page", "1")
                        .param("size", "5"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(proveedoresApi).getAll(eq(true), captor.capture());

        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(5, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("todos rechazan un size por encima del tope, sin llegar al servicio")
    void elTopeEsElMismoEnTodos() throws Exception {
        String excesivo = String.valueOf(Paginacion.MAX_PAGE_SIZE + 1);

        mvc(new InventarioController(inventarioApi))
                .perform(get("/api/inventario/v1/lotes").param("size", excesivo))
                .andExpect(status().isBadRequest());
        mvc(new CorteController(cajaApi))
                .perform(get("/api/caja/v1/corte/historial").param("size", excesivo))
                .andExpect(status().isBadRequest());
        mvc(new ReporteController(reportesApi))
                .perform(get("/api/reportes/v1/inventario").param("size", excesivo))
                .andExpect(status().isBadRequest());
        mvc(new CategoriaController(categoriasApi))
                .perform(get("/api/categorias/v1").param("size", excesivo))
                .andExpect(status().isBadRequest());
        mvc(new ProveedorController(proveedoresApi))
                .perform(get("/api/proveedores/v1").param("size", excesivo))
                .andExpect(status().isBadRequest());

        verify(inventarioApi, never()).getAll(any());
        verify(cajaApi, never()).getHistorialCortes(any());
        verify(reportesApi, never()).getReporteInventario(any());
        verify(categoriasApi, never()).getAll(any());
        verify(proveedoresApi, never()).getAll(anyBoolean(), any());
    }

    @Test
    @DisplayName("todos rechazan un page negativo, sin llegar al servicio")
    void elPageNegativoSeRechazaEnTodos() throws Exception {
        mvc(new InventarioController(inventarioApi))
                .perform(get("/api/inventario/v1/lotes").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc(new CorteController(cajaApi))
                .perform(get("/api/caja/v1/corte/historial").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mvc(new CategoriaController(categoriasApi))
                .perform(get("/api/categorias/v1").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verify(inventarioApi, never()).getAll(any());
        verify(cajaApi, never()).getHistorialCortes(any());
        verify(categoriasApi, never()).getAll(any());
    }
}
