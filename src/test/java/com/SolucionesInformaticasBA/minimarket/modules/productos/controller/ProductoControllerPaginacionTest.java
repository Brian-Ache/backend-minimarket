package com.SolucionesInformaticasBA.minimarket.modules.productos.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.GlobalExceptionHandler;

/**
 * Paginación del catálogo: los límites de page/size y el orden con el que se consulta.
 * Antes un size fuera de rango reventaba dentro de PageRequest.of y salía como 500.
 */
@ExtendWith(MockitoExtension.class)
class ProductoControllerPaginacionTest {

    @Mock
    private ProductosApi productosApi;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductoController(productosApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("size=0 responde 400 y no llega al servicio")
    void sizeCeroEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/productos/v1").param("size", "0"))
                .andExpect(status().isBadRequest());

        verify(productosApi, never()).getAll(any());
    }

    @Test
    @DisplayName("page negativo responde 400 y no llega al servicio")
    void pageNegativoEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/productos/v1").param("page", "-1"))
                .andExpect(status().isBadRequest());

        verify(productosApi, never()).getAll(any());
    }

    @Test
    @DisplayName("size por encima del tope responde 400")
    void sizeExcesivoEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/productos/v1").param("size", "101"))
                .andExpect(status().isBadRequest());

        verify(productosApi, never()).getAll(any());
    }

    @Test
    @DisplayName("el buscador también valida el rango")
    void searchValidaElRango() throws Exception {
        mockMvc.perform(get("/api/productos/v1/search").param("q", "coca").param("size", "0"))
                .andExpect(status().isBadRequest());

        verify(productosApi, never()).search(anyString(), any());
    }

    @Test
    @DisplayName("un size válido pasa y ordena por updatedAt con el id como desempate")
    void ordenaConDesempatePorId() throws Exception {
        // Page.empty() sin pageable devuelve una página Unpaged, que no se puede serializar.
        when(productosApi.getAll(any())).thenReturn(Page.empty(PageRequest.of(2, 50)));

        mockMvc.perform(get("/api/productos/v1").param("page", "2").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(productosApi).getAll(captor.capture());
        Pageable pageable = captor.getValue();

        assertEquals(2, pageable.getPageNumber());
        assertEquals(50, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "updatedAt").and(Sort.by(Sort.Direction.ASC, "id")),
                pageable.getSort());
    }
}
