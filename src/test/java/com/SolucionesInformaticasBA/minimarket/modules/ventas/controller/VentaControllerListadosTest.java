package com.SolucionesInformaticasBA.minimarket.modules.ventas.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
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

import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.VentasApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.GlobalExceptionHandler;

/**
 * Los tres listados de ventas pasaron a paginar: es la tabla que más rápido crece del sistema y
 * antes se devolvía entera en cada request.
 */
@ExtendWith(MockitoExtension.class)
class VentaControllerListadosTest {

    @Mock
    private VentasApi ventasApi;

    private MockMvc mockMvc;

    private static final UUID ID_USUARIO = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new VentaController(ventasApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("el listado ordena de la más reciente a la más vieja, con el id de desempate")
    void ordenaPorFechaConDesempate() throws Exception {
        when(ventasApi.getAll(any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/ventas/v1")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ventasApi).getAll(captor.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("el tamaño de página tiene techo y no llega al servicio si se pasa")
    void sizeFueraDeRangoEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/ventas/v1").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ventas/v1").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/ventas/v1").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verify(ventasApi, never()).getAll(any());
    }

    @Test
    @DisplayName("el listado por usuario también pagina")
    void listadoPorUsuarioPagina() throws Exception {
        when(ventasApi.getByUsuario(any(), any())).thenReturn(Page.empty(PageRequest.of(1, 50)));

        mockMvc.perform(get("/api/ventas/v1/usuario/" + ID_USUARIO)
                        .param("page", "1").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(ventasApi).getByUsuario(any(), captor.capture());
        assertEquals(1, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }

    @Test
    @DisplayName("el listado por fechas también pagina y valida el rango de página")
    void listadoPorFechaPagina() throws Exception {
        when(ventasApi.getByFecha(any(), any(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/ventas/v1/fecha")
                        .param("desde", "2026-09-01T00:00:00")
                        .param("hasta", "2026-09-02T00:00:00"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/ventas/v1/fecha")
                        .param("desde", "2026-09-01T00:00:00")
                        .param("hasta", "2026-09-02T00:00:00")
                        .param("size", "0"))
                .andExpect(status().isBadRequest());
    }
}
