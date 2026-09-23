package com.SolucionesInformaticasBA.minimarket.shared.exeption;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.productos.controller.ProductoController;

/**
 * El bloqueo de filas del inventario hace que la base pueda rechazar una operación por espera
 * de lock agotada o por deadlock. Eso es un cruce entre dos pedidos válidos, no una falla del
 * servidor: sin handler propio caía en el catch-all y salía 500.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerConcurrenciaTest {

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
    @DisplayName("la espera de lock agotada responde 409 y no 500")
    void lockNoAdquiridoEs409() throws Exception {
        when(productosApi.getById(any()))
                .thenThrow(new CannotAcquireLockException("Lock wait timeout exceeded"));

        mockMvc.perform(get("/api/productos/v1/" + UUID.randomUUID()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(
                        "La operación se cruzó con otra sobre el mismo producto. Volvé a intentarla"));
    }

    @Test
    @DisplayName("el deadlock resuelto por la base también responde 409")
    void deadlockEs409() throws Exception {
        when(productosApi.getById(any()))
                .thenThrow(new DeadlockLoserDataAccessException("Deadlock found", null));

        mockMvc.perform(get("/api/productos/v1/" + UUID.randomUUID()))
                .andExpect(status().isConflict());
    }
}
