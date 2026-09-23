package com.SolucionesInformaticasBA.minimarket.modules.reportes.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.SolucionesInformaticasBA.minimarket.modules.reportes.api.ReportesApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.GlobalExceptionHandler;

/**
 * El {@code limite} del top de productos no tenía validación: un valor negativo llegaba a
 * {@code Stream.limit} y salía como 500, haciendo pasar un error del cliente por una falla del
 * servidor.
 */
@ExtendWith(MockitoExtension.class)
class ReporteControllerTest {

    @Mock
    private ReportesApi reportesApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ReporteController(reportesApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("un limite negativo es 400 y ni siquiera llega al servicio")
    void limiteNegativoEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reportes/v1/productos-mas-vendidos")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30")
                        .param("limite", "-1"))
                .andExpect(status().isBadRequest());

        verify(reportesApi, never()).getProductosMasVendidos(any(), any(), anyInt());
    }

    @Test
    @DisplayName("el limite tiene piso en 1 y techo en 100")
    void limiteFueraDeRangoEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reportes/v1/productos-mas-vendidos")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30")
                        .param("limite", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reportes/v1/productos-mas-vendidos")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30")
                        .param("limite", "101"))
                .andExpect(status().isBadRequest());

        verify(reportesApi, never()).getProductosMasVendidos(any(), any(), anyInt());
    }

    @Test
    @DisplayName("sin limite explícito se piden 10")
    void limitePorDefecto() throws Exception {
        when(reportesApi.getProductosMasVendidos(any(), any(), anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/reportes/v1/productos-mas-vendidos")
                        .param("desde", "2026-09-01").param("hasta", "2026-09-30"))
                .andExpect(status().isOk());

        ArgumentCaptor<Integer> limite = ArgumentCaptor.forClass(Integer.class);
        verify(reportesApi).getProductosMasVendidos(any(), any(), limite.capture());
        assertEquals(10, limite.getValue());
    }

    @Test
    @DisplayName("una fecha mal formada es 400 y no 500")
    void fechaInvalidaEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reportes/v1/ventas")
                        .param("desde", "ayer").param("hasta", "2026-09-30"))
                .andExpect(status().isBadRequest());

        verify(reportesApi, never()).getReporteVentas(any(), any());
    }

    @Test
    @DisplayName("falta una fecha obligatoria: 400 con el nombre del parámetro")
    void fechaFaltanteEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/reportes/v1/ganancias").param("desde", "2026-09-01"))
                .andExpect(status().isBadRequest());

        verify(reportesApi, never()).getReporteGanancias(any(), any());
    }
}
