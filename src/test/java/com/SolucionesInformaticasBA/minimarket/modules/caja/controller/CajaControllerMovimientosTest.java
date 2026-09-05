package com.SolucionesInformaticasBA.minimarket.modules.caja.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class CajaControllerMovimientosTest {

    @Mock
    private CajaApi cajaApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CajaController(cajaApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("el listado ordena del movimiento más reciente al más viejo, con desempate")
    void ordenaPorFechaConDesempate() throws Exception {
        when(cajaApi.getMovimientos(any(), any(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/caja/v1/movimientos")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(cajaApi).getMovimientos(any(), any(), captor.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("un size fuera de rango es 400 y no llega al servicio")
    void sizeFueraDeRangoEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/caja/v1/movimientos").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/caja/v1/movimientos").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/caja/v1/movimientos").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verify(cajaApi, never()).getMovimientos(any(), any(), any());
    }

    @Test
    @DisplayName("las fechas del rango llegan al servicio tal como vinieron")
    void lasFechasLleganAlServicio() throws Exception {
        when(cajaApi.getMovimientos(any(), any(), any())).thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/caja/v1/movimientos")
                        .param("desde", "2026-09-01T00:00:00")
                        .param("hasta", "2026-09-02T00:00:00"))
                .andExpect(status().isOk());

        verify(cajaApi).getMovimientos(
                org.mockito.ArgumentMatchers.eq(java.time.LocalDateTime.parse("2026-09-01T00:00:00")),
                org.mockito.ArgumentMatchers.eq(java.time.LocalDateTime.parse("2026-09-02T00:00:00")),
                any());
    }
}
