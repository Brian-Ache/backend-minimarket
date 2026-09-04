package com.SolucionesInformaticasBA.minimarket.modules.compras.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

}
