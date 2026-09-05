package com.SolucionesInformaticasBA.minimarket.modules.inventario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.MovimientoStockRequest;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.dto.StockResponse;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.GlobalExceptionHandler;

/**
 * Un movimiento cargado a mano no puede hacerse pasar por el de una venta o una compra: esos
 * los escribe el sistema con el id del comprobante, y son los que lee la reversa de anulación.
 */
@ExtendWith(MockitoExtension.class)
class InventarioControllerMovimientosTest {

    @Mock
    private InventarioApi inventarioApi;

    private MockMvc mockMvc;

    private static final UUID ID_PRODUCTO = UUID.randomUUID();
    private static final UUID ID_USUARIO = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new InventarioController(inventarioApi))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ID_USUARIO.toString(), null));
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("un movimiento manual con tipo VENTA se rechaza con 400")
    void tipoVentaEnMovimientoManualEsBadRequest() throws Exception {
        mockMvc.perform(put("/api/inventario/v1/stock/aumentar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("VENTA", UUID.randomUUID())))
                .andExpect(status().isBadRequest());

        verify(inventarioApi, never()).aumentar(any());
    }

    @Test
    @DisplayName("la referencia que venga en el body se descarta")
    void laReferenciaDelClienteSeIgnora() throws Exception {
        when(inventarioApi.aumentar(any())).thenReturn(StockResponse.builder()
                .idProducto(ID_PRODUCTO).cantidad(10).build());

        mockMvc.perform(put("/api/inventario/v1/stock/aumentar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("AJUSTE", UUID.randomUUID())))
                .andExpect(status().isOk());

        ArgumentCaptor<MovimientoStockRequest> captor =
                ArgumentCaptor.forClass(MovimientoStockRequest.class);
        verify(inventarioApi).aumentar(captor.capture());

        assertNull(captor.getValue().getIdReferencia());
        assertEquals(ID_USUARIO, captor.getValue().getIdUsuario());
    }

    @Test
    @DisplayName("el historial de movimientos pagina y ordena del más reciente al más viejo")
    void movimientosPaginanYOrdenan() throws Exception {
        when(inventarioApi.obtenerMovimientos(any(), any()))
                .thenReturn(Page.empty(PageRequest.of(1, 50)));

        mockMvc.perform(get("/api/inventario/v1/movimientos/" + ID_PRODUCTO)
                        .param("page", "1").param("size", "50"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(inventarioApi).obtenerMovimientos(any(), captor.capture());

        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")),
                captor.getValue().getSort());
    }

    @Test
    @DisplayName("un size fuera de rango en el historial es 400 y no 500")
    void sizeInvalidoEnMovimientosEsBadRequest() throws Exception {
        mockMvc.perform(get("/api/inventario/v1/movimientos/" + ID_PRODUCTO).param("size", "0"))
                .andExpect(status().isBadRequest());

        verify(inventarioApi, never()).obtenerMovimientos(any(), any());
    }

    private String cuerpo(String tipo, UUID idReferencia) {
        return """
                {
                  "idProducto": "%s",
                  "cantidad": 3,
                  "tipo": "%s",
                  "motivo": "carga manual",
                  "idReferencia": "%s"
                }
                """.formatted(ID_PRODUCTO, tipo, idReferencia);
    }
}
