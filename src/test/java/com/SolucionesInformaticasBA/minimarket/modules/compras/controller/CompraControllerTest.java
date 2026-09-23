package com.SolucionesInformaticasBA.minimarket.modules.compras.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.SolucionesInformaticasBA.minimarket.modules.compras.api.CompraApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.GlobalExceptionHandler;

@ExtendWith(MockitoExtension.class)
class CompraControllerTest {

    @Mock
    private CompraApi compraApi;

    private MockMvc mockMvc;

    private static final UUID ID_COMPRA = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CompraController(compraApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("anular no pide ni acepta un idUsuario del cliente")
    void anularNoRecibeIdUsuarioDelCliente() throws Exception {
        // Antes exigía un header idUsuario: sin él respondía 500, y con él se podía firmar la
        // reversa con la identidad de otro. Ahora el servicio lo resuelve del JWT.
        mockMvc.perform(delete("/api/compras/v1/" + ID_COMPRA))
                .andExpect(status().isNoContent());

        verify(compraApi).delete(ID_COMPRA);
    }

    @Test
    @DisplayName("un idUsuario en el header ya no cambia nada")
    void elHeaderViejoSeIgnora() throws Exception {
        mockMvc.perform(delete("/api/compras/v1/" + ID_COMPRA)
                        .header("idUsuario", UUID.randomUUID().toString()))
                .andExpect(status().isNoContent());

        verify(compraApi).delete(ID_COMPRA);
    }

    @Test
    @DisplayName("size fuera de rango responde 400 y no llega al servicio")
    void sizeInvalidoEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/compras/v1").param("size", "0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/compras/v1").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/compras/v1").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verify(compraApi, never()).getAllFiltered(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("el listado ordena por fecha con el id como desempate")
    void ordenPorFechaConDesempate() throws Exception {
        when(compraApi.getAllFiltered(any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/compras/v1")).andExpect(status().isOk());

        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")),
                pageableCapturado().getSort());
    }

    @Test
    @DisplayName("ordenar por total también desempata por id")
    void ordenPorTotalConDesempate() throws Exception {
        when(compraApi.getAllFiltered(any(), any(), any(), any(), any()))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        mockMvc.perform(get("/api/compras/v1").param("sortTotal", "asc"))
                .andExpect(status().isOk());

        assertEquals(Sort.by(Sort.Direction.ASC, "total").and(Sort.by(Sort.Direction.ASC, "id")),
                pageableCapturado().getSort());
    }

    private Pageable pageableCapturado() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(compraApi).getAllFiltered(eq(null), eq(null), eq(null), eq(null), captor.capture());
        return captor.getValue();
    }
}
