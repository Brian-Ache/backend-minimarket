package com.SolucionesInformaticasBA.minimarket.modules.ventas.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.SolucionesInformaticasBA.minimarket.modules.caja.api.CajaApi;
import com.SolucionesInformaticasBA.minimarket.modules.caja.api.dto.SesionCajaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.api.InventarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.LoteRepository;
import com.SolucionesInformaticasBA.minimarket.modules.inventario.repository.MovimientoStockRepository;
import com.SolucionesInformaticasBA.minimarket.modules.productos.api.ProductosApi;
import com.SolucionesInformaticasBA.minimarket.modules.usuarios.api.UsuarioApi;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.ResumenDiarioResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.api.dto.VentaResponse;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.DetalleVenta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.entity.Venta;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.DetalleVentaRepository;
import com.SolucionesInformaticasBA.minimarket.modules.ventas.repository.VentaRepository;
import com.SolucionesInformaticasBA.minimarket.shared.exeption.ForbiddenException;

@ExtendWith(MockitoExtension.class)
class VentaServiceListadosTest {

    @Mock private VentaRepository ventaRepository;
    @Mock private DetalleVentaRepository detalleVentaRepository;
    @Mock private UsuarioApi usuarioApi;
    @Mock private ProductosApi productosApi;
    @Mock private InventarioApi inventarioApi;
    @Mock private LoteRepository loteRepository;
    @Mock private MovimientoStockRepository movimientoStockRepository;
    @Mock private CajaApi cajaApi;

    @InjectMocks private VentaService ventaService;

    private static final UUID ID_VENTA = UUID.randomUUID();
    private static final UUID ID_SESION = UUID.randomUUID();
    private static final UUID ID_DUENIO = UUID.randomUUID();

    @Test
    @DisplayName("el listado consulta paginado y no trae la tabla entera para filtrar en memoria")
    void getAllNoTraeLaTablaEntera() {
        Pageable pageable = PageRequest.of(0, 20);
        when(ventaRepository.findFiltradas(null, pageable))
                .thenReturn(new PageImpl<>(List.of(venta()), pageable, 137));
        when(detalleVentaRepository.findByIdVentaInAndDeletedAtIsNull(List.of(ID_VENTA)))
                .thenReturn(List.of(detalle()));

        Page<VentaResponse> pagina = ventaService.getAll(null, pageable);

        assertEquals(1, pagina.getContent().size());
        // El total viene de la consulta, no de contar lo que se trajo a memoria.
        assertEquals(137, pagina.getTotalElements());
        verify(ventaRepository, never()).findAll();
    }

    @Test
    @DisplayName("los detalles de la página se traen en una sola consulta")
    void losDetallesSeTraenEnUnaConsulta() {
        Pageable pageable = PageRequest.of(0, 20);
        Venta otra = venta();
        otra.setId(UUID.randomUUID());
        when(ventaRepository.findFiltradas(null, pageable))
                .thenReturn(new PageImpl<>(List.of(venta(), otra), pageable, 2));
        when(detalleVentaRepository.findByIdVentaInAndDeletedAtIsNull(any())).thenReturn(List.of(detalle()));

        ventaService.getAll(null, pageable);

        verify(detalleVentaRepository).findByIdVentaInAndDeletedAtIsNull(any());
        verify(detalleVentaRepository, never()).findByIdVentaAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("el resumen del turno se fecha con la apertura del turno, no con hoy")
    void elResumenDelTurnoUsaLaFechaDeApertura() {
        // Un turno que abrió anteayer y se consulta hoy salía fechado hoy.
        LocalDateTime apertura = LocalDateTime.now().minusDays(2).withHour(22);
        when(cajaApi.getSesionById(ID_SESION)).thenReturn(SesionCajaResponse.builder()
                .id(ID_SESION).fechaApertura(apertura).build());
        when(ventaRepository.findByIdSesionAndCobradaTrueAndDeletedAtIsNull(ID_SESION))
                .thenReturn(List.of(ventaCobrada("EFECTIVO", 1500f)));

        ResumenDiarioResponse resumen = ventaService.getResumenPorSesion(ID_SESION);

        assertEquals(apertura.toLocalDate(), resumen.getFecha());
        assertEquals(1500f, resumen.getTotalEfectivo());
        assertEquals(1, resumen.getCantidadVentas());
    }

    @Test
    @DisplayName("el desglose por medio de pago del turno separa efectivo, tarjeta y transferencia")
    void elResumenDelTurnoSeparaPorMedioDePago() {
        when(cajaApi.getSesionById(ID_SESION)).thenReturn(SesionCajaResponse.builder()
                .id(ID_SESION).fechaApertura(LocalDateTime.now()).build());
        when(ventaRepository.findByIdSesionAndCobradaTrueAndDeletedAtIsNull(ID_SESION))
                .thenReturn(List.of(ventaCobrada("EFECTIVO", 1000f),
                        ventaCobrada("TARJETA", 2000f),
                        ventaCobrada("TRANSFERENCIA", 500f)));

        ResumenDiarioResponse resumen = ventaService.getResumenPorSesion(ID_SESION);

        assertEquals(1000f, resumen.getTotalEfectivo());
        assertEquals(2000f, resumen.getTotalTarjeta());
        assertEquals(500f, resumen.getTotalTransferencia());
        assertEquals(3500f, resumen.getTotalVentas());
    }

    @Test
    @DisplayName("el alcance viaja a la consulta: un empleado solo pide lo suyo")
    void elAlcancePorUsuarioLlegaALaConsulta() {
        Pageable pageable = PageRequest.of(0, 20);
        UUID idEmpleado = UUID.randomUUID();
        when(ventaRepository.findFiltradas(idEmpleado, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        ventaService.getAll(idEmpleado, pageable);

        verify(ventaRepository).findFiltradas(idEmpleado, pageable);
    }

    @Test
    @DisplayName("un empleado no puede leer la venta de otro por id")
    void getByIdDeOtroUsuarioEsForbidden() {
        // Sin esto alcanzaba con tener el id de una venta ajena para leerla entera.
        autenticar(UUID.randomUUID(), "ROLE_EMPLEADO");
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta()));

        assertThrows(ForbiddenException.class, () -> ventaService.getById(ID_VENTA));
    }

    @Test
    @DisplayName("un administrador sí puede leer la venta de otro")
    void getByIdComoAdministrador() {
        autenticar(UUID.randomUUID(), "ROLE_ADMIN");
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(venta()));
        when(detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(ID_VENTA)).thenReturn(List.of());

        assertEquals(ID_VENTA, ventaService.getById(ID_VENTA).getId());
    }

    @Test
    @DisplayName("el dueño de la venta la puede leer")
    void getByIdDeLaPropia() {
        Venta propia = venta();
        autenticar(propia.getIdUsuario(), "ROLE_EMPLEADO");
        when(ventaRepository.findByIdAndDeletedAtIsNull(ID_VENTA)).thenReturn(Optional.of(propia));
        when(detalleVentaRepository.findByIdVentaAndDeletedAtIsNull(ID_VENTA)).thenReturn(List.of());

        assertEquals(ID_VENTA, ventaService.getById(ID_VENTA).getId());
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID idUsuario, String rol) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(idUsuario.toString(), null,
                        List.of(new SimpleGrantedAuthority(rol))));
    }

    private Venta venta() {
        return Venta.builder()
                .id(ID_VENTA)
                .idUsuario(ID_DUENIO)
                .total(1500f)
                .cobrada(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private Venta ventaCobrada(String metodoPago, float total) {
        Venta v = venta();
        v.setId(UUID.randomUUID());
        v.setTotal(total);
        v.setCobrada(true);
        v.setMetodoPago(metodoPago);
        v.setFechaCobro(LocalDateTime.now());
        return v;
    }

    private DetalleVenta detalle() {
        return DetalleVenta.builder()
                .id(UUID.randomUUID())
                .idVenta(ID_VENTA)
                .nombreProducto("Leche")
                .cantidad(1)
                .precioUnitario(1500f)
                .build();
    }
}
